# SDK 내부 배포 — 가이드 (의존성 추가 방식)

> **목적**: IVM Lite SDK를 내부 프로젝트에서 의존성으로 사용하는 방법  
> **대상**: 같은 조직/팀 내 다른 프로젝트에서 SDK 사용

---

## 🎯 내부용 배포 방법 비교

| 방법 | 장점 | 단점 | 권장도 |
|------|------|------|--------|
| **로컬 Maven 저장소** | 간단, 빠름 | 각자 배포 필요 | ⭐⭐⭐⭐⭐ |
| **파일 시스템 경로** | 매우 간단 | 경로 관리 필요 | ⭐⭐⭐ |
| **멀티 모듈** | 자동 동기화 | 같은 저장소 필요 | ⭐⭐⭐⭐ |
| **사설 Maven 저장소** | 중앙 관리 | 인프라 필요 | ⭐⭐⭐⭐ |

---

## 방법 1: 로컬 Maven 저장소 (가장 간단, 권장)

### SDK 프로젝트에서 배포

**`build.gradle.kts`에 추가**:
```kotlin
plugins {
    // ... 기존 플러그인들 ...
    `maven-publish`
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
                description.set("IVM Lite SDK for Kotlin (Internal)")
            }
        }
    }
}
```

**배포 명령**:
```bash
# 로컬 Maven 저장소 (~/.m2/repository)에 배포
./gradlew publishToMavenLocal
```

### 다른 프로젝트에서 사용

**`build.gradle.kts`**:
```kotlin
repositories {
    mavenLocal()  // 로컬 Maven 저장소 추가 (최상단에!)
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

**사용 예시**:
```kotlin
import com.oliveyoung.ivmlite.sdk.Ivm

Ivm.configure {
    baseUrl = "http://localhost:8080"
    tenantId = "oliveyoung"
}

Ivm.client().product {
    sku = "SKU-001"
    name = "Product Name"
}.deploy()
```

**업데이트 시**:
```bash
# SDK 프로젝트에서
./gradlew clean publishToMavenLocal

