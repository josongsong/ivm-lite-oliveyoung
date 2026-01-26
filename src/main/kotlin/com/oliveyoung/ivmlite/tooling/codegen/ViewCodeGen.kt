package com.oliveyoung.ivmlite.tooling.codegen

import com.oliveyoung.ivmlite.pkg.contracts.domain.ContractStatus
import com.oliveyoung.ivmlite.pkg.contracts.domain.ViewDefinitionContract
import com.oliveyoung.ivmlite.pkg.contracts.ports.ContractRegistryPort
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import kotlinx.coroutines.runBlocking
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * View Contract → Kotlin 코드 생성기
 * 
 * Contract YAML 파일 또는 DynamoDB에서 ViewRef 및 데이터 클래스를 자동 생성합니다.
 * 
 * @example 실행
 * ```bash
 * ./gradlew generateViews
 * # 또는
 * ViewCodeGen.generate(
 *     contractsDir = File("src/main/resources/contracts"),
 *     outputDir = File("build/generated/kotlin")
 * )
 * ```
 * 
 * @example 생성 결과
 * ```kotlin
 * // build/generated/kotlin/com/oliveyoung/ivmlite/sdk/schema/GeneratedViews.kt
 * object GeneratedViews {
 *     object Product {
 *         val pdp = ViewRef<JsonObject>(
 *             viewId = "product.pdp",
 *             slices = listOf("CORE", "PRICING"),
 *             description = "상품 상세 페이지 View"
 *         )
 *     }
 * }
 * ```
 */
object ViewCodeGen {
    private val yaml = Yaml()
    
    /**
     * Contract YAML에서 View 코드 생성
     * 
     * @param contractsDir Contract YAML 파일 디렉토리
     * @param outputDir 생성된 Kotlin 파일 출력 디렉토리
     * @param packageName 생성될 패키지명
     */
    fun generate(
        contractsDir: File,
        outputDir: File,
        packageName: String = "com.oliveyoung.ivmlite.sdk.schema.generated"
    ): GenerationResult {
        val viewContracts = scanViewContracts(contractsDir)
        if (viewContracts.isEmpty()) {
            return GenerationResult(
                success = false,
                message = "No VIEW_DEFINITION contracts found in ${contractsDir.path}",
                generatedFiles = emptyList()
            )
        }
        
        return generateFromInfoList(viewContracts, outputDir, packageName, "YAML files")
    }
    
    /**
     * DynamoDB Contract Registry에서 View 코드 생성
     * 
     * @param registry DynamoDB ContractRegistryPort
     * @param outputDir 생성된 Kotlin 파일 출력 디렉토리
     * @param packageName 생성될 패키지명
     */
    fun generateFromDynamoDB(
        registry: ContractRegistryPort,
        outputDir: File,
        packageName: String = "com.oliveyoung.ivmlite.sdk.schema.generated"
    ): GenerationResult = runBlocking {
        val result = registry.listViewDefinitions(ContractStatus.ACTIVE)
        
        when (result) {
            is ContractRegistryPort.Result.Err -> {
                GenerationResult(
                    success = false,
                    message = "Failed to load ViewDefinitions from DynamoDB: ${result.error}",
                    generatedFiles = emptyList()
                )
            }
            is ContractRegistryPort.Result.Ok -> {
                val contracts = result.value
                if (contracts.isEmpty()) {
                    return@runBlocking GenerationResult(
                        success = false,
                        message = "No ACTIVE VIEW_DEFINITION contracts found in DynamoDB",
                        generatedFiles = emptyList()
                    )
                }
                
                val viewInfos = contracts.map { contract ->
                    viewDefinitionToInfo(contract)
                }
                
                generateFromInfoList(viewInfos, outputDir, packageName, "DynamoDB")
            }
        }
    }
    
    /**
     * ViewDefinitionContract → ViewContractInfo 변환
     */
    private fun viewDefinitionToInfo(contract: ViewDefinitionContract): ViewContractInfo {
        // id에서 domain과 viewName 추출: "view.product.pdp.v1" → domain=product, viewName=pdp
        val parts = contract.meta.id.removePrefix("view.").split(".")
        val domain = parts.getOrNull(0) ?: "default"
        val viewName = parts.getOrNull(1) ?: contract.meta.id
        
        return ViewContractInfo(
            id = contract.meta.id,
            domain = domain,
            viewName = viewName,
            version = contract.meta.version.toString(),
            status = contract.meta.status.name,
            requiredSlices = contract.requiredSlices.map { it.name },
            optionalSlices = contract.optionalSlices.map { it.name },
            sourceFile = "DynamoDB:${contract.meta.id}"
        )
    }
    
