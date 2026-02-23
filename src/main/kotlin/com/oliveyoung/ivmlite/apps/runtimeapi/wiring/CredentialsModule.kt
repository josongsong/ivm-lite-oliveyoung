package com.oliveyoung.ivmlite.apps.runtimeapi.wiring

import org.koin.core.module.Module
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

/**
 * CredentialsModule - 외부 앱 embed 시 CredentialsProvider 주입용
 *
 * IVM-Lite를 라이브러리로 embed하는 외부 앱이 자체 AWS 자격 증명을 주입할 때 사용합니다.
 * infraModule보다 **먼저** 로드해야 합니다.
 *
 * @example IAM 역할 (EC2/ECS/Lambda) - 주입 불필요
 * ```kotlin
 * // DefaultCredentialsProvider가 자동으로 IMDS/ECS credentials 사용
 * loadKoinModules(productionModules)
 * ```
 *
 * @example 자체 CredentialsProvider 주입
 * ```kotlin
 * val myProvider = MyAppStsCredentialsProvider.create()
 * loadKoinModules(
 *     listOf(credentialsModule(myProvider)) + productionModules
 * )
 * ```
 *
 * @example 환경 변수 기반 (이미 DefaultCredentialsProvider가 처리)
 * ```kotlin
 * // AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY 설정 시
 * // resolveCredentialsFromConfig()가 StaticCredentialsProvider 사용
 * loadKoinModules(productionModules)
 * ```
 */
fun credentialsModule(provider: AwsCredentialsProvider): Module = module {
    single<AwsCredentialsProvider> { provider }
}
