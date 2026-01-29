package com.oliveyoung.ivmlite.pkg.alerts.ports

import com.oliveyoung.ivmlite.pkg.alerts.domain.AlertRule

/**
 * Alert Rule Loader Port
 * 
 * AlertRule을 로드하는 책임.
 * YAML 파일, DB, 또는 in-memory에서 로드할 수 있다.
 */
interface AlertRuleLoaderPort {
    
    /**
     * 모든 규칙 로드
     */
    fun loadAll(): List<AlertRule>
    
    /**
     * ID로 규칙 조회
     */
    fun findById(id: String): AlertRule?
    
    /**
     * 활성화된 규칙만 조회
     */
    fun loadEnabled(): List<AlertRule> = loadAll().filter { it.enabled }
    
    /**
     * 규칙 리로드 (설정 변경 시)
     */
    fun reload()
}
