import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../test/server.ts'
import { readMultipart } from '../../test/multipart.ts'
import { problem } from '../../test/problem.ts'
import { ApiError } from './ApiError.ts'
import { request, withQuery } from './httpClient.ts'

async function catchApiError(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    if (error instanceof ApiError) {
      return error
    }
    throw error
  }
  throw new Error('expected ApiError')
}

describe('request', () => {
  it('givenProblemResponse_whenRequest_thenThrowsApiErrorWithCode', async () => {
    // Arrange
    server.use(http.get('/api/workspaces/999', () => problem(404, 'WORKSPACE_NOT_FOUND')))

    // Act
    const error = await catchApiError(request('/api/workspaces/999'))

    // Assert
    expect(error.status).toBe(404)
    expect(error.errorCode).toBe('WORKSPACE_NOT_FOUND')
    expect(error.violations).toEqual([])
  })

  it('givenValidationProblem_whenRequest_thenKeepsViolations', async () => {
    // Arrange
    server.use(
      http.post('/api/x', () =>
        problem(400, 'VALIDATION_FAILED', [{ field: 'query', message: 'size must be between 1 and 500' }]),
      ),
    )

    // Act
    const error = await catchApiError(request('/api/x', { method: 'POST', body: '{}' }))

    // Assert
    expect(error.errorCode).toBe('VALIDATION_FAILED')
    expect(error.violations).toEqual([{ field: 'query', message: 'size must be between 1 and 500' }])
  })

  it('givenBodyWithoutErrorCode_whenRequest_thenUnexpectedError', async () => {
    // Arrange
    server.use(http.get('/api/x', () => new HttpResponse('Bad Request', { status: 400 })))

    // Act
    const error = await catchApiError(request('/api/x'))

    // Assert
    expect(error.status).toBe(400)
    expect(error.errorCode).toBe('UNEXPECTED_ERROR')
  })

  it('givenJsonWithoutErrorCode_whenRequest_thenUnexpectedError', async () => {
    // Arrange
    server.use(http.get('/api/x', () => HttpResponse.json({ title: 'x' }, { status: 404 })))

    // Act
    const error = await catchApiError(request('/api/x'))

    // Assert
    expect(error.errorCode).toBe('UNEXPECTED_ERROR')
  })

  it('givenNetworkFailure_whenRequest_thenNetworkError', async () => {
    // Arrange
    server.use(http.get('/api/x', () => HttpResponse.error()))

    // Act
    const error = await catchApiError(request('/api/x'))

    // Assert
    expect(error.errorCode).toBe('NETWORK_ERROR')
    expect(error.status).toBe(0)
  })

  it('givenJsonBody_whenRequest_thenSendsJsonContentType', async () => {
    // Arrange
    let contentType: string | null = null
    server.use(
      http.post('/api/x', ({ request: incoming }) => {
        contentType = incoming.headers.get('content-type')
        return HttpResponse.json({ ok: true })
      }),
    )

    // Act
    const body = await request<{ ok: boolean }>('/api/x', { method: 'POST', body: JSON.stringify({ a: 1 }) })

    // Assert
    expect(body).toEqual({ ok: true })
    expect(contentType).toBe('application/json')
  })
})

describe('request gateway errors', () => {
  it.each([
    [500, '', 'text/plain'],
    [502, '<html><body>502 Bad Gateway</body></html>', 'text/html'],
    [504, '<html><body>504 Gateway Time-out</body></html>', 'text/html'],
  ])(
    'givenGatewayErrorWithoutProblemBody_whenRequest_thenNetworkErrorWithStatus (%i)',
    async (status, body, contentType) => {
      // Arrange
      server.use(http.get('/api/x', () => new HttpResponse(body, { status, headers: { 'Content-Type': contentType } })))

      // Act
      const error = await catchApiError(request('/api/x'))

      // Assert
      expect(error.status).toBe(status)
      expect(error.errorCode).toBe('NETWORK_ERROR')
    },
  )

  it.each([502, 503])('givenServerErrorWithJsonBodyWithoutErrorCode_whenRequest_thenNetworkError (%i)', async (status) => {
    // Arrange
    server.use(http.get('/api/x', () => HttpResponse.json({ title: 'x' }, { status })))

    // Act
    const error = await catchApiError(request('/api/x'))

    // Assert
    expect(error.status).toBe(status)
    expect(error.errorCode).toBe('NETWORK_ERROR')
  })

  it.each([
    [503, 'EMBEDDING_UNAVAILABLE'],
    [500, 'INTERNAL_ERROR'],
  ])('givenServerErrorWithProblemBody_whenRequest_thenKeepsErrorCode (%i %s)', async (status, errorCode) => {
    // Arrange
    server.use(http.get('/api/x', () => problem(status, errorCode)))

    // Act
    const error = await catchApiError(request('/api/x'))

    // Assert
    expect(error.status).toBe(status)
    expect(error.errorCode).toBe(errorCode)
  })
})

describe('request with FormData', () => {
  it('givenFormDataBody_whenRequest_thenSendsMultipartWithoutJsonContentType', async () => {
    // Arrange
    let contentType: string | null = null
    let parts: Awaited<ReturnType<typeof readMultipart>> | null = null
    server.use(
      http.post('/api/x', async ({ request: incoming }) => {
        contentType = incoming.headers.get('content-type')
        parts = await readMultipart(incoming)
        return HttpResponse.json({ ok: true })
      }),
    )
    const body = new FormData()
    body.append('name', 'Yardım notları')
    body.append('file', new File(['# Başlık'], 'izin-notlari.md', { type: 'text/markdown' }))

    // Act
    const response = await request<{ ok: boolean }>('/api/x', { method: 'POST', body })

    // Assert
    expect(response).toEqual({ ok: true })
    expect(contentType).toMatch(/^multipart\/form-data; boundary=/)
    expect(parts).toEqual({
      fields: { name: 'Yardım notları' },
      files: [{ field: 'file', fileName: 'izin-notlari.md', content: '# Başlık' }],
    })
  })
})

describe('withQuery', () => {
  it('givenParamsWithNullAndUndefined_whenWithQuery_thenSkipsEmptyValues', () => {
    expect(withQuery('/api/x', { page: 0, size: 20, sort: undefined, q: null })).toBe('/api/x?page=0&size=20')
  })

  it('givenNoParams_whenWithQuery_thenReturnsPathUnchanged', () => {
    expect(withQuery('/api/x', {})).toBe('/api/x')
  })
})
