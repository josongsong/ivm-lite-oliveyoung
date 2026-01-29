import { describe, expect, it } from 'vitest'
import { APP_INFO, CHART_CONFIG, OUTBOX_CONFIG, QUERY_CONFIG } from './index'

describe('Config', () => {
  describe('QUERY_CONFIG', () => {
    it('모든 interval 값이 정의되어 있다', () => {
      expect(QUERY_CONFIG.REALTIME_INTERVAL).toBe(5_000)
      expect(QUERY_CONFIG.DASHBOARD_INTERVAL).toBe(10_000)
      expect(QUERY_CONFIG.OBSERVABILITY_INTERVAL).toBe(15_000)
      expect(QUERY_CONFIG.WORKFLOW_INTERVAL).toBe(30_000)
      expect(QUERY_CONFIG.CHART_INTERVAL).toBe(60_000)
    })

    it('값들이 밀리초 단위로 적절한 범위에 있다', () => {
      // 최소 1초, 최대 2분
      Object.values(QUERY_CONFIG).forEach(value => {
        expect(value).toBeGreaterThanOrEqual(1_000)
        expect(value).toBeLessThanOrEqual(120_000)
      })
    })
  })

  describe('OUTBOX_CONFIG', () => {
    it('모든 outbox 설정 값이 정의되어 있다', () => {
      expect(OUTBOX_CONFIG.DEFAULT_LIMIT).toBe(50)
      expect(OUTBOX_CONFIG.BATCH_RETRY_LIMIT).toBe(100)
      expect(OUTBOX_CONFIG.STALE_TIMEOUT_SECONDS).toBe(300)
    })

    it('limit 값들이 양수이다', () => {
      expect(OUTBOX_CONFIG.DEFAULT_LIMIT).toBeGreaterThan(0)
      expect(OUTBOX_CONFIG.BATCH_RETRY_LIMIT).toBeGreaterThan(0)
    })

    it('stale timeout이 합리적인 범위이다', () => {
      // 1분 ~ 1시간 사이
      expect(OUTBOX_CONFIG.STALE_TIMEOUT_SECONDS).toBeGreaterThanOrEqual(60)
      expect(OUTBOX_CONFIG.STALE_TIMEOUT_SECONDS).toBeLessThanOrEqual(3600)
    })
  })

  describe('CHART_CONFIG', () => {
    it('차트 설정이 정의되어 있다', () => {
      expect(CHART_CONFIG.HOURLY_STATS_HOURS).toBe(24)
    })
  })

  describe('APP_INFO', () => {
    it('앱 정보가 정의되어 있다', () => {
      expect(APP_INFO.VERSION).toBeDefined()
      expect(APP_INFO.NAME).toBe('IVM Lite Admin')
    })

    it('버전이 semver 형식이다', () => {
      expect(APP_INFO.VERSION).toMatch(/^\d+\.\d+\.\d+$/)
    })
  })
})
