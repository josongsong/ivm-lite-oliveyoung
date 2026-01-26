package com.oliveyoung.ivmlite.apps.opscli

import com.oliveyoung.ivmlite.tooling.application.ValidateContracts
import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) = IvmLiteCli().main(args)

private class IvmLiteCli : CliktCommand(name = "ivm-lite") {
    init {
        subcommands(ValidateContractsCmd())
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
