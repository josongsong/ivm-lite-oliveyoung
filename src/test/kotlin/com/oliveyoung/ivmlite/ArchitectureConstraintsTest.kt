package com.oliveyoung.ivmlite

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.StringSpec

/**
 * RFC-V4-010 + RFC-IMPL-009 아키텍처 제약 테스트
 * 
 * 핵심 규칙:
 * - 비즈니스 도메인(rawdata, slices, changeset) 간 직접 import 금지
 * - contracts는 설정/스키마이므로 모든 도메인에서 참조 가능
 * - apps는 orchestration만 호출
 * - shared는 도메인 로직 금지
 * - DI는 wiring 패키지에서만
 */
class ArchitectureConstraintsTest : StringSpec({

    val classes: JavaClasses = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("com.oliveyoung.ivmlite")

    "RFC-V4-010: 비즈니스 도메인 간 직접 import 금지" {
        // rawdata <-> slices <-> changeset 비즈니스 도메인 간 직접 통신 금지
        // contracts는 설정/스키마이므로 예외
        // RFC 의도:
        // - 도메인 **타입**(Record, VO 등)은 허용
        // - Port 인터페이스를 통한 DI는 허용 (Dependency Inversion Principle)
        // - 도메인 **서비스/로직** 직접 호출만 금지
        val businessDomains = listOf("rawdata", "changeset", "slices")

        // 도메인 타입(Record) 및 Port 인터페이스 참조는 허용
        val isAllowedDependency = object : DescribedPredicate<JavaClass>("is allowed cross-domain dependency") {
            override fun test(input: JavaClass): Boolean {
                val name = input.simpleName
                // Record 클래스, Port 인터페이스, Result 타입은 허용
                return name.endsWith("Record") ||
                    name.endsWith("Port") ||
                    name.contains("Result") ||
                    input.packageName.contains(".ports")
            }
        }

        businessDomains.forEach { domainA ->
            businessDomains.filter { it != domainA }.forEach { domainB ->
                noClasses()
                    .that().resideInAPackage("..pkg.$domainA..")
                    .should().dependOnClassesThat(
                        com.tngtech.archunit.core.domain.JavaClass.Predicates
                            .resideInAPackage("..pkg.$domainB..")
                            .and(DescribedPredicate.not(isAllowedDependency))
                    )
                    .check(classes)
            }
        }
    }

    "RFC-V4-010: apps는 도메인 서비스 직접 호출 금지 (orchestration 통해서만)" {
        // RFC-IMPL-010: Port 추상화 완료 → 강제 적용
        // playground는 개발/테스트용이므로 제외
        // RFC 의도:
        // - 도메인 **타입**(Record, VO, Entry 등)은 허용
        // - Port 인터페이스를 통한 DI는 허용
        // - 도메인 **서비스/로직** 직접 호출만 금지

        val isDomainServiceClass = object : DescribedPredicate<JavaClass>("is domain service class") {
            override fun test(input: JavaClass): Boolean {
                val name = input.simpleName
                val pkg = input.packageName

                // domain 패키지 내 서비스 클래스 패턴
                if (!pkg.contains(".domain")) return false

                // 서비스 클래스 패턴 (금지 대상)
                return name.endsWith("Engine") ||
                    name.endsWith("Builder") ||
                    name.endsWith("Calculator") ||
                    name.endsWith("Service") ||
                    name.endsWith("Executor") ||
                    name.endsWith("Handler") ||
                    name.endsWith("Processor")
            }
        }

        noClasses()
            .that().resideInAPackage("..apps..")
            .and().resideOutsideOfPackage("..wiring..")
            .and().resideOutsideOfPackage("..playground..")
            .should().dependOnClassesThat(isDomainServiceClass)
            .check(classes)
    }

    "RFC-V4-010: shared는 비즈니스 로직 금지" {
        noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat()
            .resideInAPackage("..pkg.rawdata..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.changeset..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.slices..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.orchestration..")
            .check(classes)
    }

    "RFC-IMPL-009: shared에서 Koin 사용 금지" {
        noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.koin..")
            .check(classes)
    }

    "RFC-IMPL-009: shared에서 Ktor 사용 금지" {
        noClasses()
            .that().resideInAPackage("..shared..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.ktor..")
            .check(classes)
    }

    "RFC-IMPL-009: domain에서 Resilience4j 사용 금지" {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("io.github.resilience4j..")
            .check(classes)
    }

    // RFC-V4-010: orchestration → orchestration Workflow 호출 금지 (깊이 제한)
    // 복잡한 조건은 코드 리뷰로 검증. ArchUnit에서는 단일 조건만 지원.

    // RFC-V4-010: orchestration 네이밍 규칙 (*Workflow)
    // Kotlin 컴파일러가 생성하는 내부 클래스($Companion, $execute$1 등)가 많아
    // ArchUnit으로 정확히 필터링하기 어려움. 코드 리뷰로 검증.
    // 실제 외부 진입점 클래스: IngestWorkflow, SlicingWorkflow, QueryViewWorkflow, OutboxPollingWorker

    "RFC-V4-010: tooling은 런타임 도메인 호출 금지" {
        // tooling은 개발/테스트 도구이므로 런타임 도메인(pkg.*)을 직접 호출하면 안 됨
        // shared는 허용 (공통 타입/에러)
        noClasses()
            .that().resideInAPackage("..tooling..")
            .should().dependOnClassesThat()
            .resideInAPackage("..pkg.rawdata..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.changeset..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.slices..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.orchestration..")
            .check(classes)
    }

    // ============================================
    // SOLID 원칙 강화 규칙
    // ============================================
    
    "SOLID: Orchestration에서 Domain 서비스 클래스 직접 참조 금지" {
        // RFC-IMPL-010: Port 추상화 완료 → 강제 적용
        // Orchestration은 Port를 통해서만 도메인 로직에 접근해야 함
        // 예외: Record, VO, Contract 등 데이터 타입은 허용
        // 금지: *Engine, *Builder, *Calculator, *Service 등 서비스 클래스

        val isDomainServiceClass = object : DescribedPredicate<JavaClass>("is domain service class") {
            override fun test(input: JavaClass): Boolean {
                val name = input.simpleName
                val pkg = input.packageName

                // domain 패키지 내 서비스 클래스 패턴
                if (!pkg.contains(".domain")) return false

                // 서비스 클래스 패턴 (금지 대상)
                return name.endsWith("Engine") ||
                    name.endsWith("Builder") ||
                    name.endsWith("Calculator") ||
                    name.endsWith("Service") ||
                    name.endsWith("Executor") ||
                    name.endsWith("Handler") ||
                    name.endsWith("Processor")
            }
        }

        noClasses()
            .that().resideInAPackage("..orchestration.application..")
            .should().dependOnClassesThat(isDomainServiceClass)
            .check(classes)
    }
    
    "SOLID: adapters는 다른 도메인의 adapters를 직접 참조 금지" {
        // 각 도메인의 adapters는 독립적이어야 함
        val businessDomains = listOf("rawdata", "changeset", "slices", "sinks")
        
        businessDomains.forEach { domainA ->
            businessDomains.filter { it != domainA }.forEach { domainB ->
                noClasses()
                    .that().resideInAPackage("..pkg.$domainA.adapters..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..pkg.$domainB.adapters..")
                    .check(classes)
            }
        }
    }
    
    "SOLID: Port 인터페이스는 domain 패키지의 서비스 클래스를 참조하면 안 됨" {
        // Port는 순수 인터페이스여야 함 (구현 의존 금지)
        val isDomainServiceClass = object : DescribedPredicate<JavaClass>("is domain service class") {
            override fun test(input: JavaClass): Boolean {
                val name = input.simpleName
                val pkg = input.packageName
                
                if (!pkg.contains(".domain")) return false
                
                // 서비스 클래스 패턴 (Port가 참조하면 안 됨)
                return name.endsWith("Engine") ||
                    name.endsWith("Builder") ||
                    name.endsWith("Calculator") ||
                    name.endsWith("Service") ||
                    name.endsWith("Executor")
            }
        }
        
        noClasses()
            .that().resideInAPackage("..ports..")
            .and().haveSimpleNameEndingWith("Port")
            .should().dependOnClassesThat(isDomainServiceClass)
            .check(classes)
    }
    
    "SOLID: Record/VO 클래스는 외부 인프라에 의존하면 안 됨" {
        // RFC-IMPL-010: Port 추상화 완료 → 강제 적용
        // 도메인 모델은 순수해야 함 (인프라 의존 금지)
        val isRecord = object : DescribedPredicate<JavaClass>("is Record or VO") {
            override fun test(input: JavaClass): Boolean {
                val name = input.simpleName
                return name.endsWith("Record") ||
                    name.endsWith("Entry") ||
                    name.endsWith("VO")
            }
        }

        classes()
            .that(isRecord)
            .and().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                "java..",
                "kotlin..",
                "kotlinx..",
                "org.jetbrains..",  // Kotlin nullability annotations
                "com.oliveyoung.ivmlite.shared..",
                "com.oliveyoung.ivmlite.pkg..domain.."
            )
            .check(classes)
    }
})
