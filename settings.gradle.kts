// ============================================
// 🚀 SOTA Build Settings
// ============================================

rootProject.name = "ivm-lite"

// ============================================
// Plugin Management (버전 일관성)
// ============================================
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// ============================================
// Dependency Resolution (중앙 집중)
// ============================================
dependencyResolutionManagement {
    // FAIL_ON_PROJECT_REPOS: 프로젝트에서 repositories 선언 시 빌드 실패 (권장)
    // PREFER_SETTINGS: settings의 repositories 우선, 프로젝트 repositories는 무시 (경고만)
    // PREFER_PROJECT: 프로젝트 repositories 우선
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // 필요시 추가 레포지토리
        // maven("https://your-nexus.com/repository/maven-public/")
    }
}

// ============================================
// Build Cache 설정 (SOTA)
// ============================================
buildCache {
    local {
        // 로컬 빌드 캐시 활성화
        isEnabled = true
        
        // 캐시 디렉토리 (기본값 사용)
        // directory = File(rootDir, ".gradle/build-cache")
        
        // 캐시 정리 정책 (7일 이상된 항목 삭제)
        removeUnusedEntriesAfterDays = 7
    }
    
    // 원격 빌드 캐시 (팀 공유용 - 필요시 활성화)
    // remote<HttpBuildCache> {
    //     url = uri("https://your-build-cache-server/cache/")
    //     isAllowInsecureProtocol = false
    //     isPush = true
    //     credentials {
    //         username = System.getenv("BUILD_CACHE_USER") ?: ""
    //         password = System.getenv("BUILD_CACHE_PASSWORD") ?: ""
    //     }
    // }
}

// ============================================
// Feature Preview (실험적 기능)
// ============================================
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
