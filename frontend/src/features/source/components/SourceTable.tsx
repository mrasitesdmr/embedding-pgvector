import table from '../../../shared/components/table.module.css'
import type { SourceResponse } from '../model/types.ts'
import { SourceRow } from './SourceRow.tsx'

type SourceTableProps = {
  workspaceId: number
  sources: SourceResponse[]
}

export function SourceTable({ workspaceId, sources }: SourceTableProps) {
  return (
    <div className={table.scroll}>
      <table className={table.table}>
        <thead>
          <tr>
            <th scope="col">Kaynak</th>
            <th scope="col">Tür</th>
            <th scope="col">Parça</th>
            <th scope="col">Durum</th>
            <th scope="col">Son senkron</th>
          </tr>
        </thead>
        <tbody>
          {sources.map((source) => (
            <SourceRow key={source.sourceId} workspaceId={workspaceId} source={source} />
          ))}
        </tbody>
      </table>
    </div>
  )
}
