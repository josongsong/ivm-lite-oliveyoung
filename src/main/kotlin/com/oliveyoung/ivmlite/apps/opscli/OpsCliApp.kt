package com.oliveyoung.ivmlite.apps.opscli

import com.oliveyoung.ivmlite.tooling.application.SeedContractsToDynamoDB
import com.oliveyoung.ivmlite.tooling.application.ValidateContracts
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlinx.coroutines.runBlocking
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import java.io.File
import java.net.URI
import kotlin.system.exitProcess

fun main(args: Array<String>) = IvmLiteCli().main(args)

private class IvmLiteCli : CliktCommand(name = "ivm-lite") {
    init {
        subcommands(
            ValidateContractsCmd(),
            SeedContractsToDynamoCmd(),
        )
    }
    override fun run() = Unit
}

private class ValidateContractsCmd : CliktCommand(name = "validate-contracts") {
    private val dirPath by argument(help = "contracts directory path")
    override fun run() {
        try {
            ValidateContracts.validateDir(File(dirPath))
            echo("OK")
        } catch (e: DomainError) {
            echo("ERROR: ${e.message}")
            exitProcess(2)
        }
    }
}

private class SeedContractsToDynamoCmd : CliktCommand(name = "seed-contracts-to-dynamo") {
    private val tableName by option("--table", help = "DynamoDB table name")
        .default(System.getenv("DYNAMODB_TABLE") ?: "")
    private val dirPath by option("--dir", help = "contracts directory path").default("src/main/resources/contracts/v1")
    private val endpoint by option("--endpoint", help = "DynamoDB endpoint URL (optional; default uses AWS endpoint)")
        .default(System.getenv("DYNAMODB_ENDPOINT") ?: "")
    private val dryRun by option("--dry-run", help = "Dry run mode (no changes)").flag()

    override fun run() {
        try {
            if (tableName.isBlank()) {
                echo("ERROR: DynamoDB table name is required. Set DYNAMODB_TABLE or pass --table.")
                exitProcess(1)
            }

            val builder = DynamoDbAsyncClient.builder()
                .region(Region.of(System.getenv("AWS_REGION") ?: "ap-northeast-2"))

            if (endpoint.isNotBlank()) {
                // Local/override mode (endpoint provided): dummy credentials are OK
                builder.endpointOverride(URI.create(endpoint))
                builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("dummy", "dummy")
                    )
                )
            } else {
                // Remote AWS mode: use default credentials chain (env, ~/.aws, IAM role, ...)
                builder.credentialsProvider(DefaultCredentialsProvider.create())
            }

            val dynamoClient = builder.build()

            val contractsDir = File(dirPath)
            if (!contractsDir.exists()) {
                echo("ERROR: Directory not found: $dirPath")
                exitProcess(1)
            }

            echo("📦 Seeding contracts to DynamoDB...")
            echo("   Table: $tableName")
            echo("   Directory: $dirPath")
            echo("   Endpoint: ${if (endpoint.isBlank()) "(AWS default)" else endpoint}")
            if (dryRun) {
                echo("   Mode: DRY RUN")
            }

            SeedContractsToDynamoDB.seed(
                dynamoClient = dynamoClient,
                tableName = tableName,
                contractsDir = contractsDir,
                dryRun = dryRun,
            )

            echo("✅ Done!")
        } catch (e: Exception) {
            echo("ERROR: ${e.message}")
            if (e.cause != null) {
                echo("   Cause: ${e.cause?.message}")
            }
            exitProcess(1)
        }
    }
}