# 사용하는 프로젝트에서
./gradlew --refresh-dependencies build
```

---

## 방법 2: 파일 시스템 경로 (빠른 테스트용)

### JAR 파일 직접 참조

**1단계: SDK 프로젝트에서 JAR 생성**
```bash
./gradlew jar sourcesJar
# 결과: build/libs/ivm-lite-1.0.0.jar
```

**2단계: 다른 프로젝트에서 참조**
```kotlin
// build.gradle.kts
dependencies {
    // 절대 경로
    implementation(files("/path/to/ivm-lite-oliveyoung-full/build/libs/ivm-lite-1.0.0.jar"))
    
    // 또는 상대 경로
    implementation(files("../ivm-lite-oliveyoung-full/build/libs/ivm-lite-1.0.0.jar"))
    
    // 또는 디렉토리 전체
    implementation(fileTree("libs") { include("*.jar") })
}
```

**장점**: 매우 빠름, 설정 최소  
**단점**: 경로 관리 필요, 버전 관리 어려움

---

## 방법 3: 로컬 Maven 저장소 (파일 시스템 기반)

### 상대 경로로 Maven 저장소 설정

**SDK 프로젝트 `build.gradle.kts`**:
```kotlin
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    
    repositories {
        maven {
            url = uri("${project.buildDir}/repo")  // build/repo에 배포
        }
    }
}
```

**배포**:
```bash
./gradlew publish
# 결과: build/repo/com/oliveyoung/ivm-lite/1.0.0/
```

**다른 프로젝트에서 사용**:
```kotlin
repositories {
    maven {
        url = uri("../ivm-lite-oliveyoung-full/build/repo")  // 상대 경로
    }
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

**장점**: Git에 포함 가능 (빌드 아티팩트), 경로 고정  
**단점**: 빌드 디렉토리 관리 필요

---

## 방법 4: 멀티 모듈 프로젝트 (같은 저장소)

### Composite Build 사용

**다른 프로젝트의 `settings.gradle.kts`**:
```kotlin
rootProject.name = "my-service"

includeBuild("../ivm-lite-oliveyoung-full") {
    dependencySubstitution {
        substitute(module("com.oliveyoung:ivm-lite")).using(project(":"))
    }
}
```

**`build.gradle.kts`**:
```kotlin
dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
    // Gradle이 자동으로 includeBuild된 프로젝트를 사용
}
```

**장점**: 자동 동기화, 소스 코드 직접 참조 가능  
**단점**: 같은 저장소 구조 필요

---

## 방법 5: GitHub Packages (GitHub에 올려서 사용, 권장)

### GitHub Packages 사용

**가장 간단한 방법**: GitHub에 코드를 올리고 GitHub Packages로 배포

**SDK 프로젝트 `build.gradle.kts`**:
```kotlin
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("IVM Lite SDK")
                description.set("IVM Lite SDK for Kotlin")
            }
        }
    }
    
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/oliveyoung/ivm-lite-oliveyoung-full")
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
# GitHub Personal Access Token 필요 (GITHUB_TOKEN 환경 변수 또는 gradle.properties)
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxx
./gradlew publish
```

**다른 프로젝트에서 사용**:
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/oliveyoung/ivm-lite-oliveyoung-full")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

**장점**: 
- GitHub에 코드만 올리면 됨
- 별도 인프라 불필요
- 버전 관리 용이 (Git 태그와 연동)

**단점**: 
- GitHub 계정 필요
- Private 저장소는 토큰 필요

### GitHub Personal Access Token 생성

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token (classic)
3. 권한 선택:
   - `read:packages` (다운로드용)
   - `write:packages` (업로드용)
4. 토큰 생성 후 복사

**사용**:
```bash
# 환경 변수로 설정
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxx

# 또는 gradle.properties에 추가 (Git에 커밋하지 않음!)
gpr.user=your-username
gpr.token=ghp_xxxxxxxxxxxxx
```

## 방법 6: JitPack (GitHub 연동, 가장 간단)

### JitPack 사용

**설정 불필요**: GitHub에 코드만 올리면 자동으로 Maven 저장소 제공

**SDK 프로젝트**: 설정 불필요! 그냥 GitHub에 올리면 됨

**다른 프로젝트에서 사용**:
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    // GitHub 저장소 URL 기반
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:1.0.0")
    // 또는 특정 커밋/태그
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:main-SNAPSHOT")
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.0")
}
```

**배포**:
```bash
# 1. GitHub에 코드 푸시
git push origin main

# 2. 태그 생성 (버전 지정)
git tag v1.0.0
git push origin v1.0.0

# 3. JitPack이 자동으로 빌드 및 배포
# https://jitpack.io/#oliveyoung/ivm-lite-oliveyoung-full
```

**장점**: 
- 설정 완전 불필요
- GitHub에만 올리면 자동 배포
- 무료

**단점**: 
- 첫 빌드가 느릴 수 있음
- Public 저장소만 지원 (Private는 유료)

## 방법 7: 사설 Maven 저장소 (조직 내부)

### Nexus/Artifactory 사용

**SDK 프로젝트 `build.gradle.kts`**:
```kotlin
publishing {
    repositories {
        maven {
            name = "InternalRepo"
            url = uri("https://nexus.company.com/repository/maven-releases/")
            credentials {
                username = project.findProperty("nexusUsername") as String?
                password = project.findProperty("nexusPassword") as String?
            }
        }
    }
}
```

**배포**:
```bash
./gradlew publish
```

**다른 프로젝트에서 사용**:
```kotlin
repositories {
    maven {
        url = uri("https://nexus.company.com/repository/maven-releases/")
        credentials {
            username = project.findProperty("nexusUsername") as String?
            password = project.findProperty("nexusPassword") as String?
        }
    }
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

**장점**: 중앙 관리, 버전 관리 용이  
**단점**: 인프라 필요

---

## 🚀 빠른 시작 (로컬 Maven 저장소 방식)

### 1단계: SDK 프로젝트 설정

**`build.gradle.kts`에 추가** (최하단):
```kotlin
plugins {
    // ... 기존 플러그인들 ...
    `maven-publish`
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
                description.set("IVM Lite SDK for Kotlin (Internal)")
            }
        }
    }
}
```

**`gradle.properties`에 추가**:
```properties
group=com.oliveyoung
version=1.0.0
```

### 2단계: 배포

```bash
./gradlew clean build publishToMavenLocal
```

**확인**:
```bash
ls ~/.m2/repository/com/oliveyoung/ivm-lite/1.0.0/
# ivm-lite-1.0.0.jar
# ivm-lite-1.0.0-sources.jar
# ivm-lite-1.0.0.pom
```

### 3단계: 다른 프로젝트에서 사용

**`build.gradle.kts`**:
```kotlin
repositories {
    mavenLocal()  // ← 이게 중요! 최상단에 추가
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

**사용**:
```kotlin
import com.oliveyoung.ivmlite.sdk.Ivm

Ivm.configure {
    baseUrl = "http://localhost:8080"
    tenantId = "oliveyoung"
}

val result = Ivm.client().product {
    sku = "SKU-001"
    name = "Product Name"
}.deploy()
```

---

## 📋 버전 관리

### 버전 업데이트

**`gradle.properties`**:
```properties
version=1.0.1  # 버전 업데이트
```

**재배포**:
```bash
./gradlew clean publishToMavenLocal
```

**사용하는 프로젝트에서 업데이트**:
```kotlin
dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.1")  // 버전 변경
}
```

**또는 최신 버전 자동 사용**:
```kotlin
dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.+")  // 1.0.x 최신 버전
}
```

---

## 🔄 개발 워크플로우

### 개발 중 빠른 반복

**SDK 변경 → 배포 → 테스트**:
```bash
# SDK 프로젝트에서
./gradlew publishToMavenLocal

