package com.oliveyoung.ivmlite.pkg.sinks.application

import arrow.core.Either
import arrow.core.raise.either
import com.oliveyoung.ivmlite.pkg.sinks.ports.SinkPublisherPort
import com.oliveyoung.ivmlite.sinks.contract.SinkEnvelopeV1
import com.oliveyoung.ivmlite.sinks.contract.SinkError
import com.oliveyoung.ivmlite.sinks.contract.SinkRoutingTable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("SinkDispatcher")

/**
 * Sink Dispatcher (SSOT)
 *
 * RFC-017: Sink Plugin Architecture
 *
 * View → 메시지 큐 발행을 담당 (엔진 책임)
 * - SinkRoutingTable로 target → queueUrl 라우팅
 * - SinkPublisherPort로 실제 발행 (Hexagonal: Port 의존)
 */
class SinkDispatcher(
    private val routingTable: SinkRoutingTable,
    private val publisher: SinkPublisherPort
) {
    /**
     * Sink로 Envelope 발행
     *
     * @param envelope Sink Envelope (target 포함)
     * @return Either<SinkError, Unit>
     */
    fun dispatch(envelope: SinkEnvelopeV1): Either<SinkError, Unit> = either {
        val queueUrl = routingTable.queueUrlOf(envelope.target)
            ?: raise(
                com.oliveyoung.ivmlite.sinks.contract.SinkError.NonRetryableError(
                    reasonCode = com.oliveyoung.ivmlite.sinks.contract.ErrorReasonCode.INVALID_CONFIGURATION,
                    message = "No queue URL for target=${envelope.target}"
                )
            )

        logger.debug("Dispatching to target=${envelope.target}, queueUrl=$queueUrl, version=${envelope.payloadVersion}")

        publisher.publish(queueUrl, envelope).bind()

        logger.info("Dispatched successfully: target=${envelope.target}, version=${envelope.payloadVersion}")
    }

    /**
     * 배치 발행 (최적화)
     */
    fun dispatchBatch(envelopes: List<SinkEnvelopeV1>): Either<SinkError, Unit> = either {
        envelopes.forEach { envelope ->
            dispatch(envelope).bind()
        }
    }
}
