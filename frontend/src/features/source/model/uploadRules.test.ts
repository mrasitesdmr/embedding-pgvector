import { describe, expect, it } from 'vitest'
import {
  acceptAttribute,
  FILE_EMPTY_RULE_MESSAGE,
  FILE_REQUIRED_MESSAGE,
  FILE_SIZE_RULE_MESSAGE,
  FILE_TYPE_RULE_MESSAGE,
  resolveSourceName,
  sourceNameFromFileName,
  SOURCE_NAME_RULE_MESSAGE,
  validateSourceName,
  validateUploadFile,
  validateVersionLabel,
  VERSION_RULE_MESSAGE,
} from './uploadRules.ts'

function fileOf(name: string, size: number): File {
  const file = new File(['x'], name)
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('validateUploadFile', () => {
  it('givenNoFile_whenValidate_thenAsksForFile', () => {
    expect(validateUploadFile(null)).toBe(FILE_REQUIRED_MESSAGE)
  })

  it.each([
    ['kilavuz.docx', 10, FILE_TYPE_RULE_MESSAGE],
    ['arsiv.pdf.zip', 10, FILE_TYPE_RULE_MESSAGE],
    ['README', 10, FILE_TYPE_RULE_MESSAGE],
    ['bos.txt', 0, FILE_EMPTY_RULE_MESSAGE],
    ['k.pdf', 20_971_521, FILE_SIZE_RULE_MESSAGE],
    ['k.pdf', 20_971_520, null],
    ['KILAVUZ.PDF', 10, null],
    ['Şifre notları.markdown', 10, null],
    ['tablo.csv', 10, null],
    ['notlar.txt', 10, null],
    ['notlar.md', 1, null],
  ])('givenFile_whenValidateUploadFile_thenReturnsRuleMessage (%s, %i bytes)', (name, size, expected) => {
    expect(validateUploadFile(fileOf(name, size))).toBe(expected)
  })

  it.each([
    ['PDF', 'notlar.md', 'Bu kaynağa sadece PDF dosyası yüklenebilir.'],
    ['PDF', 'kilavuz.PDF', null],
    ['MARKDOWN', 'a.txt', null],
    ['MARKDOWN', 'a.markdown', null],
    ['MARKDOWN', 'a.csv', 'Bu kaynağa sadece TXT, MD ya da MARKDOWN dosyası yüklenebilir.'],
    ['CSV', 'a.pdf', 'Bu kaynağa sadece CSV dosyası yüklenebilir.'],
    ['CSV', 'a.docx', FILE_TYPE_RULE_MESSAGE],
    ['PDF', 'bos.pdf', FILE_EMPTY_RULE_MESSAGE],
  ] as const)('givenSourceKind_whenValidateUploadFile_thenRejectsOtherKinds (%s, %s)', (kind, name, expected) => {
    const size = name === 'bos.pdf' ? 0 : 10
    expect(validateUploadFile(fileOf(name, size), kind)).toBe(expected)
  })

  it('givenWebCrawlKind_whenValidateUploadFile_thenFallsBackToGeneralExtensionList', () => {
    expect(validateUploadFile(fileOf('a.csv', 10), 'WEB_CRAWL')).toBeNull()
  })
})

describe('acceptAttribute', () => {
  it.each([
    [undefined, '.pdf,.txt,.md,.markdown,.csv'],
    ['PDF', '.pdf'],
    ['MARKDOWN', '.txt,.md,.markdown'],
    ['CSV', '.csv'],
    ['TICKET_EXPORT', '.pdf,.txt,.md,.markdown,.csv'],
  ] as const)('givenKind_whenAcceptAttribute_thenListsExtensions (%s)', (kind, expected) => {
    expect(acceptAttribute(kind)).toBe(expected)
  })
})

describe('validateSourceName', () => {
  it.each([
    ['', SOURCE_NAME_RULE_MESSAGE],
    ['   ', SOURCE_NAME_RULE_MESSAGE],
    ['a', SOURCE_NAME_RULE_MESSAGE],
    [' a ', SOURCE_NAME_RULE_MESSAGE],
    ['ab', null],
    [' Masraf notları ', null],
    ['x'.repeat(80), null],
    ['x'.repeat(81), SOURCE_NAME_RULE_MESSAGE],
    [` ${'x'.repeat(80)} `, null],
  ])('givenName_whenValidateSourceName_thenReturnsRuleMessage (%j)', (name, expected) => {
    expect(validateSourceName(name)).toBe(expected)
  })
})

describe('validateVersionLabel', () => {
  it.each([
    ['', null],
    ['3.2', null],
    ['   ', null],
    ['v'.repeat(40), null],
    [` ${'v'.repeat(40)} `, null],
    ['v'.repeat(41), VERSION_RULE_MESSAGE],
  ])('givenLabel_whenValidateVersionLabel_thenReturnsRuleMessage (%j)', (label, expected) => {
    expect(validateVersionLabel(label)).toBe(expected)
  })
})

describe('sourceNameFromFileName', () => {
  it.each([
    ['Atlaskap-Lojistik-Surec-El-Kitabi.pdf', 'Atlaskap-Lojistik-Surec-El-Kitabi'],
    ['rapor.final.md', 'rapor.final'],
    ['  notlar .txt', 'notlar'],
    ['a.pdf', 'a'],
    ['uzantisiz', 'uzantisiz'],
    [`${'x'.repeat(85)}.pdf`, 'x'.repeat(80)],
    [`${'x'.repeat(79)} ${'y'.repeat(10)}.pdf`, 'x'.repeat(79)],
  ])('givenFileName_whenSourceNameFromFileName_thenStripsExtensionTrimsAndCuts (%j)', (fileName, expected) => {
    expect(sourceNameFromFileName(fileName)).toBe(expected)
  })
})

describe('resolveSourceName', () => {
  it.each([
    ['  Süreç  ', 'kilavuz.pdf', 'Süreç'],
    ['', 'kilavuz.pdf', 'kilavuz'],
    ['   ', 'kilavuz.pdf', 'kilavuz'],
  ])('givenTypedName_whenResolveSourceName_thenTypedNameWinsOverFileName (%j)', (rawName, fileName, expected) => {
    expect(resolveSourceName(rawName, new File(['x'], fileName))).toBe(expected)
  })
})
