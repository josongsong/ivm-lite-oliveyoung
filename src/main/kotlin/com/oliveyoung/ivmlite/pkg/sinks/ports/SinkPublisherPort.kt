package com.oliveyoung.ivmlite.pkg.sinks.ports

import arrow.core.Either
import com.oliveyoung.ivmlite.sinks.contract.SinkEnvelopeV1
import com.oliveyoung.ivmlite.sinks.contract.SinkError

/**
 * SinkPublisherPort - Sink 메시지 발행 인터페이스
 *
 * RFC-017: Sink Plugin Architecture
 *
 * Hexagonal Architecture: Application → Port (이 인터페이스) ← Adapter (SqsSinkPublisher)
 */
interface SinkPublisherPort {

    /**
     * Envelope를 메시지 큐로 발행
     *
     * @param queueUrl 대상 큐 URL
     * @param envelope Sink Envelope
     * @return Either<SinkError, Unit>
     */
    fun publish(queueUrl: String, envelope: SinkEnvelopeV1): Either<SinkError, Unit>
}
