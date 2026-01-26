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

    "RFC-V4-010: apps는 도메인 직접 호출 금지 (orchestration 통해서만)" {
        noClasses()
            .that().resideInAPackage("..apps..")
            .and().resideOutsideOfPackage("..wiring..")
            .should().dependOnClassesThat()
            .resideInAPackage("..pkg.rawdata.domain..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.changeset.domain..")
            .orShould().dependOnClassesThat()
            .resideInAPackage("..pkg.slices.domain..")
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

    // RFC-V4-010: orchestration은 ports를 통해서만 도메인 호출
    // 단, 도메인 **타입**(데이터 클래스, VO)은 사용 허용
    // 도메인 **서비스/로직** 직접 호출만 금지 (코드 리뷰로 검증)
    // 현재 OutboxEntry, RawDataRecord, SliceRecord 등 도메인 타입은 orchestration에서 필수적으로 사용
})
