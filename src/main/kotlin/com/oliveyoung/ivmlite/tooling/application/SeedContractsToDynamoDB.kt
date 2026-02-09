package com.oliveyoung.ivmlite.tooling.application

import com.oliveyoung.ivmlite.pkg.contracts.adapters.DynamoDBContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.adapters.LocalYamlContractRegistryAdapter
import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractRef
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.types.Result
import com.oliveyoung.ivmlite.shared.domain.types.SemVer
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.io.File

/**
 * YAML 계약 파일을 DynamoDB에 업로드하는 Seed 스크립트
 * 
 * Flyway 스타일로 계약을 DynamoDB에 자동 업로드합니다.
 * 
 * 사용법:
 * ```bash
 * # Remote-only: DYNAMODB_TABLE 환경 변수를 설정한 뒤 실행
 * ./scripts/seed-contracts.sh
 * ```
 */
object SeedContractsToDynamoDB {
    private val log = LoggerFactory.getLogger(SeedContractsToDynamoDB::class.java)

    /**
     * YAML 계약 디렉토리에서 모든 계약을 DynamoDB에 업로드
     * 
     * @param dynamoClient DynamoDB 클라이언트
     * @param tableName DynamoDB 테이블명
     * @param contractsDir YAML 계약 파일이 있는 디렉토리
     * @param dryRun true면 실제 업로드 없이 검증만 수행
     */
    fun seed(
        dynamoClient: DynamoDbAsyncClient,
        tableName: String,
        contractsDir: File,
        dryRun: Boolean = false,
    ) {
        if (!contractsDir.exists() || !contractsDir.isDirectory) {
            throw IllegalArgumentException("Contracts directory not found: ${contractsDir.path}")
        }

        val yamlFiles = contractsDir.listFiles { _, name ->
            name.endsWith(".yaml") || name.endsWith(".yml")
        } ?: emptyArray()

        if (yamlFiles.isEmpty()) {
            log.warn("No YAML files found in ${contractsDir.path}")
            return
        }

        log.info("📦 Found ${yamlFiles.size} contract files in ${contractsDir.path}")
        if (dryRun) {
            log.info("🔍 DRY RUN mode - no changes will be made")
        }

        val adapter = DynamoDBContractRegistryAdapter(dynamoClient, tableName)
        
        // YAML 파일을 읽기 위해 리소스 경로 사용
        // 실제 파일 경로를 리소스 경로로 변환
        val resourcePath = contractsDir.path.replace(File("src/main/resources").absolutePath, "")
            .trimStart('/').replace('\\', '/')
        val yamlAdapter = LocalYamlContractRegistryAdapter("/$resourcePath")

        var successCount = 0
        var skipCount = 0
        var errorCount = 0

        runBlocking {
            yamlFiles.forEach { file ->
                try {
                    val contractName = file.nameWithoutExtension
                    log.info("📄 Processing: $contractName")

                    // YAML 파일에서 계약 정보 추출 (kind, id, version)
                    val yamlContent = file.readText()
                    val kind = extractKind(yamlContent, file.name)
                    val id = extractId(yamlContent, file.name)
                    val version = extractVersion(yamlContent, file.name)

                    if (kind == null || id == null || version == null) {
                        log.error("❌ Missing required fields (kind/id/version) in ${file.name}")
                        errorCount++
                        return@forEach
                    }

                    val ref = ContractRef(id, SemVer.parse(version))

                    // DynamoDB에 이미 존재하는지 확인
                    val dynamoResult = when (kind.uppercase()) {
                        "RULESET" -> adapter.loadRuleSetContract(ref)
                        "CHANGESET", "CHANGESETCONTRACT" -> adapter.loadChangeSetContract(ref)
                        "JOIN_SPEC", "JOINSPECCONTRACT" -> adapter.loadJoinSpecContract(ref)
                        "VIEW_DEFINITION", "VIEWDEFINITIONCONTRACT" -> adapter.loadViewDefinitionContract(ref)
                        else -> {
                            log.warn("⚠️  Unknown contract kind: $kind in ${file.name}, skipping")
                            skipCount++
                            return@forEach
                        }
                    }

                    @Suppress("UNCHECKED_CAST")
                    val typedDynamoResult = dynamoResult as Result<Any>
                    if (typedDynamoResult.isOk) {
                        log.info("⏭️  Contract already exists: $kind#$id@$version (skipping)")
                        skipCount++
                        return@forEach
                    }

                    // YAML에서 Contract 객체 로드
                    val contractResult: Result<Any> = when (kind.uppercase()) {
                        "RULESET" -> yamlAdapter.loadRuleSetContract(ref) as Result<Any>
                        "CHANGESET", "CHANGESETCONTRACT" -> yamlAdapter.loadChangeSetContract(ref) as Result<Any>
                        "JOIN_SPEC", "JOINSPECCONTRACT" -> yamlAdapter.loadJoinSpecContract(ref) as Result<Any>
                        "VIEW_DEFINITION", "VIEWDEFINITIONCONTRACT" -> yamlAdapter.loadViewDefinitionContract(ref) as Result<Any>
                        else -> {
                            log.warn("⚠️  Unknown contract kind: $kind in ${file.name}, skipping")
                            skipCount++
                            return@forEach
                        }
                    }

                    val contract = contractResult.getOrNull()
                    if (contract == null) {
                        log.error("❌ Failed to parse contract from YAML: ${contractResult.errorOrNull()}")
                        errorCount++
                        return@forEach
                    }

                    // DynamoDB에 업로드
                    if (dryRun) {
                        log.info("🔍 [DRY RUN] Would upload: $kind#$id@$version")
                        successCount++
                    } else {
                        val saveResult: Result<Unit> = when (kind.uppercase()) {
                            "RULESET" -> adapter.saveRuleSetContract(contract as com.oliveyoung.ivmlite.pkg.contracts.domain.RuleSetContract)
                            "CHANGESET", "CHANGESETCONTRACT" -> {
                                val csContract = contract as com.oliveyoung.ivmlite.pkg.contracts.domain.ChangeSetContract
                                adapter.saveChangeSetContract(csContract)
                            }
                            "JOIN_SPEC", "JOINSPECCONTRACT" -> {
                                val jsContract = contract as com.oliveyoung.ivmlite.pkg.contracts.domain.JoinSpecContract
                                adapter.saveJoinSpecContract(jsContract)
                            }
                            "VIEW_DEFINITION", "VIEWDEFINITIONCONTRACT" -> adapter.saveViewDefinitionContract(contract as com.oliveyoung.ivmlite.pkg.contracts.domain.ViewDefinitionContract)
                            else -> {
                                log.error("❌ Unsupported contract kind for save: $kind")
                                errorCount++
                                return@forEach
                            }
                        }

                        if (saveResult.isOk) {
                            log.info("✅ Uploaded: $kind#$id@$version")
                            successCount++
                        } else {
                            log.error("❌ Failed to upload $kind#$id@$version: ${saveResult.errorOrNull()}")
                            errorCount++
                        }
                    }
                } catch (e: Exception) {
                    log.error("❌ Error processing ${file.name}: ${e.message}", e)
                    errorCount++
                }
            }
        }

        log.info("")
        log.info("📊 Summary:")
        log.info("   ✅ Uploaded: $successCount")
        log.info("   ⏭️  Skipped: $skipCount")
        log.info("   ❌ Errors: $errorCount")
        log.info("")

        if (errorCount > 0) {
            throw RuntimeException("Failed to upload $errorCount contract(s)")
        }
    }

    private fun extractKind(yamlContent: String, filename: String): String? {
        return yamlContent.lines()
            .firstOrNull { it.trim().startsWith("kind:") }
            ?.substringAfter("kind:")
            ?.trim()
            ?.removeSurrounding("\"", "'")
    }

    private fun extractId(yamlContent: String, filename: String): String? {
        return yamlContent.lines()
            .firstOrNull { it.trim().startsWith("id:") }
            ?.substringAfter("id:")
            ?.trim()
            ?.removeSurrounding("\"", "'")
    }

    private fun extractVersion(yamlContent: String, filename: String): String? {
        return yamlContent.lines()
            .firstOrNull { it.trim().startsWith("version:") }
            ?.substringAfter("version:")
            ?.trim()
            ?.removeSurrounding("\"", "'")
    }
}
