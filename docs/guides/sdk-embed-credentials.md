# SDK Embed — CredentialsProvider 주입

IVM-Lite를 라이브러리로 embed하는 외부 앱에서 AWS 자격 증명을 주입하는 방법입니다.

---

## 1. 사용 시나리오

| 시나리오 | CredentialsProvider 주입 | 설정 방법 |
|----------|--------------------------|-----------|
| HTTP API만 사용 | 불필요 | IVM-Lite 서버에 IAM 역할 연결 |
| In-process embed + IAM 역할 | 불필요 | EC2/ECS/Lambda에 IAM 역할 연결 |
| In-process embed + 자체 credentials | **필요** | `credentialsModule()` 사용 |
| In-process embed + 환경 변수 | 불필요 | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` |

---

## 2. CredentialsProvider 주입 방법

### 2.1 credentialsModule() 사용 (권장)

```kotlin
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.credentialsModule
import com.oliveyoung.ivmlite.apps.runtimeapi.wiring.productionModules
import org.koin.ktor.plugin.Koin
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

// 자체 Provider (예: STS AssumeRole, WebIdentity 등)
val myProvider: AwsCredentialsProvider = MyAppCredentialsProvider.create()

install(Koin) {
    modules(listOf(credentialsModule(myProvider)) + productionModules)
}
```

### 2.2 Koin module 직접 정의

```kotlin
import org.koin.dsl.module
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

val myCredentialsModule = module {
    single<AwsCredentialsProvider> {
        // 자체 IAM/STS/WebIdentity 등
        MyAppCredentialsProvider.create()
    }
}

install(Koin) {
    modules(listOf(myCredentialsModule) + productionModules)
}
```

**중요**: `AwsCredentialsProvider`를 제공하는 모듈은 `infraModule`보다 **먼저** 로드되어야 합니다. `listOf(myModule) + productionModules` 순서를 지키세요.

---

## 3. 우선순위

InfraModule의 자격 증명 해석 순서:

1. **주입된 `AwsCredentialsProvider`** (Koin에 바인딩된 경우)
2. `awsProfile` (AWS CLI 프로필)
3. `accessKeyId` / `secretAccessKey` (환경 변수 또는 설정)
4. `DefaultCredentialsProvider` (환경 변수, ~/.aws/credentials, IAM 역할)

---

## 4. 예시: STS AssumeRole

```kotlin
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.services.sts.StsClient
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider

val provider: AwsCredentialsProvider = StsAssumeRoleCredentialsProvider.builder()
    .refreshRequest { it.roleArn("arn:aws:iam::123456789012:role/MyAppRole").roleSessionName("ivm-lite") }
    .stsClient(StsClient.create())
    .build()

install(Koin) {
    modules(listOf(credentialsModule(provider)) + productionModules)
}
```

---

## 5. 관련 문서

- [RFC-019 External SDK Integration](../rfc_archive/2026-02/RFC-019-external-sdk-integration.md)
- [IAM 역할 설정](../.cursorrules) - ECS/EC2/Lambda IAM 연결 방법
