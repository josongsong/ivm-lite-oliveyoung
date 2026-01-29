import { describe, expect, it } from 'vitest'
import { formatAge, formatDuration, formatTimeSince, formatUptime } from './formatters'

describe('formatters', () => {
  describe('formatDuration', () => {
    it('초 단위를 올바르게 포맷한다', () => {
      expect(formatDuration(30)).toBe('30s')
      expect(formatDuration(59)).toBe('59s')
    })

    it('분 단위를 올바르게 포맷한다', () => {
      expect(formatDuration(60)).toBe('1m 0s')
      expect(formatDuration(90)).toBe('1m 30s')
      expect(formatDuration(3599)).toBe('59m 59s')
    })

    it('시간 단위를 올바르게 포맷한다', () => {
      expect(formatDuration(3600)).toBe('1h 0m')
      expect(formatDuration(3661)).toBe('1h 1m')
      expect(formatDuration(7200)).toBe('2h 0m')
    })

    it('일 단위를 올바르게 포맷한다', () => {
      expect(formatDuration(86400)).toBe('1d 0h')
      expect(formatDuration(90000)).toBe('1d 1h')
      expect(formatDuration(172800)).toBe('2d 0h')
    })
  })

  describe('formatAge', () => {
    it('초 단위를 한글로 포맷한다', () => {
      expect(formatAge(30)).toBe('30초')
      expect(formatAge(59)).toBe('59초')
    })

    it('분 단위를 한글로 포맷한다', () => {
      expect(formatAge(60)).toBe('1분')
      expect(formatAge(120)).toBe('2분')
      expect(formatAge(3599)).toBe('59분')
    })

    it('시간 단위를 한글로 포맷한다', () => {
      expect(formatAge(3600)).toBe('1시간')
      expect(formatAge(7200)).toBe('2시간')
      expect(formatAge(86399)).toBe('23시간')
    })

    it('일 단위를 한글로 포맷한다', () => {
      expect(formatAge(86400)).toBe('1일')
      expect(formatAge(172800)).toBe('2일')
    })
  })

  describe('formatUptime', () => {
    it('uptime을 "Xd Xh Xm" 형식으로 포맷한다', () => {
      expect(formatUptime(0)).toBe('0d 0h 0m')
      expect(formatUptime(60)).toBe('0d 0h 1m')
      expect(formatUptime(3600)).toBe('0d 1h 0m')
      expect(formatUptime(86400)).toBe('1d 0h 0m')
      expect(formatUptime(90061)).toBe('1d 1h 1m')
    })
  })

  describe('formatTimeSince', () => {
    it('방금 전을 "Just now"로 표시한다', () => {
      const now = new Date().toISOString()
      expect(formatTimeSince(now)).toBe('Just now')
    })

    it('분 단위를 올바르게 표시한다', () => {
      const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
      expect(formatTimeSince(fiveMinutesAgo)).toBe('5 mins ago')

      const oneMinuteAgo = new Date(Date.now() - 1 * 60 * 1000).toISOString()
      expect(formatTimeSince(oneMinuteAgo)).toBe('1 min ago')
    })

    it('시간 단위를 올바르게 표시한다', () => {
      const twoHoursAgo = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
      expect(formatTimeSince(twoHoursAgo)).toBe('2 hours ago')

      const oneHourAgo = new Date(Date.now() - 1 * 60 * 60 * 1000).toISOString()
      expect(formatTimeSince(oneHourAgo)).toBe('1 hour ago')
    })

    it('일 단위를 올바르게 표시한다', () => {
      const twoDaysAgo = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString()
      expect(formatTimeSince(twoDaysAgo)).toBe('2 days ago')

      const oneDayAgo = new Date(Date.now() - 1 * 24 * 60 * 60 * 1000).toISOString()
      expect(formatTimeSince(oneDayAgo)).toBe('1 day ago')
    })
  })
})
