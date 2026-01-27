package com.oliveyoung.ivmlite.shared.domain.types

import com.github.f4b6a3.tsid.TsidCreator

/**
 * Version Generator (SSOT) - TSID 기반
 * 
 * Twitter Snowflake 유사한 Time-Sorted ID 생성
 * - 64-bit Long
 * - 시간순 정렬 가능 (monotonic increasing)
 * - 분산 환경에서 충돌 없음
 * - Lock-free (고성능)
 * 
 * TSID 구조 (64-bit):
 * | 42-bit timestamp | 10-bit node | 12-bit counter |
 * - timestamp: 밀리초 단위 (약 139년)
 * - node: 1024개 노드 구분 가능
 * - counter: 밀리초당 4096개 생성 가능
 * 
 * 성능:
 * - 단일 노드: 초당 약 400만 개 생성 가능
 * - 분산 환경: 1024 노드 × 400만 = 약 40억개/초
 * 
 * @see <a href="https://github.com/f4b6a3/tsid-creator">TSID Creator</a>
 */
object VersionGenerator {
    
    /**
     * 충돌 없는 고유 version 생성
     * 
     * Thread-safe, Lock-free
     * 분산 환경에서도 고유성 보장 (node ID 자동 할당)
     */
    fun generate(): Long = TsidCreator.getTsid().toLong()
    
    /**
     * version을 TSID 문자열로 변환
     * 
     * 예시: 1738000000000000001 → "0HJKXYZ123AB"
     */
    fun toTsidString(version: Long): String = 
        com.github.f4b6a3.tsid.Tsid.from(version).toString()
    
    /**
     * version에서 생성 시각 추출 (밀리초)
     */
    fun extractTimestamp(version: Long): Long = 
        com.github.f4b6a3.tsid.Tsid.from(version).instant.toEpochMilli()
    
    /**
     * version에서 노드 ID 추출
     */
    fun extractNodeId(version: Long): Int {
        val tsid = com.github.f4b6a3.tsid.Tsid.from(version)
        // TSID의 random 부분에서 상위 10bit가 node ID
        return ((tsid.toLong() shr 12) and 0x3FF).toInt()
    }
    
    /**
     * version에서 시퀀스(카운터) 추출
     */
    fun extractCounter(version: Long): Int {
        val tsid = com.github.f4b6a3.tsid.Tsid.from(version)
        // TSID의 하위 12bit가 counter
        return (tsid.toLong() and 0xFFF).toInt()
    }
    
    /**
     * version을 사람이 읽기 쉬운 형태로 변환
     * 
     * 예시: "2024-01-27T12:34:56.789Z (node=42, seq=123)"
     */
    fun toReadable(version: Long): String {
        val tsid = com.github.f4b6a3.tsid.Tsid.from(version)
        val instant = tsid.instant
        val nodeId = extractNodeId(version)
        val counter = extractCounter(version)
        return "$instant (node=$nodeId, seq=$counter)"
    }
}
