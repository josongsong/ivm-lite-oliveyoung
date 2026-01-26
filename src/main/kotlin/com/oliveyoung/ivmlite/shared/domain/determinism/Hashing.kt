package com.oliveyoung.ivmlite.shared.domain.determinism

import java.security.MessageDigest

object Hashing {
  fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun sha256Tagged(input: String): String = "sha256:${sha256Hex(input)}"
}
