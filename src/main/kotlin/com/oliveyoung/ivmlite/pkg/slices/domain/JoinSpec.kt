package com.oliveyoung.ivmlite.pkg.slices.domain

data class JoinSpec(
  val name: String,
  val type: JoinType,
  val sourceFieldPath: String,
  val targetEntityType: String,
  val targetKeyPattern: String,
  val required: Boolean,
)

enum class JoinType { LOOKUP }
