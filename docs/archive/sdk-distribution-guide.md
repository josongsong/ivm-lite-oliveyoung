# SDK 외부 배포 — 가이드

> **목적**: IVM Lite SDK를 Maven Central 또는 다른 저장소에 배포하는 방법  
> **대상**: 외부 개발자들이 사용할 수 있도록 공개 배포

---

## 📋 배포 전 체크리스트

### 1. SDK 모듈 분리 (권장)

현재 SDK는 전체 프로젝트와 함께 있으므로, 배포를 위해 SDK만 별도 모듈로 분리하는 것을 권장합니다.

**현재 구조**:
```
ivm-lite-oliveyoung-full/
  src/main/kotlin/com/oliveyoung/ivmlite/
    sdk/          # SDK 코드 (배포 대상)
    pkg/          # 내부 패키지 (배포 불필요)
    apps/         # 애플리케이션 (배포 불필요)
```

**옵션 1: 멀티 모듈로 분리 (권장)**
```
ivm-lite-oliveyoung-full/
  sdk/                    # 새 모듈 (배포용)
    build.gradle.kts
    src/main/kotlin/com/oliveyoung/ivmlite/sdk/...
  runtime/                # 기존 코드 (내부용)
    build.gradle.kts
    src/main/kotlin/com/oliveyoung/ivmlite/pkg/...
    src/main/kotlin/com/oliveyoung/ivmlite/apps/...
  build.gradle.kts        # 루트 빌드
  settings.gradle.kts     # 멀티 모듈 설정
```

**옵션 2: 현재 구조 유지 + Source Set 분리**
- SDK만 포함하는 별도 소스셋 생성
- 배포 시 SDK 소스셋만 포함
- 단순하지만 덜 깔끔함

---

## 🔧 배포 설정

### 1. Maven Publishing 플러그인 추가

**`build.gradle.kts` (SDK 모듈)**:
```kotlin
plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.serialization") version "1.9.25"
    `maven-publish`
    signing  // Maven Central 배포 시 필요
}

group = "com.oliveyoung"
version = "1.0.0"  // 또는 gradle.properties에서 읽기

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("IVM Lite SDK")
                description.set("IVM Lite SDK for Kotlin - Data ingestion, slicing, and sink delivery")
                url.set("https://github.com/oliveyoung/ivm-lite")
                
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("oliveyoung")
                        name.set("Olive Young")
                        email.set("dev@oliveyoung.com")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/oliveyoung/ivm-lite.git")
                    developerConnection.set("scm:git:ssh://github.com/oliveyoung/ivm-lite.git")
                    url.set("https://github.com/oliveyoung/ivm-lite")
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("ossrhUsername") as String?
                password = project.findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
```

### 2. Gradle Properties 설정

**`gradle.properties`**:
```properties
# 프로젝트 정보
group=com.oliveyoung
version=1.0.0

# Maven Central 배포 인증 정보
ossrhUsername=your-username
ossrhPassword=your-password

# GPG 서명 (Maven Central 필수)
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

### 3. SDK 의존성 정리

**SDK 모듈의 `build.gradle.kts`**:
```kotlin
dependencies {
    // ============================================
    // Kotlin Core (필수)
    // ============================================
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // ============================================
    // HTTP Client (SDK가 API 호출에 사용)
    // ============================================
    api("io.ktor:ktor-client-core:2.3.9")
    api("io.ktor:ktor-client-cio:2.3.9")
    api("io.ktor:ktor-client-content-negotiation:2.3.9")
    
    // ============================================
    // 기타 유틸리티
    // ============================================
    api("com.github.f4b6a3:tsid-creator:5.2.6")  // Version 생성용
    
    // ============================================
    // 내부 패키지 제외 (SDK는 독립적이어야 함)
    // ============================================
    // pkg.*, apps.* 등은 포함하지 않음
}
```

### 4. 현재 프로젝트에 바로 적용 (빠른 시작)

**현재 단일 모듈 프로젝트에 바로 적용하려면**:

**`build.gradle.kts`에 추가**:
```kotlin
plugins {
    // ... 기존 플러그인들 ...
    `maven-publish`
    signing
}

// 프로젝트 정보
group = "com.oliveyoung"
version = "1.0.0"  // 또는 gradle.properties에서

java {
    withSourcesJar()
    withJavadocJar()
}

