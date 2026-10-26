import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const DEFAULT_COMPOSE_FILE = fileURLToPath(new URL('../../../deployment/docker-compose.yml', import.meta.url))

export function queryRows(sql: string): string[][] {
  const composeFile = process.env.E2E_COMPOSE_FILE ?? DEFAULT_COMPOSE_FILE
  const output = execFileSync(
    'docker',
    [
      'compose',
      '-f',
      composeFile,
      'exec',
      '-T',
      'postgres',
      'psql',
      '-U',
      'embedding',
      '-d',
      'embedding_pgvector',
      '-At',
      '-F',
      '|',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      sql,
    ],
    { encoding: 'utf-8' },
  )
  return output
    .split('\n')
    .filter((line) => line !== '')
    .map((line) => line.split('|'))
}

export function queryValue(sql: string): string {
  const rows = queryRows(sql)
  if (rows.length !== 1 || rows[0].length !== 1) {
    throw new Error(`Expected a single value for: ${sql}`)
  }
  return rows[0][0]
}

export function execute(sql: string): void {
  queryRows(sql)
}
