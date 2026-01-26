package com.oliveyoung.ivmlite.pkg.slices.domain

import com.oliveyoung.ivmlite.shared.domain.types.EntityKey
import com.oliveyoung.ivmlite.shared.domain.types.SliceType
import com.oliveyoung.ivmlite.shared.domain.types.TenantId
import com.oliveyoung.ivmlite.shared.domain.types.VersionLong

data class InvertedIndexEntry(
  val tenantId: TenantId,
  val refEntityKey: EntityKey,
  val refVersion: VersionLong,
  val targetEntityKey: EntityKey,
  val targetVersion: VersionLong,
  val indexType: String,
  val indexValue: String,
  val sliceType: SliceType,
  val sliceHash: String,
  val tombstone: Boolean,
)
