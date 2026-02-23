package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider

/**
 * credentialsModule() 단위 테스트
 *
 * 주입된 AwsCredentialsProvider가 Koin에 정상 등록되는지 검증.
 * (IntegrationTag 없음 - unitTest에서 실행됨)
 */
class CredentialsModuleTest : DescribeSpec({

    beforeEach {
        stopKoin()
    }

    afterEach {
        stopKoin()
    }

    describe("credentialsModule") {
        it("주입된 AwsCredentialsProvider를 Koin에 등록한다") {
            val provider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-key", "test-secret")
            )

            startKoin {
                modules(credentialsModule(provider))
            }

            val resolved = org.koin.core.context.GlobalContext.get().get<AwsCredentialsProvider>()
            resolved shouldBe provider
        }
    }
})
