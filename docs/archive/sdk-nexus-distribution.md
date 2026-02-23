# SDK Nexus — 배포 가이드

> **목적**: Nexus Repository에 SDK를 배포하고 사용하는 방법  
> **대상**: 조직 내부 Nexus 서버가 있는 경우

---

## 🎯 Nexus 개요

### Nexus Repository Manager란?

- **Sonatype Nexus Repository Manager**: 중앙화된 아티팩트 저장소
- **목적**: 조직 내부의 모든 라이브러리/아티팩트를 한 곳에서 관리
- **장점**: 중앙 관리, 버전 관리, 보안 정책, 프록시 캐싱

### Nexus Repository 구조

```
Nexus 서버
  ├── maven-releases/      # Release 버전 (1.0.0)
  │   └── com/oliveyoung/ivm-lite/1.0.0/
  │
  ├── maven-snapshots/    # Snapshot 버전 (1.0.0-SNAPSHOT)
  │   └── com/oliveyoung/ivm-lite/1.0.0-SNAPSHOT/
  │
  └── maven-public/        # 통합 뷰 (releases + snapshots + proxy)
      └── (자동으로 releases + snapshots 통합)
```

---

## 🔧 설정 방법

### 1단계: build.gradle.kts 설정

**`build.gradle.kts`에 추가**:
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
            name = "NexusReleases"
            url = uri("https://nexus.company.com/repository/maven-releases/")
            credentials {
                username = project.findProperty("nexusUsername") as String?
                password = project.findProperty("nexusPassword") as String?
            }
        }
        
        // Snapshot 버전용 (선택사항)
        maven {
            name = "NexusSnapshots"
            url = uri("https://nexus.company.com/repository/maven-snapshots/")
            credentials {
                username = project.findProperty("nexusUsername") as String?
                password = project.findProperty("nexusPassword") as String?
            }
        }
    }
}
```

**버전별 자동 라우팅** (권장):
```kotlin
publishing {
    repositories {
        maven {
            name = "Nexus"
            url = uri(
                if (version.toString().endsWith("-SNAPSHOT")) {
                    "https://nexus.company.com/repository/maven-snapshots/"
                } else {
                    "https://nexus.company.com/repository/maven-releases/"
                }
            )
            credentials {
                username = project.findProperty("nexusUsername") as String?
                password = project.findProperty("nexusPassword") as String?
            }
        }
    }
}
```

### 2단계: 인증 정보 설정

**옵션 A: gradle.properties (로컬 개발용)**
```properties
# Nexus 인증 정보
nexusUsername=your-username
nexusPassword=your-password
```

**옵션 B: 환경 변수 (CI/CD 권장)**
```bash
export NEXUS_USERNAME=your-username
export NEXUS_PASSWORD=your-password
```

**옵션 C: gradle.properties에서 환경 변수 읽기**
```properties
nexusUsername=${NEXUS_USERNAME}
nexusPassword=${NEXUS_PASSWORD}
```

**⚠️ 보안 주의**: `gradle.properties`에 비밀번호를 넣으면 Git에 커밋하지 마세요!

**`.gitignore`에 추가**:
```
gradle.properties.local
*.password
```

### 3단계: 배포

**Release 버전 배포**:
```bash
# gradle.properties에서 version 확인 (예: 1.0.0)
./gradlew clean build test publish
```

**Snapshot 버전 배포**:
```bash
# gradle.properties에서 version을 1.0.1-SNAPSHOT으로 변경
./gradlew clean build publish
```

**배포 확인**:
- Nexus UI: `https://nexus.company.com/#browse/browse:maven-releases:com/oliveyoung/ivm-lite`
- 또는: `https://nexus.company.com/repository/maven-releases/com/oliveyoung/ivm-lite/1.0.0/`

---

## 📦 다른 프로젝트에서 사용

### Release 버전 사용

**`build.gradle.kts`**:
```kotlin
repositories {
    maven {
        name = "NexusReleases"
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

### Snapshot 버전 사용 (개발 중)

**`build.gradle.kts`**:
```kotlin
repositories {
    maven {
        name = "NexusSnapshots"
        url = uri("https://nexus.company.com/repository/maven-snapshots/")
        credentials {
            username = project.findProperty("nexusUsername") as String?
            password = project.findProperty("nexusPassword") as String?
        }
    }
    mavenCentral()
}

dependencies {
    // Snapshot 버전 (항상 최신 빌드 사용)
    implementation("com.oliveyoung:ivm-lite:1.0.1-SNAPSHOT")
}
```

### 통합 뷰 사용 (간단)

**`build.gradle.kts`**:
```kotlin
repositories {
    maven {
        name = "NexusPublic"
        url = uri("https://nexus.company.com/repository/maven-public/")
        credentials {
            username = project.findProperty("nexusUsername") as String?
            password = project.findProperty("nexusPassword") as String?
        }
    }
    mavenCentral()
}

