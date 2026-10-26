import { HttpResponse } from 'msw'

type TestViolation = { field: string; message: string }

export function problem(status: number, errorCode: string, violations?: TestViolation[]) {
  return HttpResponse.json(
    {
      type: `https://embedding-pgvector.example/errors/${errorCode.toLowerCase().replaceAll('_', '-')}`,
      title: 'Error',
      status,
      detail: 'Embedding service is not reachable',
      errorCode,
      ...(violations === undefined ? {} : { violations }),
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}
