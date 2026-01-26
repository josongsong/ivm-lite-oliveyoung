package com.oliveyoung.ivmlite.tooling.application

import com.oliveyoung.ivmlite.shared.domain.errors.DomainError
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Minimal contract validator for DX.
 * Validates YAML parse + required top-level keys.
 */
object ValidateContracts {
  private val yaml = Yaml()

  fun validateDir(dir: File) {
    if (!dir.exists() || !dir.isDirectory) {
      throw DomainError.ContractError("contracts dir not found: ${dir.path}")
    }
    val files = dir.walkTopDown().filter { it.isFile && (it.extension == "yaml" || it.extension == "yml") }.toList()
    if (files.isEmpty()) throw DomainError.ContractError("no yaml files under: ${dir.path}")

    files.forEach { f ->
      val obj = yaml.load<Any>(f.readText(Charsets.UTF_8)) ?: throw DomainError.ContractError("empty yaml: ${f.path}")
      val m = obj as? Map<*, *> ?: throw DomainError.ContractError("yaml root must be map: ${f.path}")
      val kind = m["kind"] as? String ?: throw DomainError.ContractError("missing 'kind': ${f.path}")
      val id = m["id"] as? String ?: throw DomainError.ContractError("missing 'id': ${f.path}")
      val version = m["version"] as? Any ?: throw DomainError.ContractError("missing 'version': ${f.path}")
      if (kind.isBlank() || id.isBlank()) throw DomainError.ContractError("invalid kind/id: ${f.path}")
      if (version.toString().isBlank()) throw DomainError.ContractError("invalid version: ${f.path}")
    }
  }
}
