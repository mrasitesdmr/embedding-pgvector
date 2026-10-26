export type Violation = { field: string; message: string }

export class ApiError extends Error {
  readonly status: number
  readonly errorCode: string
  readonly violations: Violation[]

  constructor(status: number, errorCode: string, violations: Violation[] = []) {
    super(errorCode)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.violations = violations
  }
}

export function isApiErrorWithCode(error: unknown, errorCode: string): boolean {
  return error instanceof ApiError && error.errorCode === errorCode
}
