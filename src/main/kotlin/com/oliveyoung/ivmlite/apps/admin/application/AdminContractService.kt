package com.oliveyoung.ivmlite.apps.admin.application

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.oliveyoung.ivmlite.shared.domain.types.Result
import org.yaml.snakeyaml.Yaml
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Admin Contract Service
 *
 * Contract YAML 파일 로딩 및 조회 서비스.
 * 동적으로 contracts/v1 디렉토리를 스캔하여 하드코딩 제거.
 */
class AdminContractService {

    // ==================== Public API ====================

    /**
     * 전체 Contract 목록 조회
     */
    fun getAllContracts(): Result<List<ContractInfo>> {
        return try {
            Result.Ok(loadAllContractsInternal())
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to load contracts: ${e.message}"))
        }
    }

    /**
     * Kind별 Contract 목록 조회
     */
    fun getByKind(kind: ContractKind): Result<List<ContractInfo>> {
        return try {
            val contracts = loadAllContractsInternal().filter { it.kind == kind.yamlValue }
            Result.Ok(contracts)
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to load contracts by kind: ${e.message}"))
        }
    }

    /**
     * 특정 Contract 상세 조회
     */
    fun getById(kind: ContractKind, id: String): Result<ContractInfo> {
        return try {
            val contract = loadAllContractsInternal().find {
                it.kind.equals(kind.yamlValue, ignoreCase = true) && it.id == id
            }
            if (contract != null) {
                Result.Ok(contract)
            } else {
                Result.Err(DomainError.NotFoundError("Contract", "${kind.yamlValue}/$id"))
            }
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to load contract: ${e.message}"))
        }
    }

    /**
     * Contract 통계 조회
     */
    fun getStats(): Result<ContractStats> {
        return try {
            val contracts = loadAllContractsInternal()
            val byKind = contracts.groupBy { it.kind }.mapValues { it.value.size }
            val byStatus = contracts.groupBy { it.status }.mapValues { it.value.size }

            Result.Ok(
                ContractStats(
                    total = contracts.size,
                    byKind = byKind,
                    byStatus = byStatus
                )
            )
        } catch (e: Exception) {
            Result.Err(DomainError.StorageError("Failed to get contract stats: ${e.message}"))
        }
    }

    // ==================== Private Helpers ====================

    /**
     * 동적으로 contracts/v1 디렉토리 스캔
     *
     * JAR 내부와 개발 환경 모두 지원
     */
    private fun loadAllContractsInternal(): List<ContractInfo> {
        val contracts = mutableListOf<ContractInfo>()
        val yaml = Yaml()
        val resourcePath = "/contracts/v1"

        try {
            val resourceUrl = javaClass.getResource(resourcePath)
            if (resourceUrl == null) {
                return contracts
            }

            val uri = resourceUrl.toURI()

            if (uri.scheme == "jar") {
                // JAR 내부 (프로덕션)
                loadFromJar(uri, yaml, contracts)
            } else {
                // 파일 시스템 (개발 환경)
                loadFromFileSystem(uri, yaml, contracts)
            }
        } catch (e: Exception) {
            // 로딩 실패 시 빈 목록 반환 (fail-safe)
        }

        return contracts
    }

    private fun loadFromJar(uri: URI, yaml: Yaml, contracts: MutableList<ContractInfo>) {
        val jarUri = URI.create(uri.toString().substringBefore("!"))
        FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { fs ->
            val contractsPath = fs.getPath("/contracts/v1")
            if (Files.exists(contractsPath)) {
                Files.list(contractsPath)
                    .filter { it.toString().endsWith(".yaml") }
                    .forEach { path ->
                        try {
                            val content = Files.readString(path)
                            parseContract(yaml, content, path.fileName.toString())?.let {
                                contracts.add(it)
                            }
                        } catch (e: Exception) {
                            // Skip invalid files
                        }
                    }
            }
        }
    }

    private fun loadFromFileSystem(uri: URI, yaml: Yaml, contracts: MutableList<ContractInfo>) {
        val path = Paths.get(uri)
        if (Files.exists(path) && Files.isDirectory(path)) {
            Files.list(path)
                .filter { it.toString().endsWith(".yaml") }
                .forEach { filePath ->
                    try {
                        val content = Files.readString(filePath)
                        parseContract(yaml, content, filePath.fileName.toString())?.let {
                            contracts.add(it)
                        }
                    } catch (e: Exception) {
                        // Skip invalid files
                    }
                }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseContract(yaml: Yaml, content: String, fileName: String): ContractInfo? {
        val map = yaml.load<Map<String, Any?>>(content) as? Map<String, Any?> ?: return null

        val kind = map["kind"]?.toString() ?: return null
        val id = map["id"]?.toString() ?: return null
        val version = map["version"]?.toString() ?: "1.0.0"
        val status = map["status"]?.toString() ?: "ACTIVE"

        return ContractInfo(
            kind = kind,
            id = id,
            version = version,
            status = status,
            fileName = fileName,
            content = content,
            parsed = map
        )
    }
}

// ==================== Domain Models ====================
// ContractKind는 com.oliveyoung.ivmlite.pkg.contracts.domain.ContractKind 사용

data class ContractInfo(
    val kind: String,
    val id: String,
    val version: String,
    val status: String,
    val fileName: String,
    val content: String,
    val parsed: Map<String, Any?>
)

data class ContractStats(
    val total: Int,
    val byKind: Map<String, Int>,
    val byStatus: Map<String, Int>
)