    /**
     * 공통 생성 로직
     */
    private fun generateFromInfoList(
        viewContracts: List<ViewContractInfo>,
        outputDir: File,
        packageName: String,
        source: String
    ): GenerationResult {
        val generatedFiles = mutableListOf<File>()
        
        // 1. GeneratedViews.kt 생성
        val viewsCode = generateViewsObject(viewContracts, packageName)
        val viewsFile = File(outputDir, packageName.replace('.', '/') + "/GeneratedViews.kt")
        viewsFile.parentFile.mkdirs()
        viewsFile.writeText(viewsCode)
        generatedFiles.add(viewsFile)
        
        // 2. 각 도메인별 데이터 클래스 생성 (선택)
        val groupedByDomain = viewContracts.groupBy { it.domain }
        groupedByDomain.forEach { (domain, contracts) ->
            val dataClassesCode = generateDataClasses(domain, contracts, packageName)
            val dataFile = File(outputDir, packageName.replace('.', '/') + "/${domain.capitalize()}Data.kt")
            dataFile.writeText(dataClassesCode)
            generatedFiles.add(dataFile)
        }
        
        return GenerationResult(
            success = true,
            message = "Generated ${generatedFiles.size} files from ${viewContracts.size} contracts ($source)",
            generatedFiles = generatedFiles
        )
    }
    
    /**
     * Contract 디렉토리 스캔하여 VIEW_DEFINITION 계약 추출
     */
    private fun scanViewContracts(dir: File): List<ViewContractInfo> {
        if (!dir.exists()) return emptyList()
        
        return dir.walkTopDown()
            .filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }
            .mapNotNull { file -> parseViewContract(file) }
            .toList()
    }
    
    /**
     * 단일 YAML 파일 파싱
     */
    private fun parseViewContract(file: File): ViewContractInfo? {
        val content = try {
            yaml.load<Map<String, Any>>(file.readText())
        } catch (e: Exception) {
            throw DomainError.ContractError("Failed to parse YAML file ${file.path}: ${e.message}")
        }
        
        val kind = content["kind"] as? String
        if (kind == null || kind != "VIEW_DEFINITION") {
            return null  // VIEW_DEFINITION이 아닌 파일은 무시 (다른 계약 타입)
        }
        
        val id = content["id"] as? String
            ?: throw DomainError.ContractError("Missing 'id' field in ${file.path}")
        
        val version = content["version"]?.toString() ?: "1.0.0"  // 기본값 허용 (코드 생성 도구)
        val status = content["status"] as? String ?: "ACTIVE"  // 기본값 허용 (코드 생성 도구)
        
        @Suppress("UNCHECKED_CAST")
        val requiredSlices = (content["requiredSlices"] as? List<String>)
            ?: throw DomainError.ContractError("Missing required field 'requiredSlices' in ${file.path}")
        @Suppress("UNCHECKED_CAST")
        val optionalSlices = (content["optionalSlices"] as? List<String>) ?: emptyList()  // optionalSlices는 빈 리스트 허용
        
        // id에서 domain과 viewName 추출: "view.product.pdp.v1" → domain=product, viewName=pdp
        val parts = id.removePrefix("view.").split(".")
        val domain = parts.getOrNull(0) ?: "default"
        val viewName = parts.getOrNull(1) ?: id
        
        return ViewContractInfo(
            id = id,
            domain = domain,
            viewName = viewName,
            version = version,
            status = status,
            requiredSlices = requiredSlices,
            optionalSlices = optionalSlices,
            sourceFile = file.path
        )
    }
    
    /**
     * GeneratedViews.kt 파일 내용 생성
     */
    private fun generateViewsObject(contracts: List<ViewContractInfo>, packageName: String): String {
        val groupedByDomain = contracts.groupBy { it.domain }
        
        return buildString {
            appendLine("// AUTO-GENERATED FILE - DO NOT EDIT")
            appendLine("// Generated by ViewCodeGen from Contract YAML files")
            appendLine("// Regenerate with: ./gradlew generateViews")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import com.oliveyoung.ivmlite.sdk.schema.ViewRef")
            appendLine("import kotlinx.serialization.json.JsonObject")
            appendLine()
            appendLine("/**")
            appendLine(" * Auto-generated View references from Contract YAML")
            appendLine(" * ")
            appendLine(" * @example")
            appendLine(" * ```kotlin")
            appendLine(" * // 타입 세이프 조회")
            appendLine(" * val view = Ivm.query(GeneratedViews.Product.pdp)")
            appendLine(" *     .key(\"SKU-001\")")
            appendLine(" *     .get()")
            appendLine(" * ```")
            appendLine(" */")
            appendLine("object GeneratedViews {")
            appendLine()
            
            groupedByDomain.forEach { (domain, domainContracts) ->
                appendLine("    object ${domain.capitalize()} {")
                
                domainContracts.forEach { contract ->
                    val slicesLiteral = contract.allSlices.joinToString(", ") { "\"$it\"" }
                    appendLine("        /**")
                    appendLine("         * ${contract.viewName.capitalize()} View")
                    appendLine("         * ")
                    appendLine("         * - ID: ${contract.id}")
                    appendLine("         * - Version: ${contract.version}")
                    appendLine("         * - Slices: ${contract.allSlices.joinToString(", ")}")
                    appendLine("         * - Source: ${contract.sourceFile}")
                    appendLine("         */")
                    appendLine("        val ${contract.viewName} = ViewRef<JsonObject>(")
                    appendLine("            viewId = \"${contract.domain}.${contract.viewName}\",")
                    appendLine("            slices = listOf($slicesLiteral),")
                    appendLine("            description = \"${contract.viewName.capitalize()} View (auto-generated)\"")
                    appendLine("        )")
                    appendLine()
                }
                
                appendLine("    }")
                appendLine()
            }
            
            // all views list
            appendLine("    /** All generated view references */")
            appendLine("    val all: List<ViewRef<*>> = listOf(")
            contracts.forEachIndexed { index, contract ->
                val comma = if (index < contracts.size - 1) "," else ""
                appendLine("        ${contract.domain.capitalize()}.${contract.viewName}$comma")
            }
            appendLine("    )")
            appendLine()
            
            // find function
            appendLine("    /** Find view by ID */")
            appendLine("    fun find(viewId: String): ViewRef<*>? = all.find { it.viewId == viewId }")
            appendLine("}")
        }
    }
    
    /**
     * 도메인별 데이터 클래스 생성
     */
    private fun generateDataClasses(domain: String, contracts: List<ViewContractInfo>, packageName: String): String {
        return buildString {
            appendLine("// AUTO-GENERATED FILE - DO NOT EDIT")
            appendLine("// Generated by ViewCodeGen")
            appendLine()
            appendLine("package $packageName")
            appendLine()
            appendLine("import kotlinx.serialization.Serializable")
            appendLine("import kotlinx.serialization.json.*")
            appendLine()
            
            contracts.forEach { contract ->
                val className = "${domain.capitalize()}${contract.viewName.capitalize()}Data"
                
                appendLine("/**")
                appendLine(" * Data class for ${contract.viewName} view")
                appendLine(" * ")
                appendLine(" * Slices: ${contract.allSlices.joinToString(", ")}")
                appendLine(" * ")
                appendLine(" * @example")
                appendLine(" * ```kotlin")
                appendLine(" * val data = ${className}.fromJson(viewResult.data)")
                appendLine(" * ```")
                appendLine(" */")
                appendLine("@Serializable")
                appendLine("data class $className(")
                appendLine("    val entityKey: String,")
                
                // 각 슬라이스에 대한 필드 생성
                contract.allSlices.forEachIndexed { index, slice ->
                    val fieldName = slice.lowercase()
                    val comma = if (index < contract.allSlices.size - 1) "," else ""
                    appendLine("    val $fieldName: JsonObject? = null$comma")
                }
                
                appendLine(") {")
                appendLine("    companion object {")
                appendLine("        fun fromJson(json: JsonObject): $className {")
                appendLine("            return $className(")
                appendLine("                entityKey = json[\"entityKey\"]?.jsonPrimitive?.content ?: \"\",")
                
                contract.allSlices.forEachIndexed { index, slice ->
                    val fieldName = slice.lowercase()
                    val comma = if (index < contract.allSlices.size - 1) "," else ""
                    appendLine("                $fieldName = json[\"$fieldName\"]?.jsonObject$comma")
                }
                
                appendLine("            )")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
                appendLine()
            }
        }
    }
    
    private fun String.capitalize(): String = 
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/**
 * View Contract 정보
 */
