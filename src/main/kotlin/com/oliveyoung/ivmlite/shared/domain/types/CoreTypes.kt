package com.oliveyoung.ivmlite.shared.domain.types

@JvmInline value class TenantId(val value: String)
@JvmInline value class EntityKey(val value: String)
@JvmInline value class VersionLong(val value: Long)

data class ContractRef(val id: String, val version: SemVer)

data class SemVer(val major: Int, val minor: Int, val patch: Int) {
  override fun toString(): String = "$major.$minor.$patch"
  companion object {
    fun parse(s: String): SemVer {
      val parts = s.trim().split(".")
      require(parts.size == 3) { "Invalid SemVer: $s" }
      return SemVer(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }
  }
}
