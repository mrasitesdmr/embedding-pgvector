import { describe, expect, it, vi } from 'vitest'
import { ApiError } from './ApiError.ts'
import { ingestionFailureReasonFor, messageFor, messageForError } from './errorMessages.ts'

const FALLBACK = 'Beklenmeyen bir hata oluştu. Tekrar dene.'

describe('messageFor', () => {
  it.each([
    ['VALIDATION_FAILED', 'Girdiğin bilgileri kontrol et.'],
    ['BAD_REQUEST', 'İstek okunamadı. Sayfayı yenileyip tekrar dene.'],
    ['NOT_FOUND', 'İstenen adres bulunamadı.'],
    ['METHOD_NOT_ALLOWED', 'Bu işlem desteklenmiyor.'],
    ['NOT_ACCEPTABLE', 'Sunucu bu isteğe uygun bir yanıt veremedi.'],
    ['UNSUPPORTED_MEDIA_TYPE', 'İstek biçimi desteklenmiyor.'],
    [
      'EMBEDDING_UNAVAILABLE',
      "Embedding servisine (Ollama) ulaşılamıyor. Ollama'nın çalıştığını kontrol et ya da Kelime yöntemiyle ara.",
    ],
    [
      'EMBEDDING_MODEL_NOT_FOUND',
      "Embedding modeli Ollama'da yüklü değil. Modeli indir (ollama pull bge-m3) ya da Kelime yöntemiyle ara.",
    ],
    ['INTERNAL_ERROR', 'Sunucuda beklenmeyen bir hata oluştu. Tekrar dene.'],
    ['WORKSPACE_NOT_FOUND', 'Çalışma alanı bulunamadı.'],
    ['SOURCE_NOT_FOUND', 'Kaynak bulunamadı.'],
    ['CHUNK_NOT_FOUND', 'Bu parça artık yok; kaynak yeni bir sürüme geçmiş olabilir.'],
    ['INGESTION_JOB_NOT_FOUND', 'Yükleme işi bulunamadı.'],
    ['SOURCE_NAME_TAKEN', 'Bu ad çalışma alanında başka bir kaynakta kullanılıyor.'],
    ['SOURCE_LIMIT_REACHED', 'Bu çalışma alanı 10 kaynak sınırına ulaştı.'],
    ['SOURCE_KIND_MISMATCH', 'Bu kaynağa sadece aynı türde dosya yüklenebilir.'],
    ['INGESTION_IN_PROGRESS', 'Bu kaynağın yüklemesi sürüyor. Bitince tekrar dene.'],
    [
      'EMBEDDING_MODEL_MISMATCH',
      'Ayarlardaki embedding modeli, bu çalışma alanının parçalarını oluşturan modelden farklı. Anlam ve Hybrid arama ile dosya yükleme bu yüzden yapılamıyor; Kelime yöntemiyle arayabilirsin.',
    ],
    ['FILE_TOO_LARGE', "Dosya 20 MB'tan büyük olamaz."],
    ['UNSUPPORTED_FILE_TYPE', 'Bu dosya türü desteklenmiyor. PDF, TXT, MD, MARKDOWN ya da CSV seç.'],
    [
      'UNSUPPORTED_FILE_ENCODING',
      'Metin dosyası UTF-8 olmalı. Dosyayı UTF-8 olarak kaydedip tekrar yükle.',
    ],
    ['NETWORK_ERROR', 'Sunucuya ulaşılamıyor. Bağlantını ve sistemin çalıştığını kontrol et.'],
  ])('givenContractErrorCode_whenMessageFor_thenReturnsSpecificTurkishMessage (%s)', (errorCode, expected) => {
    expect(messageFor(errorCode)).toBe(expected)
  })

  it.each(['SOMETHING_NEW', 'UNEXPECTED_ERROR', ''])(
    'givenUnknownCode_whenMessageFor_thenReturnsFallback (%j)',
    (errorCode) => {
      expect(messageFor(errorCode)).toBe(FALLBACK)
    },
  )
})

describe('messageForError', () => {
  it('givenApiError_whenMessageForError_thenUsesErrorCodeMessage', () => {
    expect(messageForError(new ApiError(404, 'SOURCE_NOT_FOUND'))).toBe('Kaynak bulunamadı.')
  })

  it('givenUnknownError_whenMessageForError_thenReturnsFallbackAndLogsError', () => {
    // Arrange
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    try {
      // Act
      const message = messageForError(new TypeError('boom'))

      // Assert
      expect(message).toBe(FALLBACK)
      expect(errorSpy).toHaveBeenCalledTimes(1)
    } finally {
      errorSpy.mockRestore()
    }
  })
})

describe('ingestionFailureReasonFor', () => {
  it.each([
    ['NO_TEXT_EXTRACTED', 'dosyada metin yok (sadece görsel içeren PDF ya da sadece başlık satırı olan CSV olabilir)'],
    ['FILE_UNREADABLE', 'PDF okunamıyor (dosya bozuk ya da parola korumalı olabilir)'],
    ['EMBEDDING_UNAVAILABLE', 'embedding servisine (Ollama) ulaşılamıyor'],
    ['EMBEDDING_MODEL_NOT_FOUND', "embedding modeli Ollama'da yüklü değil (ollama pull bge-m3)"],
    ['EMBEDDING_MODEL_MISMATCH', 'model uyuşmazlığı: embedding modeli beklenenden farklı boyutta vektör döndürdü'],
    ['INGESTION_INTERRUPTED', 'iş yarıda kaldı (sunucu iş sürerken durdu); dosyayı tekrar yükle'],
    ['INTERNAL_ERROR', 'sunucuda beklenmeyen bir hata oluştu'],
    [null, 'beklenmeyen bir hata oluştu'],
    [undefined, 'beklenmeyen bir hata oluştu'],
    ['SOMETHING_NEW', 'beklenmeyen bir hata oluştu'],
  ])('givenIngestionErrorCode_whenIngestionFailureReasonFor_thenReturnsTurkishReason (%s)', (errorCode, expected) => {
    expect(ingestionFailureReasonFor(errorCode)).toBe(expected)
  })

  it('givenIngestionReason_whenCompared_thenDoesNotAdviseKeywordSearchUnlikeHttpMessage', () => {
    expect(ingestionFailureReasonFor('EMBEDDING_UNAVAILABLE')).not.toContain('Kelime')
    expect(messageFor('EMBEDDING_UNAVAILABLE')).toContain('Kelime')
  })
})