data class ViewContractInfo(
    val id: String,
    val domain: String,
    val viewName: String,
    val version: String,
    val status: String,
    val requiredSlices: List<String>,
    val optionalSlices: List<String>,
    val sourceFile: String
) {
    val allSlices: List<String> get() = requiredSlices + optionalSlices
}

/**
 * 코드 생성 결과
 */
data class GenerationResult(
    val success: Boolean,
    val message: String,
    val generatedFiles: List<File>
)

// ===== CLI Entry Point =====

/**
 * CLI로 실행 가능
 * 
 * ```bash
 * java -cp app.jar com.oliveyoung.ivmlite.tooling.codegen.ViewCodeGenKt \
 *   --contracts src/main/resources/contracts \
 *   --output build/generated/kotlin
 * ```
 */
fun main(args: Array<String>) {
    val contractsDir = args.findArg("--contracts") ?: "src/main/resources/contracts"
    val outputDir = args.findArg("--output") ?: "build/generated/kotlin"
    val packageName = args.findArg("--package") ?: "com.oliveyoung.ivmlite.sdk.schema.generated"
    
    println("ViewCodeGen - Contract → Kotlin Code Generator")
    println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    println("Contracts: $contractsDir")
    println("Output:    $outputDir")
    println("Package:   $packageName")
    println()
    
    val result = ViewCodeGen.generate(
        contractsDir = File(contractsDir),
        outputDir = File(outputDir),
        packageName = packageName
    )
    
    if (result.success) {
        println("✅ ${result.message}")
        result.generatedFiles.forEach { println("   → ${it.path}") }
    } else {
        println("❌ ${result.message}")
        System.exit(1)
    }
}

private fun Array<String>.findArg(name: String): String? {
    val index = indexOf(name)
    return if (index >= 0 && index < size - 1) get(index + 1) else null
}
