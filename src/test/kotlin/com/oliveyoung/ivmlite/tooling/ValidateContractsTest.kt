package com.oliveyoung.ivmlite.tooling

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.tooling.application.ValidateContracts
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import java.io.File
import java.nio.file.Files

/**
 * ValidateContracts SOTA 테스트
 *
 * 검증 항목:
 * 1. 디렉토리 존재/유효성
 * 2. YAML 파일 존재 여부
 * 3. YAML 구문 유효성
 * 4. 필수 키(kind, id, version) 존재
 * 5. 필수 키 값 유효성 (비어있지 않음)
 */
class ValidateContractsTest : StringSpec({

    "실제 contracts 디렉토리 검증 - 성공" {
        val contractsDir = File("src/main/resources/contracts/v1")

        shouldNotThrowAny {
            ValidateContracts.validateDir(contractsDir)
        }
    }

    "존재하지 않는 디렉토리 → ContractError" {
        val nonExistent = File("non/existent/path")

        val error = shouldThrow<DomainError.ContractError> {
            ValidateContracts.validateDir(nonExistent)
        }
        error.msg shouldContain "contracts dir not found"
    }

    "파일 경로(디렉토리가 아님) → ContractError" {
        val tempFile = Files.createTempFile("test", ".txt").toFile()
        try {
            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempFile)
            }
            error.msg shouldContain "contracts dir not found"
        } finally {
            tempFile.delete()
        }
    }

    "빈 디렉토리 (YAML 없음) → ContractError" {
        val tempDir = Files.createTempDirectory("empty-contracts").toFile()
        try {
            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "no yaml files"
        } finally {
            tempDir.delete()
        }
    }

    "잘못된 YAML 구문 → ContractError" {
        val tempDir = Files.createTempDirectory("invalid-yaml").toFile()
        val invalidYaml = File(tempDir, "invalid.yaml")
        try {
            invalidYaml.writeText("""
                kind: TEST
                id: test-1
                version: 1.0.0
                data: [unclosed bracket
            """.trimIndent())

            val error = shouldThrow<Exception> {
                ValidateContracts.validateDir(tempDir)
            }
            // YAML 파싱 에러 발생
            error.message shouldContain "unclosed"
        } finally {
            invalidYaml.delete()
            tempDir.delete()
        }
    }

    "root가 map이 아닌 YAML → ContractError" {
        val tempDir = Files.createTempDirectory("list-yaml").toFile()
        val listYaml = File(tempDir, "list.yaml")
        try {
            listYaml.writeText("""
                - item1
                - item2
            """.trimIndent())

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "yaml root must be map"
        } finally {
            listYaml.delete()
            tempDir.delete()
        }
    }

    "kind 키 누락 → ContractError" {
        val tempDir = Files.createTempDirectory("missing-kind").toFile()
        val yaml = File(tempDir, "missing-kind.yaml")
        try {
            yaml.writeText("""
                id: test-1
                version: 1.0.0
            """.trimIndent())

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "missing 'kind'"
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    "id 키 누락 → ContractError" {
        val tempDir = Files.createTempDirectory("missing-id").toFile()
        val yaml = File(tempDir, "missing-id.yaml")
        try {
            yaml.writeText("""
                kind: TEST
                version: 1.0.0
            """.trimIndent())

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "missing 'id'"
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    "version 키 누락 → ContractError" {
        val tempDir = Files.createTempDirectory("missing-version").toFile()
        val yaml = File(tempDir, "missing-version.yaml")
        try {
            yaml.writeText("""
                kind: TEST
                id: test-1
            """.trimIndent())

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "missing 'version'"
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    "kind가 빈 문자열 → ContractError" {
        val tempDir = Files.createTempDirectory("blank-kind").toFile()
        val yaml = File(tempDir, "blank-kind.yaml")
        try {
            yaml.writeText("""
                kind: ""
                id: test-1
                version: 1.0.0
            """.trimIndent())

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "invalid kind/id"
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    "id가 빈 문자열 → ContractError" {
        val tempDir = Files.createTempDirectory("blank-id").toFile()
        val yaml = File(tempDir, "blank-id.yaml")
        try {
            yaml.writeText("""
                kind: TEST
                id: "   "
                version: 1.0.0
            """.trimIndent())

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "invalid kind/id"
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    "유효한 contract 파일 - 모든 필수 키 존재" {
        val tempDir = Files.createTempDirectory("valid-contract").toFile()
        val yaml = File(tempDir, "valid.yaml")
        try {
            yaml.writeText("""
                kind: CHANGESET
                id: changeset.v1
                version: 1.0.0
                data:
                  identity:
                    entityKeyFormat: "{ENTITY_TYPE}#{tenantId}#{entityId}"
            """.trimIndent())

            shouldNotThrowAny {
                ValidateContracts.validateDir(tempDir)
            }
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    "하위 디렉토리의 YAML도 검증" {
        val tempDir = Files.createTempDirectory("nested-contracts").toFile()
        val subDir = File(tempDir, "sub")
        subDir.mkdirs()
        val yaml = File(subDir, "nested.yaml")
        try {
            yaml.writeText("""
                kind: JOIN_SPEC
                id: join-spec.v1
                version: 1.0.0
            """.trimIndent())

            shouldNotThrowAny {
                ValidateContracts.validateDir(tempDir)
            }
        } finally {
            yaml.delete()
            subDir.delete()
            tempDir.delete()
        }
    }

    "빈 YAML 파일 → ContractError" {
        val tempDir = Files.createTempDirectory("empty-yaml").toFile()
        val yaml = File(tempDir, "empty.yaml")
        try {
            yaml.writeText("")

            val error = shouldThrow<DomainError.ContractError> {
                ValidateContracts.validateDir(tempDir)
            }
            error.msg shouldContain "empty yaml"
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }

    ".yml 확장자도 처리" {
        val tempDir = Files.createTempDirectory("yml-extension").toFile()
        val yaml = File(tempDir, "contract.yml")
        try {
            yaml.writeText("""
                kind: INVERTED_INDEX
                id: inverted-index.v1
                version: 2.0.0
            """.trimIndent())

            shouldNotThrowAny {
                ValidateContracts.validateDir(tempDir)
            }
        } finally {
            yaml.delete()
            tempDir.delete()
        }
    }
})
