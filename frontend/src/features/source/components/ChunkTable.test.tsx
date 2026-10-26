import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { chunk } from '../../../test/fixtures.ts'
import { renderWithProviders } from '../../../test/renderWithProviders.tsx'
import { ChunkTable } from './ChunkTable.tsx'

describe('ChunkTable', () => {
  it('givenHighlightedLongChunk_whenRendered_thenHighlightedFullAndOthersPreview', () => {
    // Arrange
    const highlightedContent = `${'a'.repeat(100)}\n${'b'.repeat(299)}`
    const otherContent = 'c'.repeat(400)

    // Act
    renderWithProviders(
      <ChunkTable
        chunks={[
          chunk(1380, { content: highlightedContent }),
          chunk(1381, { content: otherContent }),
        ]}
        highlightedChunkId={1380}
      />,
    )

    // Assert
    const [, highlightedRow, otherRow] = screen.getAllByRole('row')
    expect(highlightedContent).toHaveLength(400)
    expect(highlightedRow.querySelector('.fullText')?.textContent).toBe(highlightedContent)
    expect(highlightedRow.textContent).not.toContain('…')
    expect(otherRow.textContent).toContain(`${'c'.repeat(160)}…`)
    expect(otherRow.textContent).not.toContain('c'.repeat(161))
  })

  it('givenNoHighlightedChunk_whenRendered_thenAllRowsPreview', () => {
    // Act
    renderWithProviders(
      <ChunkTable chunks={[chunk(1380, { content: 'd'.repeat(400) })]} highlightedChunkId={null} />,
    )

    // Assert
    expect(screen.getAllByRole('row')[1].textContent).toContain(`${'d'.repeat(160)}…`)
  })
})
