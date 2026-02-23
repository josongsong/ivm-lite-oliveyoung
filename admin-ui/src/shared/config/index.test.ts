import { describe, expect, it } from 'vitest'
import { APP_INFO, CHART_CONFIG, QUERY_CONFIG } from './index'

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
