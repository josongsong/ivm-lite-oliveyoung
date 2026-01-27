package com.oliveyoung.ivmlite.shared.domain.types

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

class VersionGeneratorTest : StringSpec({

    "generate() 호출 시 고유한 version 반환" {
        val v1 = VersionGenerator.generate()
        val v2 = VersionGenerator.generate()
        val v3 = VersionGenerator.generate()

        v1 shouldNotBe v2
        v2 shouldNotBe v3
        v1 shouldNotBe v3
    }

    "generate() 호출 시 단조 증가 (시간순 정렬 가능)" {
        val v1 = VersionGenerator.generate()
        val v2 = VersionGenerator.generate()
        val v3 = VersionGenerator.generate()

        v2 shouldBeGreaterThan v1
        v3 shouldBeGreaterThan v2
    }

    "동시에 1000개 요청 시 모두 고유한 version 생성" {
        val versions = ConcurrentHashMap.newKeySet<Long>()
        val count = 1000

        runBlocking(Dispatchers.Default) {
            (1..count).map {
                async {
                    val v = VersionGenerator.generate()
                    versions.add(v)
                }
            }.awaitAll()
        }

        versions.size shouldBe count
    }

    "동시에 10000개 요청 시 모두 고유한 version 생성" {
        val versions = ConcurrentHashMap.newKeySet<Long>()
        val count = 10000

        runBlocking(Dispatchers.Default) {
            (1..count).map {
                async {
                    val v = VersionGenerator.generate()
                    versions.add(v)
                }
            }.awaitAll()
        }

        versions.size shouldBe count
    }

    "동시에 100000개 요청 시 모두 고유한 version 생성 (SOTA 성능 테스트)" {
        val versions = ConcurrentHashMap.newKeySet<Long>()
        val count = 100000

        runBlocking(Dispatchers.Default) {
            (1..count).map {
                async {
                    val v = VersionGenerator.generate()
                    versions.add(v)
                }
            }.awaitAll()
        }

        versions.size shouldBe count
    }

    "toTsidString() 변환 테스트" {
        val version = VersionGenerator.generate()
        val tsidString = VersionGenerator.toTsidString(version)
        
        // TSID는 13자 문자열
        tsidString.length shouldBe 13
    }

    "extractTimestamp() 테스트" {
        val before = System.currentTimeMillis()
        val version = VersionGenerator.generate()
        val after = System.currentTimeMillis()
        
        val extracted = VersionGenerator.extractTimestamp(version)
        
        extracted shouldBeGreaterThan (before - 1)
        (after + 1) shouldBeGreaterThan extracted
    }

    "toReadable() 테스트" {
        val version = VersionGenerator.generate()
        val readable = VersionGenerator.toReadable(version)
        
        readable shouldContain "node="
        readable shouldContain "seq="
    }

    "extractNodeId() 범위 테스트 (0-1023)" {
        val version = VersionGenerator.generate()
        val nodeId = VersionGenerator.extractNodeId(version)
        
        (nodeId >= 0) shouldBe true
        (nodeId <= 1023) shouldBe true
    }

    "extractCounter() 범위 테스트 (0-4095)" {
        val version = VersionGenerator.generate()
        val counter = VersionGenerator.extractCounter(version)
        
        (counter >= 0) shouldBe true
        (counter <= 4095) shouldBe true
    }
})
