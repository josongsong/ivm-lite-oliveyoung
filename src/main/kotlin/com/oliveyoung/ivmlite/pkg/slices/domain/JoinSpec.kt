package com.oliveyoung.ivmlite.pkg.slices.domain

data class JoinSpec(
  val name: String,
  val type: JoinType,
  val sourceFieldPath: String,
  val targetEntityType: String,
  val targetKeyPattern: String,
  val required: Boolean,
  val projection: Projection? = null,
  val targetSliceType: String? = null,  // ENRICHED 슬라이스에서 JOIN 대상 SliceType 지정
  val missingPolicy: MissingPolicy = MissingPolicy.FAIL_CLOSED,  // JOIN 실패 시 정책
)

enum class JoinType { LOOKUP }

/**
 * JOIN 실패 시 정책
 */
enum class MissingPolicy {
    /** JOIN 실패 시 전체 실패 */
    FAIL_CLOSED,
    /** JOIN 실패해도 부분 허용 */
    PARTIAL_ALLOWED
}

/**
 * Projection 모드
 */
enum class ProjectionMode {
    /** 지정된 필드만 복사 */
    COPY_FIELDS,
    /** 모든 필드 복사 후 지정된 필드 제외 */
    EXCLUDE_FIELDS
}

/**
 * 필드 매핑 정의
 *
 * @param fromTargetPath 소스 JSON Pointer (RFC 6901)
 * @param toOutputPath 출력 JSON Pointer
 */
data class FieldMapping(
    val fromTargetPath: String,
    val toOutputPath: String
)

/**
 * Projection 정의
 *
 * @param mode Projection 모드
 * @param fields 필드 매핑 목록
 */
data class Projection(
    val mode: ProjectionMode,
    val fields: List<FieldMapping>
)
