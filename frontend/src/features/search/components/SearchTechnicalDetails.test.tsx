import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { hit, searchResult } from '../../../test/fixtures.ts'
import { renderWithProviders } from '../../../test/renderWithProviders.tsx'
import { SearchTechnicalDetails } from './SearchTechnicalDetails.tsx'

describe('SearchTechnicalDetails', () => {
  it('givenHybridResultWithPlan_whenOpenTechnicalDetails_thenShowsBranchCountsRanksAndPlan', async () => {
    // Arrange
    const user = userEvent.setup()
    const result = searchResult({
      keywordCount: 1,
      vectorCount: 10,
      timings: { embeddingMs: 40, sqlMs: 12, totalMs: 57 },
      vectorPlan: 'Index Scan using idx_chunks_embedding on chunks',
      hits: [
        hit({ keywordRank: 1, keywordScore: 0.1, vectorRank: 1, distance: 0.2345, rrfScore: 0.0328 }),
        hit({ rank: 2, chunkId: 1381, keywordRank: null, keywordScore: null, vectorRank: 2, distance: 0.3, rrfScore: 0.0161 }),
      ],
    })
    const { container } = renderWithProviders(<SearchTechnicalDetails result={result} />)
    const details = container.querySelector('details')

    // Assert
    expect(details?.open).toBe(false)

    // Act
    await user.click(screen.getByText('Teknik detay'))

    // Assert
    expect(details?.open).toBe(true)
    const facts = within(container.querySelector('dl') as HTMLElement)
    expect(facts.getByText('Kelime kolu satır').nextSibling).toHaveTextContent('1')
    expect(facts.getByText('Vektör kolu satır').nextSibling).toHaveTextContent('10')
    expect(facts.getByText('Embedding süresi').nextSibling).toHaveTextContent('40 ms')
    expect(facts.getByText('SQL süresi').nextSibling).toHaveTextContent('12 ms')
    expect(facts.getByText('Toplam süre').nextSibling).toHaveTextContent('57 ms')
    expect(facts.getByText('Yöntem').nextSibling).toHaveTextContent('Hybrid')
    expect(facts.getByText('Filtre stratejisi').nextSibling).toHaveTextContent('iterative scan')
    expect(facts.getByText('ef_search').nextSibling).toHaveTextContent('40')
    const rows = screen.getAllByRole('row')
    const first = within(rows[1]).getAllByRole('cell').map((cell) => cell.textContent)
    expect(first).toEqual(['1', '#1380', '1', '0,1000', '1', '0,2345', '0,0328'])
    const second = within(rows[2]).getAllByRole('cell').map((cell) => cell.textContent)
    expect(second).toEqual(['2', '#1381', '—', '—', '2', '0,3000', '0,0161'])
    const plan = screen.getByRole('region', { name: 'Sorgu planı metni' })
    expect(plan.querySelector('pre')).toHaveTextContent('Index Scan using idx_chunks_embedding')
  })

  it('givenKeywordResult_whenRendered_thenVectorFieldsShowDashAndNoPlan', () => {
    // Arrange
    const result = searchResult({
      mode: 'KEYWORD',
      filterStrategy: null,
      efSearch: null,
      vectorCount: null,
      timings: { embeddingMs: null, sqlMs: 9, totalMs: 12 },
      vectorPlan: null,
    })

    // Act
    const { container } = renderWithProviders(<SearchTechnicalDetails result={result} />)

    // Assert
    const facts = within(container.querySelector('dl') as HTMLElement)
    expect(facts.getByText('Filtre stratejisi').nextSibling).toHaveTextContent('—')
    expect(facts.getByText('ef_search').nextSibling).toHaveTextContent('—')
    expect(facts.getByText('Vektör kolu satır').nextSibling).toHaveTextContent('—')
    expect(facts.getByText('Embedding süresi').nextSibling).toHaveTextContent('—')
    expect(screen.queryByText('Sorgu planı')).not.toBeInTheDocument()
  })
})