# 사용하는 프로젝트에서
./gradlew --refresh-dependencies build
```

**자동화 스크립트** (`scripts/publish-local.sh`):
```bash
#!/bin/bash
set -e

echo "Building and publishing SDK to local Maven repository..."
./gradlew clean build publishToMavenLocal
echo "✅ Published to ~/.m2/repository/com/oliveyoung/ivm-lite/"
```

---

## 💡 권장 사항

### 내부용으로는 로컬 Maven 저장소 방식 권장

**이유**:
1. 설정이 간단함
2. 빠른 반복 개발 가능
3. 외부 저장소 불필요
4. 표준적인 방법

**주의사항**:
- 각 개발자가 `publishToMavenLocal` 실행 필요
- CI/CD에서는 별도 설정 필요 (로컬 Maven 저장소 사용 불가)
- 버전 충돌 주의 (각자 다른 버전 배포 가능)

### CI/CD 환경에서는

**옵션 1: 사설 Maven 저장소 사용**
- Nexus/Artifactory 등
- 중앙 관리, 일관성 보장

**옵션 2: Git Submodule 또는 Composite Build**
- 소스 코드 직접 참조
- 자동 동기화

---

## 📚 예제 프로젝트 구조

```
oliveyoung-services/
  ├── ivm-lite-sdk/              # SDK 프로젝트
  │   ├── build.gradle.kts
  │   └── src/main/kotlin/...
  │
  ├── product-service/            # SDK 사용하는 프로젝트
  │   ├── build.gradle.kts       # implementation("com.oliveyoung:ivm-lite:1.0.0")
  │   └── src/main/kotlin/...
  │
  └── order-service/             # SDK 사용하는 프로젝트
      ├── build.gradle.kts       # implementation("com.oliveyoung:ivm-lite:1.0.0")
      └── src/main/kotlin/...
```

**또는 별도 저장소**:
```
ivm-lite-oliveyoung-full/        # SDK 프로젝트 (별도 저장소)
  └── build.gradle.kts

product-service/                  # SDK 사용하는 프로젝트
  └── build.gradle.kts           # mavenLocal() + implementation(...)
```

---

## 🎯 결론

**내부용으로는 로컬 Maven 저장소 방식이 가장 간단하고 실용적입니다.**

1. SDK 프로젝트에 `maven-publish` 플러그인 추가
2. `publishToMavenLocal` 실행
3. 사용하는 프로젝트에 `mavenLocal()` 추가
4. `implementation("com.oliveyoung:ivm-lite:1.0.0")` 추가

끝!
