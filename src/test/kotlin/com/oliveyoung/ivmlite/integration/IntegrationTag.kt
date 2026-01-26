package com.oliveyoung.ivmlite.integration

import io.kotest.core.Tag
import io.kotest.core.annotation.EnabledCondition
import io.kotest.core.spec.Spec
import kotlin.reflect.KClass

/**
 * Docker/Testcontainers 필요 테스트 태그
 */
object IntegrationTag : Tag()

/**
 * Docker 사용 가능 여부 체크 조건
 *
 * @EnabledIf(DockerEnabledCondition::class) 로 사용
 * Docker 없으면 테스트 클래스 전체 스킵
 */
class DockerEnabledCondition : EnabledCondition {
    override fun enabled(kclass: KClass<out Spec>): Boolean {
        return PostgresTestContainer.isDockerAvailable
    }
}
