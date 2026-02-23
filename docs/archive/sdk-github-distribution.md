# SDK GitHub — 배포 가이드

> **목적**: GitHub에 코드를 올리고 의존성으로 사용하는 방법  
> **대상**: 내부 프로젝트에서 GitHub 기반 배포

---

## 🎯 GitHub 기반 배포 방법 비교

| 방법 | 설정 난이도 | 비용 | 권장도 |
|------|------------|------|--------|
| **JitPack** | ⭐ 매우 쉬움 | 무료 | ⭐⭐⭐⭐⭐ |
| **GitHub Packages** | ⭐⭐ 쉬움 | 무료 (Public) | ⭐⭐⭐⭐ |
| **로컬 Maven** | ⭐⭐ 쉬움 | 무료 | ⭐⭐⭐ |

---

## 방법 1: JitPack (가장 간단, 권장)

### 특징

- **설정 불필요**: GitHub에 코드만 올리면 자동으로 Maven 저장소 제공
- **무료**: Public 저장소 무료
- **자동 빌드**: GitHub 푸시 시 자동 빌드

### 사용 방법

**1단계: GitHub에 코드 푸시**
```bash
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/oliveyoung/ivm-lite-oliveyoung-full.git
git push -u origin main
```

**2단계: 버전 태그 생성 (선택사항)**
```bash
# 버전 태그 생성
git tag v1.0.0
git push origin v1.0.0

# 또는 커밋 해시 사용 가능
```

**3단계: 다른 프로젝트에서 사용**
```kotlin
// build.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    // 방법 1: 태그 사용
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.0")
    
    // 방법 2: 브랜치 사용
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:main-SNAPSHOT")
    
    // 방법 3: 커밋 해시 사용
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:abc1234")
}
```

**4단계: JitPack 빌드 확인**
- https://jitpack.io/#oliveyoung/ivm-lite-oliveyoung-full
- 첫 빌드는 몇 분 소요될 수 있음

### 장점

- 설정 완전 불필요
- GitHub에만 올리면 자동 배포
- 무료 (Public 저장소)
- 버전 관리 용이 (Git 태그)

### 단점

- 첫 빌드가 느릴 수 있음 (5-10분)
- Private 저장소는 유료
- 빌드 실패 시 수동 재시도 필요

---

## 방법 2: GitHub Packages

### 특징

- GitHub의 공식 패키지 저장소
- Private 저장소도 무료
- GitHub Actions와 연동 가능

### 설정 방법

**1단계: `build.gradle.kts`에 GitHub Packages 설정 추가**

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

**2단계: GitHub Personal Access Token 생성**

1. GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate new token (classic)
3. 권한 선택:
   - `read:packages` (다운로드용)
   - `write:packages` (업로드용)
4. 토큰 생성 후 복사

**3단계: 토큰 설정**

**옵션 A: 환경 변수 (권장)**
```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxx
export GITHUB_ACTOR=oliveyoung  # GitHub 사용자명
```

**옵션 B: gradle.properties (로컬만)**
```properties
gpr.user=oliveyoung
gpr.token=ghp_xxxxxxxxxxxxx
```

**⚠️ 주의**: `gradle.properties`에 토큰을 넣으면 Git에 커밋하지 마세요!

**4단계: 배포**
```bash
./gradlew clean build publish
```

**5단계: 다른 프로젝트에서 사용**
```kotlin
// build.gradle.kts
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

### 장점

- GitHub 공식 서비스
- Private 저장소도 무료
- GitHub Actions와 연동 가능
- 버전 관리 용이

### 단점

- 토큰 관리 필요
- 설정이 JitPack보다 복잡

---

## 방법 3: GitHub Actions 자동 배포

### GitHub Actions로 자동 배포 설정

**`.github/workflows/package-publish.yml`**:
```yaml
name: Publish package to Github Package

on:
  workflow_call:
    inputs:
      jdk-version:
        type: string
        default: '17'
      module:
        required: false
        type: string
        default: ''
  push:
    tags:
      - 'v*'  # v1.0.0 같은 태그 푸시 시 자동 실행
  workflow_dispatch:  # 수동 실행도 가능

jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: 'corretto'
          java-version: ${{ inputs.jdk-version || '17' }}

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle.kts') }}

      - name: Build
        run: ./gradlew clean build test

      - name: Publish package
        run: |
          if [ -n "${{ inputs.module }}" ]; then
            ./gradlew ${{ inputs.module }}:publish
          else
            ./gradlew publish
          fi
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          GITHUB_ACTOR: ${{ github.actor }}
```

**다른 워크플로우에서 재사용**:
```yaml
# 다른 워크플로우에서 호출
jobs:
  publish-sdk:
    uses: ./.github/workflows/package-publish.yml
    with:
      jdk-version: '17'
      module: ''  # 단일 모듈이면 비워둠
```

**사용**:
```bash
# 태그 생성 및 푸시
git tag v1.0.0
git push origin v1.0.0

# GitHub Actions가 자동으로 빌드 및 배포
```

**태그 관리 방법**:
- **로컬에서 직접**: `git tag v1.0.0 && git push origin v1.0.0`
- **GitHub Releases**: UI에서 태그 생성 및 릴리스 노트 작성
- **자동화 스크립트**: `scripts/release.sh` 사용 (권장)
- 자세한 내용은 [버전 태그 관리 가이드](./version-tag-management.md) 참고

---

## 🚀 빠른 시작 (JitPack 방식, 가장 간단)

### 1단계: GitHub에 코드 푸시

```bash
# 이미 GitHub에 올려져 있다면 스킵
git remote add origin https://github.com/oliveyoung/ivm-lite-oliveyoung-full.git
git push -u origin main
```

### 2단계: 버전 태그 생성

```bash
git tag v1.0.0
git push origin v1.0.0
```

### 3단계: 다른 프로젝트에서 사용

```kotlin
// build.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.0")
}
```

**끝!** 설정 불필요, 자동 빌드 및 배포

---

## 📋 버전 관리

### JitPack 버전 형식

```kotlin
// 태그
implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.0")

// 브랜치
implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:main-SNAPSHOT")

// 커밋 해시
implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:abc1234")

// 특정 커밋의 태그
implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.0")
```

### GitHub Packages 버전 형식

```kotlin
// gradle.properties의 version 사용
implementation("com.oliveyoung:ivm-lite:1.0.0")
```

---

## 🔐 보안 고려사항

### GitHub Personal Access Token

**절대 Git에 커밋하지 마세요!**

**안전한 방법**:
1. 환경 변수 사용 (권장)
2. `gradle.properties`에 추가하되 `.gitignore`에 추가
3. GitHub Secrets 사용 (GitHub Actions)

**`.gitignore`에 추가**:
```
gradle.properties.local
*.token
```

---

## 💡 권장 사항

### 내부용으로는 JitPack이 가장 간단

**이유**:
1. 설정 완전 불필요
2. GitHub에만 올리면 자동 배포
3. 무료
4. 버전 관리 용이 (Git 태그)

**워크플로우**:
```bash
# 1. 코드 변경
git commit -m "Update SDK"

# 2. 버전 태그 생성
git tag v1.0.1
git push origin v1.0.1

# 3. JitPack이 자동 빌드 (5-10분 소요)

# 4. 다른 프로젝트에서 사용
# implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.1")
```

---

## 📚 참고 자료

- [JitPack 문서](https://jitpack.io/docs/)
- [GitHub Packages 문서](https://docs.github.com/en/packages)
- [GitHub Actions 문서](https://docs.github.com/en/actions)
