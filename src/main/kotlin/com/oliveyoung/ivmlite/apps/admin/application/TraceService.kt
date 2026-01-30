package com.oliveyoung.ivmlite.apps.admin.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import software.amazon.awssdk.services.xray.XRayClient
import software.amazon.awssdk.services.xray.model.*
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Trace Service (AWS X-Ray 연동)
 *
 * X-Ray에서 트레이스 데이터를 조회하는 서비스.
 * - 트레이스 목록 조회 (필터링, 페이징)
 * - 트레이스 상세 조회 (spans 포함)
 * - 서비스 맵 조회
 */
class TraceService(
    private val xrayClient: XRayClient
) {
    /**
     * 트레이스 목록 조회
     *
     * @param startTime 시작 시간 (기본: 15분 전)
     * @param endTime 종료 시간 (기본: 현재)
     * @param serviceName 서비스 이름 필터 (선택)
     * @param limit 최대 결과 수 (기본: 100)
     * @param nextToken 페이징 토큰 (선택)
     */
    suspend fun getTraces(
        startTime: Instant? = null,
        endTime: Instant? = null,
        serviceName: String? = null,
        limit: Int = 100,
        nextToken: String? = null
    ): Either<DomainError, TraceListResult> = either {
        try {
            withContext(Dispatchers.IO) {
                val end = endTime ?: Instant.now()
                val start = startTime ?: end.minus(15, ChronoUnit.MINUTES)

                val requestBuilder = GetTraceSummariesRequest.builder()
                    .startTime(start)
                    .endTime(end)
                    .timeRangeType(TimeRangeType.TRACE_ID)  // TRACE_ID 기본값 사용

                // 서비스 필터
                serviceName?.let {
                    requestBuilder.filterExpression("service(\"$it\")")
                }

                // 페이징
                nextToken?.let {
                    requestBuilder.nextToken(it)
                }

                // X-Ray SDK는 동기식이므로 runBlocking 사용
                val response = runBlocking {
                    xrayClient.getTraceSummaries(requestBuilder.build())
                }

                val traceSummaries = response.traceSummaries()
                
                TraceListResult(
                    traces = traceSummaries.map { summary ->
                        TraceSummary(
                            traceId = summary.id(),
                            duration = (summary.duration()?.toDouble() ?: 0.0) * 1000, // seconds to ms
                            startTime = summary.startTime(),
                            hasError = summary.hasError() ?: false,
                            hasFault = summary.hasFault() ?: false,
                            hasThrottle = summary.hasThrottle() ?: false,
                            http = summary.http()?.let { http ->
                                HttpInfo(
                                    method = http.httpMethod(),
                                    url = http.httpURL(),
                                    status = http.httpStatus()
                                )
                            },
                            annotations = summary.annotations()?.orEmpty()?.mapValues { 
                                it.value.firstOrNull()?.toString() ?: "" 
                            } ?: emptyMap(),
                            serviceIds = summary.serviceIds()?.orEmpty()?.map { it.name() } ?: emptyList()
                        )
                    },
                    nextToken = response.nextToken(),
                    approximateCount = traceSummaries.size.toLong()  // 실제 트레이스 개수
                )
            }
        } catch (e: Exception) {
            raise(DomainError.StorageError("Failed to get traces: ${e.message}"))
        }
    }

    /**
     * 트레이스 상세 조회 (spans 포함)
     *
     * @param traceIds 트레이스 ID 목록 (최대 5개)
     */
    suspend fun getTraceDetails(
        traceIds: List<String>
    ): Either<DomainError, List<TraceDetail>> = either {
        try {
            withContext(Dispatchers.IO) {
                if (traceIds.isEmpty() || traceIds.size > 5) {
                    raise(DomainError.ValidationError("traceIds", "Trace IDs must be between 1 and 5"))
                } else {
                    val request = BatchGetTracesRequest.builder()
                        .traceIds(traceIds)
                        .build()

                    val response = runBlocking {
                        xrayClient.batchGetTraces(request)
                    }

                    response.traces().map { trace ->
                        val allSpans = mutableListOf<SpanDetail>()
                        var traceStartTime: Double? = null
                        var traceEndTime: Double? = null
                        var totalDuration = 0.0

                        // 모든 세그먼트를 파싱하여 스팬으로 변환
                        trace.segments().forEach { segment ->
                            val parsedSpans = parseSegmentDocument(segment.document(), null)
                            allSpans.addAll(parsedSpans)
                        }

                        // 시작/종료 시간 및 duration 계산
                        if (allSpans.isNotEmpty()) {
                            traceStartTime = allSpans.minOf { it.startTime }
                            traceEndTime = allSpans.maxOf { it.endTime }
                            totalDuration = ((traceEndTime ?: 0.0) - (traceStartTime ?: 0.0)) * 1000
                        }

                        TraceDetail(
                            traceId = trace.id(),
                            duration = trace.duration()?.toDouble()?.times(1000) ?: totalDuration,
                            startTime = Instant.ofEpochMilli(((traceStartTime ?: 0.0) * 1000).toLong()),
                            endTime = traceEndTime?.let { Instant.ofEpochMilli((it * 1000).toLong()) },
                            segments = allSpans.sortedBy { it.startTime }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            raise(DomainError.StorageError("Failed to get trace details: ${e.message}"))
        }
    }

    /**
     * X-Ray 세그먼트 JSON 문서 파싱
     * 
     * X-Ray 세그먼트 구조:
     * - id: 세그먼트 ID
     * - name: 서비스/세그먼트 이름
     * - start_time: Unix timestamp (seconds)
     * - end_time: Unix timestamp (seconds)
     * - http: HTTP 정보 (optional)
     * - annotations: 사용자 정의 어노테이션
     * - metadata: 메타데이터
     * - subsegments: 하위 세그먼트 (재귀)
     * - error: 에러 여부
     * - fault: 장애 여부
     * - cause: 에러 원인
     */
    private fun parseSegmentDocument(
        documentJson: String,
        parentId: String?
    ): List<SpanDetail> {
        val spans = mutableListOf<SpanDetail>()
        
        try {
            val json = Json { ignoreUnknownKeys = true }
            val doc = json.parseToJsonElement(documentJson).jsonObject
            
            val spanId = doc["id"]?.jsonPrimitive?.content ?: return emptyList()
            val name = doc["name"]?.jsonPrimitive?.content ?: "unknown"
            val startTime = doc["start_time"]?.jsonPrimitive?.double ?: 0.0
            val endTime = doc["end_time"]?.jsonPrimitive?.double ?: startTime
            val duration = (endTime - startTime) * 1000 // seconds to ms
            
            // HTTP 정보 추출
            val httpObj = doc["http"]?.jsonObject
            val httpInfo = httpObj?.let { http ->
                val request = http["request"]?.jsonObject
                val response = http["response"]?.jsonObject
                HttpInfo(
                    method = request?.get("method")?.jsonPrimitive?.content,
                    url = request?.get("url")?.jsonPrimitive?.content,
                    status = response?.get("status")?.jsonPrimitive?.intOrNull
                )
            }
            
            // 어노테이션 추출
            val annotationsObj = doc["annotations"]?.jsonObject
            val annotations = annotationsObj?.mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> value.content
                    else -> value.toString()
                }
            } ?: emptyMap()
            
            // 메타데이터 추출 (첫 번째 레벨만)
            val metadataObj = doc["metadata"]?.jsonObject
            val metadata = mutableMapOf<String, String>()
            metadataObj?.forEach { (key, value) ->
                when (value) {
                    is JsonObject -> value.forEach { (k, v) ->
                        metadata["$key.$k"] = when (v) {
                            is JsonPrimitive -> v.content
                            else -> v.toString()
                        }
                    }
                    is JsonPrimitive -> metadata[key] = value.content
                    else -> metadata[key] = value.toString()
                }
            }
            
            // 에러 정보
            val hasError = doc["error"]?.jsonPrimitive?.booleanOrNull == true
            val hasFault = doc["fault"]?.jsonPrimitive?.booleanOrNull == true
            val cause = doc["cause"]?.jsonObject
            val errorMessage = cause?.get("message")?.jsonPrimitive?.content
                ?: cause?.get("exceptions")?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
            
            // 세그먼트 타입 결정
            val origin = doc["origin"]?.jsonPrimitive?.content
            val namespace = doc["namespace"]?.jsonPrimitive?.content
            val type = when {
                origin != null -> origin
                namespace == "aws" -> "AWS"
                namespace == "remote" -> "Remote"
                httpInfo != null -> "HTTP"
                else -> "Segment"
            }
            
            // 현재 스팬 추가
            spans.add(SpanDetail(
                spanId = spanId,
                parentId = parentId,
                name = name,
                startTime = startTime,
                endTime = endTime,
                duration = duration,
                service = doc["origin"]?.jsonPrimitive?.content ?: name,
                type = type,
                http = httpInfo,
                annotations = annotations,
                metadata = metadata,
                hasError = hasError || hasFault,
                errorMessage = errorMessage
            ))
            
            // 하위 세그먼트 재귀 파싱
            val subsegments = doc["subsegments"]?.jsonArray
            subsegments?.forEach { subsegment ->
                val subsegmentSpans = parseSubsegment(subsegment.jsonObject, spanId)
                spans.addAll(subsegmentSpans)
            }
            
        } catch (e: Exception) {
            // JSON 파싱 실패 시 기본 스팬 반환
            spans.add(SpanDetail(
                spanId = "parse-error-${System.nanoTime()}",
                parentId = parentId,
                name = "Parse Error",
                startTime = Instant.now().epochSecond.toDouble(),
                endTime = Instant.now().epochSecond.toDouble(),
                duration = 0.0,
                service = "unknown",
                type = "Error",
                hasError = true,
                errorMessage = "Failed to parse segment: ${e.message}"
            ))
        }
        
        return spans
    }
    
    /**
     * 하위 세그먼트 파싱 (재귀)
     */
    private fun parseSubsegment(
        subsegmentObj: JsonObject,
        parentId: String
    ): List<SpanDetail> {
        val spans = mutableListOf<SpanDetail>()
        
        val spanId = subsegmentObj["id"]?.jsonPrimitive?.content ?: "sub-${System.nanoTime()}"
        val name = subsegmentObj["name"]?.jsonPrimitive?.content ?: "unknown"
        val startTime = subsegmentObj["start_time"]?.jsonPrimitive?.double ?: 0.0
        val endTime = subsegmentObj["end_time"]?.jsonPrimitive?.double ?: startTime
        val duration = (endTime - startTime) * 1000
        
        // HTTP 정보
        val httpObj = subsegmentObj["http"]?.jsonObject
        val httpInfo = httpObj?.let { http ->
            val request = http["request"]?.jsonObject
            val response = http["response"]?.jsonObject
            HttpInfo(
                method = request?.get("method")?.jsonPrimitive?.content,
                url = request?.get("url")?.jsonPrimitive?.content,
                status = response?.get("status")?.jsonPrimitive?.intOrNull
            )
        }
        
        // 어노테이션
        val annotationsObj = subsegmentObj["annotations"]?.jsonObject
        val annotations = annotationsObj?.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.content
                else -> value.toString()
            }
        } ?: emptyMap()
        
        // SQL 정보 (있으면 메타데이터에 추가)
        val sqlObj = subsegmentObj["sql"]?.jsonObject
        val metadata = mutableMapOf<String, String>()
        sqlObj?.let { sql ->
            sql["url"]?.jsonPrimitive?.content?.let { metadata["sql.url"] = it }
            sql["sanitized_query"]?.jsonPrimitive?.content?.let { metadata["sql.query"] = it }
            sql["database_type"]?.jsonPrimitive?.content?.let { metadata["sql.database_type"] = it }
        }
        
        // 에러 정보
        val hasError = subsegmentObj["error"]?.jsonPrimitive?.booleanOrNull == true
        val hasFault = subsegmentObj["fault"]?.jsonPrimitive?.booleanOrNull == true
        val cause = subsegmentObj["cause"]?.jsonObject
        val errorMessage = cause?.get("message")?.jsonPrimitive?.content
            ?: cause?.get("exceptions")?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
        
        // 네임스페이스 기반 타입 결정
        val namespace = subsegmentObj["namespace"]?.jsonPrimitive?.content
        val type = when (namespace) {
            "aws" -> "AWS"
            "remote" -> "Remote"
            else -> if (sqlObj != null) "Database" else if (httpInfo != null) "HTTP" else "Subsegment"
        }
        
        spans.add(SpanDetail(
            spanId = spanId,
            parentId = parentId,
            name = name,
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            service = namespace ?: name,
            type = type,
            http = httpInfo,
            annotations = annotations,
            metadata = metadata,
            hasError = hasError || hasFault,
            errorMessage = errorMessage
        ))
        
        // 하위 세그먼트 재귀 파싱
        subsegmentObj["subsegments"]?.jsonArray?.forEach { nestedSub ->
            spans.addAll(parseSubsegment(nestedSub.jsonObject, spanId))
        }
        
        return spans
    }

    /**
     * 서비스 맵 조회
     *
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @param serviceName 서비스 이름 (선택)
     */
    suspend fun getServiceMap(
        startTime: Instant? = null,
        endTime: Instant? = null,
        serviceName: String? = null
    ): Either<DomainError, ServiceMapResult> = either {
        try {
            withContext(Dispatchers.IO) {
                val end = endTime ?: Instant.now()
                val start = startTime ?: end.minus(1, ChronoUnit.HOURS)

                val requestBuilder = GetServiceGraphRequest.builder()
                    .startTime(start)
                    .endTime(end)

                // 서비스 필터
                serviceName?.let {
                    requestBuilder.groupName(it)
                }

                // X-Ray SDK는 동기식이므로 runBlocking 사용
                val response = runBlocking {
                    xrayClient.getServiceGraph(requestBuilder.build())
                }

                ServiceMapResult(
                    startTime = response.startTime(),
                    endTime = response.endTime(),
                    services = response.services().map { service ->
                        ServiceNode(
                            name = service.name(),
                            referenceId = service.referenceId(),
                            names = service.names()?.orEmpty() ?: emptyList(),
                            edges = service.edges().map { edge ->
                                ServiceEdge(
                                    referenceId = edge.referenceId(),
                                    startTime = edge.startTime(),
                                    endTime = edge.endTime(),
                                    summaryStatistics = edge.summaryStatistics()?.let { stats ->
                                        EdgeStatistics(
                                            okCount = stats.okCount()?.toLong() ?: 0,
                                            errorCount = stats.errorStatistics()?.totalCount()?.toLong() ?: 0,
                                            faultCount = stats.faultStatistics()?.totalCount()?.toLong() ?: 0,
                                            throttleCount = 0L, // throttleStatistics는 없을 수 있음
                                            totalCount = stats.totalCount()?.toLong() ?: 0,
                                            totalResponseTime = stats.totalResponseTime()?.toDouble() ?: 0.0
                                        )
                                    }
                                )
                            }
                        )
                    }
                )
            }
        } catch (e: Exception) {
            raise(DomainError.StorageError("Failed to get service map: ${e.message}"))
        }
    }
}

