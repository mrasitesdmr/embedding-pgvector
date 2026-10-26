import { describe, expect, it } from 'vitest'
import { ApiError } from '../../../shared/api/ApiError.ts'
import { toUploadFieldErrors } from './uploadFormErrors.ts'
import { FILE_EMPTY_RULE_MESSAGE, SOURCE_NAME_RULE_MESSAGE, VERSION_RULE_MESSAGE } from './uploadRules.ts'

describe('toUploadFieldErrors', () => {
  it('givenValidationFailedOnAllFields_whenToUploadFieldErrors_thenRuleMessagePerField', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', [
      { field: 'name', message: 'size must be between 2 and 80' },
      { field: 'file', message: 'must not be empty' },
      { field: 'version', message: 'size must be at most 40' },
    ])

    expect(toUploadFieldErrors(error)).toEqual({
      name: SOURCE_NAME_RULE_MESSAGE,
      file: FILE_EMPTY_RULE_MESSAGE,
      version: VERSION_RULE_MESSAGE,
    })
  })

  it('givenValidationFailedOnUnknownField_whenToUploadFieldErrors_thenFormMessage', () => {
    const error = new ApiError(400, 'VALIDATION_FAILED', [{ field: 'page', message: 'bad' }])

    expect(toUploadFieldErrors(error)).toEqual({ form: 'Girdiğin bilgileri kontrol et.' })
  })

  it('givenSourceNameTaken_whenToUploadFieldErrors_thenNameField', () => {
    expect(toUploadFieldErrors(new ApiError(409, 'SOURCE_NAME_TAKEN'))).toEqual({
      name: 'Bu ad çalışma alanında başka bir kaynakta kullanılıyor.',
    })
  })

  it.each(['FILE_TOO_LARGE', 'UNSUPPORTED_FILE_TYPE', 'UNSUPPORTED_FILE_ENCODING', 'SOURCE_KIND_MISMATCH'])(
    'givenFileError_whenToUploadFieldErrors_thenFileField (%s)',
    (errorCode) => {
      const errors = toUploadFieldErrors(new ApiError(415, errorCode))

      expect(Object.keys(errors)).toEqual(['file'])
    },
  )

  it.each(['SOURCE_LIMIT_REACHED', 'INGESTION_IN_PROGRESS', 'EMBEDDING_MODEL_MISMATCH', 'WORKSPACE_NOT_FOUND', 'SOMETHING_NEW'])(
    'givenOtherErrorCode_whenToUploadFieldErrors_thenFormField (%s)',
    (errorCode) => {
      const errors = toUploadFieldErrors(new ApiError(409, errorCode))

      expect(Object.keys(errors)).toEqual(['form'])
    },
  )

  it('givenNetworkError_whenToUploadFieldErrors_thenFormMessageAboutConnection', () => {
    expect(toUploadFieldErrors(new ApiError(0, 'NETWORK_ERROR'))).toEqual({
      form: 'Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et.',
    })
  })
})