// SDK만 포함하는 소스셋 생성 (선택사항)
sourceSets {
    create("sdk") {
        java.srcDirs("src/main/kotlin/com/oliveyoung/ivmlite/sdk")
        resources.srcDirs("src/main/resources")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            // SDK 소스셋만 포함하거나, 전체 프로젝트 포함
            from(components["java"])
            
            // 또는 SDK 소스셋만:
            // artifactId = "ivm-lite-sdk"
            // from(components["sdk"])
            
            pom {
                name.set("IVM Lite SDK")
                description.set("IVM Lite SDK for Kotlin")
                url.set("https://github.com/oliveyoung/ivm-lite")
                
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                
                developers {
                    developer {
                        id.set("oliveyoung")
                        name.set("Olive Young")
                    }
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("ossrhUsername") as String?
                password = project.findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
```

**`gradle.properties`에 추가**:
```properties
# 프로젝트 정보
group=com.oliveyoung
version=1.0.0

# Maven Central 배포 인증 정보 (로컬에만 저장, Git에 커밋 안 함)
ossrhUsername=your-username
ossrhPassword=your-password

# GPG 서명 (Maven Central 필수)
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

**`.gitignore`에 추가**:
```
# 배포 인증 정보는 Git에 커밋하지 않음
gradle.properties.local
*.gpg
```

---

## 📦 배포 프로세스

### 1. Maven Central 배포 (권장)

**전제 조건**:
- Sonatype OSSRH 계정 생성 (https://issues.sonatype.org/)
- GPG 키 생성 및 배포
- 프로젝트 정보 등록

**배포 단계**:

```bash
# 1. 버전 확인 및 업데이트
# gradle.properties에서 version 확인

# 2. 빌드 및 테스트
./gradlew clean build test

# 3. 서명 및 배포
./gradlew publishToMavenCentral

# 4. Sonatype Nexus에서 Staging Repository 확인
# https://s01.oss.sonatype.org/
# - Staging Repository 열기
# - Close → Release (수동 승인 필요)
```

**자동화 스크립트** (`scripts/publish.sh`):
```bash
#!/bin/bash
set -e

VERSION=$1
if [ -z "$VERSION" ]; then
    echo "Usage: ./scripts/publish.sh <version>"
    exit 1
fi

# 버전 업데이트
sed -i '' "s/version=.*/version=$VERSION/" gradle.properties

# 빌드 및 테스트
./gradlew clean build test

# 배포
./gradlew publishToMavenCentral

echo "Published version $VERSION"
echo "Check staging repository: https://s01.oss.sonatype.org/"
```

### 2. GitHub Packages 배포

**설정** (`build.gradle.kts`):
```kotlin
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/oliveyoung/ivm-lite")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

**배포**:
```bash
./gradlew publishToGitHubPackages
```

**사용**:
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/oliveyoung/ivm-lite")
        credentials {
            username = project.findProperty("gpr.user") as String?
            password = project.findProperty("gpr.token") as String?
        }
    }
}

dependencies {
    implementation("com.oliveyoung:ivm-lite-sdk:1.0.0")
}
```

### 3. 로컬 Maven 저장소 배포 (내부용, 권장)

**가장 간단한 방법**: 로컬 Maven 저장소에 배포하고 다른 프로젝트에서 사용

**배포**:
```bash
./gradlew publishToMavenLocal
```

**다른 프로젝트에서 사용**:
```kotlin
// build.gradle.kts
repositories {
    mavenLocal()  // 로컬 Maven 저장소 추가
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

**장점**:
- 설정 간단
- 빠른 반복 개발 가능
- 외부 저장소 불필요

**단점**:
- 각 개발자마다 `publishToMavenLocal` 실행 필요
- CI/CD에서는 별도 설정 필요

### 4. 로컬 파일 시스템 경로 (내부용, 대안)

**직접 JAR 파일 참조**:

**1단계: JAR 파일 생성**
```bash
./gradlew jar
# 또는 sources 포함
./gradlew jar sourcesJar
```

**2단계: 다른 프로젝트에서 참조**
```kotlin
// build.gradle.kts
dependencies {
    implementation(files("../ivm-lite-oliveyoung-full/build/libs/ivm-lite-1.0.0.jar"))
    // 또는
    implementation(fileTree("libs") { include("*.jar") })
}
```

**또는 로컬 Maven 저장소처럼 사용**:
```kotlin
repositories {
    maven {
        url = uri("../ivm-lite-oliveyoung-full/build/repo")  // 상대 경로
    }
}
```

### 5. 멀티 모듈 프로젝트 (내부용, 권장)

**같은 저장소 내 여러 프로젝트에서 사용하는 경우**:

**`settings.gradle.kts`**:
```kotlin
rootProject.name = "oliveyoung-services"

include("ivm-lite-sdk")
include("product-service")
include("order-service")

project(":ivm-lite-sdk").projectDir = file("../ivm-lite-oliveyoung-full")
```

**다른 프로젝트의 `build.gradle.kts`**:
```kotlin
dependencies {
    implementation(project(":ivm-lite-sdk"))
}
```

**또는 별도 저장소인 경우**:
```kotlin
// settings.gradle.kts
includeBuild("../ivm-lite-oliveyoung-full") {
    dependencySubstitution {
        substitute(module("com.oliveyoung:ivm-lite")).using(project(":"))
    }
}

// build.gradle.kts
dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

---

## 📝 사용자 가이드 작성

### README.md (SDK 모듈)

```markdown
# IVM Lite SDK

IVM Lite SDK for Kotlin - Data ingestion, slicing, and sink delivery.

## 설치

### Maven
```xml
<dependency>
    <groupId>com.oliveyoung</groupId>
    <artifactId>ivm-lite-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```kotlin
dependencies {
    implementation("com.oliveyoung:ivm-lite-sdk:1.0.0")
}
```

## Quick Start

```kotlin
import com.oliveyoung.ivmlite.sdk.Ivm

// 1. 설정
Ivm.configure {
    baseUrl = "https://api.example.com"
    tenantId = "your-tenant"
}

// 2. 데이터 배포
Ivm.client().product {
    sku = "SKU-001"
    name = "Product Name"
    price = 10000
}.deploy()

// 3. 데이터 조회
val product = Ivm.client().query(Views.Product.Pdp)
    .key("SKU-001")
    .get()
```

## 문서

- [SDK 가이드](./docs/sdk-guide.md)
- [API 레퍼런스](./docs/api-reference.md)
- [예제](./examples/)
```

---

## 🔐 보안 고려사항

### 1. 민감 정보 제외

**제외해야 할 것**:
- 내부 API 엔드포인트
- 데이터베이스 연결 정보
- 인증 토큰/키
- 내부 패키지 (`pkg.*`, `apps.*`)

**SDK는 다음만 포함**:
- `sdk.*` 패키지
- 공개 API 인터페이스
- 모델 클래스
- DSL 빌더

### 2. 의존성 최소화

**원칙**:
- 최소한의 의존성만 포함
- 내부 구현 세부사항 숨김
- 인터페이스 기반 설계

---

## 📊 버전 관리 전략

### Semantic Versioning

**형식**: `MAJOR.MINOR.PATCH`

- **MAJOR**: 호환되지 않는 API 변경
- **MINOR**: 하위 호환되는 기능 추가
- **PATCH**: 하위 호환되는 버그 수정

**예시**:
- `1.0.0`: 초기 릴리스
- `1.1.0`: 새로운 기능 추가 (하위 호환)
- `1.1.1`: 버그 수정
- `2.0.0`: API 변경 (하위 호환 안 됨)

### 버전 태그

```bash
# Git 태그 생성
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

---

## 🚀 CI/CD 자동화

### GitHub Actions 예시

**`.github/workflows/publish.yml`**:
```yaml
name: Publish SDK

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Cache Gradle
        uses: actions/cache@v3
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts') }}
      
      - name: Build
        run: ./gradlew clean build test
      
      - name: Publish to Maven Central
        env:
          OSSRH_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          OSSRH_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          SIGNING_KEY_ID: ${{ secrets.SIGNING_KEY_ID }}
          SIGNING_PASSWORD: ${{ secrets.SIGNING_PASSWORD }}
          SIGNING_SECRET_KEY_RING_FILE: ${{ secrets.SIGNING_SECRET_KEY_RING_FILE }}
        run: ./gradlew publishToMavenCentral
```

---

## 📋 배포 체크리스트

### 배포 전

- [ ] SDK 모듈 분리 완료
- [ ] 불필요한 의존성 제거
- [ ] 내부 패키지 제외 확인
- [ ] 테스트 통과 확인
- [ ] 문서 작성 완료
- [ ] 버전 번호 업데이트
- [ ] CHANGELOG 작성

### 배포 중

- [ ] 빌드 성공 확인
- [ ] 테스트 통과 확인
- [ ] 서명 확인
- [ ] Staging Repository 확인

### 배포 후

- [ ] Maven Central 동기화 확인 (보통 몇 시간 소요)
- [ ] 사용자 가이드 업데이트
- [ ] 릴리스 노트 작성
- [ ] 알림 발송 (필요시)

---

## 🎯 배포 옵션 비교

| 옵션 | 장점 | 단점 | 권장도 |
|------|------|------|--------|
| **Maven Central** | 표준, 널리 사용됨, 자동 동기화 | 승인 프로세스 필요, GPG 서명 필수 | ⭐⭐⭐⭐⭐ |
| **GitHub Packages** | 간단, 빠른 설정 | GitHub 계정 필요, 덜 표준적 | ⭐⭐⭐⭐ |
| **로컬 Maven** | 테스트용으로 빠름 | 배포 아님 | ⭐⭐ |
| **사설 저장소** | 완전한 제어 | 인프라 필요 | ⭐⭐⭐ |

---

## 📚 참고 자료

- [Maven Central 배포 가이드](https://central.sonatype.org/publish/publish-guide/)
- [Gradle Publishing 플러그인](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Semantic Versioning](https://semver.org/)
- [GPG 키 생성](https://central.sonatype.org/publish/requirements/gpg/)

---

## 💡 다음 단계

1. **SDK 모듈 분리**: 별도 모듈로 분리 (권장) 또는 현재 구조 유지
2. **Maven Central 계정**: Sonatype OSSRH 계정 생성 (https://issues.sonatype.org/)
3. **GPG 키 설정**: 서명용 키 생성 및 배포
4. **첫 배포**: 테스트 버전 배포 (예: `1.0.0-alpha.1`)
5. **문서화**: 사용자 가이드 및 API 문서 작성

---

## 🚀 빠른 시작 (현재 프로젝트에 바로 적용)

### 1단계: build.gradle.kts에 Publishing 설정 추가

```kotlin
plugins {
    // ... 기존 플러그인들 ...
    `maven-publish`
    signing
}

group = "com.oliveyoung"
version = "1.0.0"

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("IVM Lite SDK")
                description.set("IVM Lite SDK for Kotlin")
                url.set("https://github.com/oliveyoung/ivm-lite")
                
                licenses {
                    license {
                        name.set("Apache-2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
            }
        }
    }
    
    repositories {
        maven {
            name = "MavenCentral"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = project.findProperty("ossrhUsername") as String?
                password = project.findProperty("ossrhPassword") as String?
            }
        }
    }
}

signing {
    sign(publishing.publications["maven"])
}
```

### 2단계: 로컬 테스트 배포

```bash
# 로컬 Maven 저장소에 배포 (테스트)
./gradlew publishToMavenLocal

# 다른 프로젝트에서 테스트
# build.gradle.kts:
# repositories { mavenLocal() }
# dependencies { implementation("com.oliveyoung:ivm-lite:1.0.0") }
```

### 3단계: Maven Central 배포 (실제 배포)

```bash
# 1. Sonatype OSSRH 계정 생성 및 프로젝트 등록
# https://issues.sonatype.org/

# 2. GPG 키 생성 및 배포
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# 3. gradle.properties에 인증 정보 추가
# (Git에 커밋하지 않음!)

# 4. 배포
./gradlew clean build test publishToMavenCentral

# 5. Sonatype Nexus에서 Staging Repository 확인 및 Release
# https://s01.oss.sonatype.org/
```

### 4단계: 사용자 가이드 작성

**`README.md` (프로젝트 루트)**:
```markdown
# IVM Lite SDK

## 설치

### Gradle
```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

### Maven
```xml
<dependency>
    <groupId>com.oliveyoung</groupId>
    <artifactId>ivm-lite</artifactId>
    <version>1.0.0</version>
</dependency>
```

## 사용법

```kotlin
import com.oliveyoung.ivmlite.sdk.Ivm

Ivm.configure {
    baseUrl = "https://api.example.com"
    tenantId = "your-tenant"
}

Ivm.client().product {
    sku = "SKU-001"
    name = "Product Name"
}.deploy()
```

자세한 내용은 [SDK 가이드](./docs/sdk-guide.md)를 참고하세요.
```
