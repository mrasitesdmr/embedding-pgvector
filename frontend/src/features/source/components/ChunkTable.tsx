import type { ChunkResponse } from '../../../shared/chunk/types.ts'
import table from '../../../shared/components/table.module.css'
import { formatInteger } from '../../../shared/format/numberFormat.ts'
import { chunkTitle, previewChunkContent } from '../model/sourceRules.ts'
import styles from './ChunkTable.module.css'

type ChunkTableProps = {
  chunks: ChunkResponse[]
  highlightedChunkId: number | null
}

export function ChunkTable({ chunks, highlightedChunkId }: ChunkTableProps) {
  return (
    <div className={table.scroll}>
      <table className={table.table}>
        <thead>
          <tr>
            <th scope="col">#</th>
            <th scope="col">Parça</th>
            <th scope="col">Token</th>
          </tr>
        </thead>
        <tbody>
          {chunks.map((chunk) => {
            const isHighlighted = chunk.chunkId === highlightedChunkId
            return (
              <tr key={chunk.chunkId} className={isHighlighted ? styles.highlighted : undefined}>
                <td className={table.num}>{chunk.chunkNo}</td>
                <td>
                  <span className={table.primary}>{chunkTitle(chunk.heading)}</span>
                  {isHighlighted ? (
                    <span className={styles.fullText}>{chunk.content}</span>
                  ) : (
                    <span className={styles.preview}>{previewChunkContent(chunk.content)}</span>
                  )}
                  <span className={table.secondary}>{`chunk ${chunk.chunkId}`}</span>
                </td>
                <td className={table.num}>{formatInteger(chunk.tokenCount)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
