# SDK 배포 — 빠른 시작 (핵심만)

> **목적**: SDK를 의존성으로 사용하는 가장 빠른 방법

---

## 🎯 방법 선택

| 방법 | 언제 사용? | 명령어 |
|------|-----------|--------|
| **로컬 Maven** | 로컬 개발 | `./gradlew publishToMavenLocal` |
| **JitPack** | GitHub에 올려서 | `git tag v1.0.0 && git push` |
| **GitHub Packages** | 조직 내부 | `./gradlew publish` |
| **Nexus** | Nexus 서버 있으면 | `./gradlew publish` |

---

## 방법 1: 로컬 Maven (가장 간단)

**SDK 프로젝트**:
```bash
./gradlew publishToMavenLocal
```

**다른 프로젝트**:
```kotlin
repositories {
    mavenLocal()  // 최상단!
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

---

## 방법 2: JitPack (GitHub 기반, 가장 간단)

**SDK 프로젝트**:
```bash
git tag v1.0.0
git push origin v1.0.0
```

**다른 프로젝트**:
```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
    mavenCentral()
}

dependencies {
    implementation("com.github.oliveyoung:ivm-lite-oliveyoung-full:v1.0.0")
}
```

---

## 방법 3: GitHub Packages (이미 설정됨)

**SDK 프로젝트**:
```bash
export GITHUB_TOKEN=ghp_xxx
export GITHUB_ACTOR=your-username
./gradlew publish
```

**다른 프로젝트**:
```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/oyg-dev/global-jvm-packages")
        credentials {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

---

## 방법 4: Nexus (이미 설정됨)

**SDK 프로젝트**:
```bash
# gradle.properties에 설정
# nexusUsername=your-username
# nexusPassword=your-password

./gradlew publish
```

**다른 프로젝트**:
```kotlin
repositories {
    maven {
        url = uri("https://nexus.company.com/repository/maven-releases/")
        credentials {
            username = project.findProperty("nexusUsername")
            password = project.findProperty("nexusPassword")
        }
    }
    mavenCentral()
}

dependencies {
    implementation("com.oliveyoung:ivm-lite:1.0.0")
}
```

---

## 버전 태그 관리

**릴리스**:
```bash
# 스크립트 사용 (권장)
./scripts/release.sh patch  # 1.0.0 → 1.0.1

# 또는 수동
git tag v1.0.0
git push origin v1.0.0
```

**끝!**
