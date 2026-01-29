import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/mocks/server'
import { ApiError, fetchApi, postApi } from './client'

describe('API Client', () => {
  describe('fetchApi', () => {
    it('성공적인 GET 요청을 처리한다', async () => {
      const data = await fetchApi<{ status: string }>('/health')

      expect(data).toHaveProperty('status')
      expect(data.status).toBe('HEALTHY')
    })

    it('404 에러 시 ApiError를 던진다', async () => {
      server.use(
        http.get('/api/test-404', () => {
          return HttpResponse.json({ error: 'Not found' }, { status: 404 })
        })
      )

      await expect(fetchApi('/test-404')).rejects.toThrow(ApiError)
      await expect(fetchApi('/test-404')).rejects.toMatchObject({
        status: 404,
        statusText: 'Not Found',
      })
    })

    it('500 에러 시 서버 메시지를 포함한 ApiError를 던진다', async () => {
      server.use(
        http.get('/api/test-500', () => {
          return HttpResponse.json(
            { message: 'Internal Server Error: Database connection failed' },
            { status: 500 }
          )
        })
      )

      try {
        await fetchApi('/test-500')
        expect.fail('Should have thrown')
      } catch (error) {
        expect(error).toBeInstanceOf(ApiError)
        expect((error as ApiError).status).toBe(500)
        expect((error as ApiError).serverMessage).toContain('Database connection failed')
      }
    })
  })

  describe('postApi', () => {
    it('성공적인 POST 요청을 처리한다', async () => {
      server.use(
        http.post('/api/test-post', async ({ request }) => {
          const body = await request.json()
          return HttpResponse.json({ received: body, success: true })
        })
      )

      const data = await postApi<{ success: boolean }>('/test-post', { name: 'test' })

      expect(data.success).toBe(true)
    })

    it('body 없이 POST 요청을 보낼 수 있다', async () => {
      server.use(
        http.post('/api/test-empty-post', () => {
          return HttpResponse.json({ success: true })
        })
      )

      const data = await postApi<{ success: boolean }>('/test-empty-post')

      expect(data.success).toBe(true)
    })

    it('POST 에러 시 ApiError를 던진다', async () => {
      server.use(
        http.post('/api/test-post-error', () => {
          return HttpResponse.json({ error: 'Validation failed' }, { status: 400 })
        })
      )

      await expect(postApi('/test-post-error', {})).rejects.toThrow(ApiError)
    })
  })

  describe('ApiError', () => {
    it('서버 메시지가 있을 때 메시지에 포함한다', () => {
      const error = new ApiError(400, 'Bad Request', 'Validation failed')

      expect(error.message).toBe('400 Bad Request: Validation failed')
      expect(error.status).toBe(400)
      expect(error.statusText).toBe('Bad Request')
      expect(error.serverMessage).toBe('Validation failed')
      expect(error.name).toBe('ApiError')
    })

    it('서버 메시지가 없을 때 기본 메시지만 표시한다', () => {
      const error = new ApiError(500, 'Internal Server Error')

      expect(error.message).toBe('500 Internal Server Error')
      expect(error.serverMessage).toBeUndefined()
    })
  })
})