// ==================== Data Classes ====================

data class TraceListResult(
    val traces: List<TraceSummary>,
    val nextToken: String? = null,
    val approximateCount: Long = 0
)

data class TraceSummary(
    val traceId: String,
    val duration: Double, // ms
    val startTime: Instant,
    val hasError: Boolean,
    val hasFault: Boolean,
    val hasThrottle: Boolean,
    val http: HttpInfo? = null,
    val annotations: Map<String, String> = emptyMap(),
    val serviceIds: List<String> = emptyList()
)

data class TraceDetail(
    val traceId: String,
    val duration: Double, // ms
    val startTime: Instant,
    val endTime: Instant? = null,
    val segments: List<SpanDetail>
)

data class SpanDetail(
    val spanId: String,
    val parentId: String? = null,
    val name: String,
    val startTime: Double, // Unix timestamp (seconds)
    val endTime: Double,
    val duration: Double, // ms
    val service: String,
    val type: String,
    val http: HttpInfo? = null,
    val annotations: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap(),
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

data class HttpInfo(
    val method: String? = null,
    val url: String? = null,
    val status: Int? = null
)

data class ServiceMapResult(
    val startTime: Instant,
    val endTime: Instant,
    val services: List<ServiceNode>
)

data class ServiceNode(
    val name: String,
    val referenceId: Int,
    val names: List<String> = emptyList(),
    val edges: List<ServiceEdge> = emptyList()
)

data class ServiceEdge(
    val referenceId: Int,
    val startTime: Instant,
    val endTime: Instant,
    val summaryStatistics: EdgeStatistics? = null
)

data class EdgeStatistics(
    val okCount: Long,
    val errorCount: Long,
    val faultCount: Long,
    val throttleCount: Long,
    val totalCount: Long,
    val totalResponseTime: Double
)
