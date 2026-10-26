import { ApiError } from '../../../shared/api/ApiError.ts'
import { messageFor, messageForError } from '../../../shared/api/errorMessages.ts'
import { FILE_EMPTY_RULE_MESSAGE, SOURCE_NAME_RULE_MESSAGE, VERSION_RULE_MESSAGE } from './uploadRules.ts'

export type UploadFieldErrors = {
  name?: string
  file?: string
  version?: string
  form?: string
}

type UploadField = 'name' | 'file' | 'version'

const VIOLATION_MESSAGES: Record<UploadField, string> = {
  name: SOURCE_NAME_RULE_MESSAGE,
  file: FILE_EMPTY_RULE_MESSAGE,
  version: VERSION_RULE_MESSAGE,
}

function isUploadField(field: string): field is UploadField {
  return Object.hasOwn(VIOLATION_MESSAGES, field)
}

const FILE_ERROR_CODES = ['FILE_TOO_LARGE', 'UNSUPPORTED_FILE_TYPE', 'UNSUPPORTED_FILE_ENCODING', 'SOURCE_KIND_MISMATCH']

function toViolationErrors(error: ApiError): UploadFieldErrors {
  const errors: UploadFieldErrors = {}
  for (const violation of error.violations) {
    if (isUploadField(violation.field)) {
      errors[violation.field] = VIOLATION_MESSAGES[violation.field]
    }
  }
  return Object.keys(errors).length === 0 ? { form: messageFor(error.errorCode) } : errors
}

export function toUploadFieldErrors(error: unknown): UploadFieldErrors {
  if (!(error instanceof ApiError)) {
    return { form: messageForError(error) }
  }
  if (error.errorCode === 'VALIDATION_FAILED') {
    return toViolationErrors(error)
  }
  if (error.errorCode === 'SOURCE_NAME_TAKEN') {
    return { name: messageFor(error.errorCode) }
  }
  if (FILE_ERROR_CODES.includes(error.errorCode)) {
    return { file: messageFor(error.errorCode) }
  }
  return { form: messageForError(error) }
}
