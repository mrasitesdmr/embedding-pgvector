import type { MatchedTermField, MatchedTermResponse, SearchHitResponse } from './types.ts'

export const SNIPPET_MAX_LENGTH = 190
export const SNIPPET_CUT_LENGTH = 188
export const UNTITLED_CHUNK_TITLE = 'Başlıksız parça'

export type HighlightSegment = { text: string; isMatch: boolean }

export function buildSnippet(content: string): string {
  if (content.length <= SNIPPET_MAX_LENGTH) {
    return content
  }
  const lastSpace = content.lastIndexOf(' ', SNIPPET_CUT_LENGTH)
  const cut = lastSpace > 0 ? lastSpace : SNIPPET_CUT_LENGTH
  return `${content.slice(0, cut)} …`
}

export function toHighlightSegments(
  text: string,
  terms: MatchedTermResponse[],
  field: MatchedTermField,
): HighlightSegment[] {
  const ranges = terms
    .filter((term) => term.field === field && term.start >= 0 && term.start < term.end && term.end <= text.length)
    .map((term) => ({ start: term.start, end: term.end }))
    .toSorted((left, right) => left.start - right.start || left.end - right.end)

  const merged: { start: number; end: number }[] = []
  for (const range of ranges) {
    const previous = merged.at(-1)
    if (previous !== undefined && range.start <= previous.end) {
      previous.end = Math.max(previous.end, range.end)
    } else {
      merged.push({ ...range })
    }
  }

  const segments: HighlightSegment[] = []
  let position = 0
  for (const range of merged) {
    if (range.start > position) {
      segments.push({ text: text.slice(position, range.start), isMatch: false })
    }
    segments.push({ text: text.slice(range.start, range.end), isMatch: true })
    position = range.end
  }
  if (position < text.length) {
    segments.push({ text: text.slice(position), isMatch: false })
  }
  return segments
}

export function hitTitle(heading: string | null | undefined): string {
  return heading ?? UNTITLED_CHUNK_TITLE
}

export function hitTitleSegments(hit: SearchHitResponse): HighlightSegment[] {
  if (hit.heading === null || hit.heading === undefined) {
    return [{ text: UNTITLED_CHUNK_TITLE, isMatch: false }]
  }
  return toHighlightSegments(hit.heading, hit.matchedTerms, 'HEADING')
}

export function hitSnippetSegments(hit: SearchHitResponse): HighlightSegment[] {
  return toHighlightSegments(buildSnippet(hit.content), hit.matchedTerms, 'CONTENT')
}

export function hitContentSegments(hit: SearchHitResponse): HighlightSegment[] {
  return toHighlightSegments(hit.content, hit.matchedTerms, 'CONTENT')
}

export function hitSourceLine(hit: SearchHitResponse): string {
  return `${hit.sourceName} · parça ${hit.chunkNo} · #${hit.chunkId}`
}