dependencies {
    // Release 또는 Snapshot 모두 사용 가능
    implementation("com.oliveyoung:ivm-lite:1.0.0")
    // 또는
    // implementation("com.oliveyoung:ivm-lite:1.0.1-SNAPSHOT")
}
```

---

## 🔄 버전 관리 전략

### Release vs Snapshot

| 버전 형식 | 용도 | 예시 | 특징 |
|----------|------|------|------|
| **Release** | 안정 버전 | `1.0.0` | 변경 불가, 재배포 불가 |
| **Snapshot** | 개발 버전 | `1.0.1-SNAPSHOT` | 변경 가능, 항상 최신 빌드 |

### 버전 업데이트 워크플로우

**개발 중**:
```properties
# gradle.properties
version=1.0.1-SNAPSHOT
```

```bash
# Snapshot 배포 (여러 번 가능)
./gradlew publish
```

**릴리스 준비**:
```properties
# gradle.properties
version=1.0.1
```

```bash
# Release 배포 (한 번만)
./gradlew publish
```

**다음 개발 버전**:
```properties
# gradle.properties
version=1.0.2-SNAPSHOT
```

---

## 🚀 CI/CD 통합

### GitHub Actions 예시

**`.github/workflows/publish-nexus.yml`**:
```yaml
name: Publish to Nexus

on:
  push:
    tags:
      - 'v*'  # v1.0.0 태그 푸시 시 실행
  workflow_dispatch:

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
      
      - name: Build
        run: ./gradlew clean build test
      
      - name: Publish to Nexus
        env:
          NEXUS_USERNAME: ${{ secrets.NEXUS_USERNAME }}
          NEXUS_PASSWORD: ${{ secrets.NEXUS_PASSWORD }}
        run: ./gradlew publish
```

**GitHub Secrets 설정**:
- `NEXUS_USERNAME`: Nexus 사용자명
- `NEXUS_PASSWORD`: Nexus 비밀번호

---

## 📋 Nexus 설정 체크리스트

### Nexus 관리자가 해야 할 일

- [ ] Nexus 서버 설치 및 설정
- [ ] `maven-releases` 저장소 생성
- [ ] `maven-snapshots` 저장소 생성
- [ ] `maven-public` 그룹 생성 (releases + snapshots 통합)
- [ ] 사용자 계정 생성 및 권한 부여
- [ ] 배포 권한 부여 (`deploy` 권한)
- [ ] 다운로드 권한 부여 (`read` 권한)

### 개발자가 해야 할 일

- [ ] `build.gradle.kts`에 Nexus 설정 추가
- [ ] 인증 정보 설정 (gradle.properties 또는 환경 변수)
- [ ] 배포 테스트
- [ ] 다른 프로젝트에서 사용 테스트

---

## 💡 권장 사항

### 내부용으로는 Nexus가 가장 적합

**이유**:
1. 중앙 관리: 모든 아티팩트를 한 곳에서 관리
2. 버전 관리: Release/Snapshot 분리로 안정성 보장
3. 보안: 접근 제어 및 검증 규칙 설정 가능
4. 프록시: 외부 저장소 캐싱으로 빌드 속도 향상
5. 검색: Nexus UI에서 아티팩트 검색 가능

### Nexus OSS vs Nexus Pro

| 기능 | Nexus OSS (무료) | Nexus Pro (유료) |
|------|-----------------|-----------------|
| Maven 저장소 | ✅ | ✅ |
| Docker Registry | ✅ | ✅ |
| NPM Registry | ✅ | ✅ |
| 보안 스캔 | ❌ | ✅ |
| 고급 정책 | ❌ | ✅ |
| 지원 | 커뮤니티 | 공식 지원 |

**내부용으로는 Nexus OSS로 충분합니다.**

---

## 🔍 문제 해결

### 배포 실패 시

**에러**: `401 Unauthorized`
- **원인**: 인증 정보 오류
- **해결**: `nexusUsername`, `nexusPassword` 확인

**에러**: `403 Forbidden`
- **원인**: 배포 권한 없음
- **해결**: Nexus 관리자에게 `deploy` 권한 요청

**에러**: `409 Conflict`
- **원인**: Release 버전 재배포 시도
- **해결**: Release 버전은 재배포 불가, 새 버전 사용

### 다운로드 실패 시

**에러**: `Could not resolve`
- **원인**: 저장소 URL 또는 인증 정보 오류
- **해결**: `repositories` 설정 확인

---

## 📚 참고 자료

- [Nexus Repository Manager 문서](https://help.sonatype.com/repomanager3)
- [Maven Publishing 가이드](https://docs.gradle.org/current/userguide/publishing_maven.html)
- [Nexus 설치 가이드](https://help.sonatype.com/repomanager3/installation)
