import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const DEFAULT_COMPOSE_FILE = fileURLToPath(new URL('../../deployment/docker-compose.yml', import.meta.url))
const RESET_STATEMENT = [
  'TRUNCATE search_events, ingestion_jobs, chunks, sources CASCADE',
  'ALTER SEQUENCE sources_id_seq RESTART WITH 10001',
].join('; ')

export default function globalSetup() {
  if (process.env.E2E_OLLAMA_DOWN === '1') {
    return
  }
  const composeFile = process.env.E2E_COMPOSE_FILE ?? DEFAULT_COMPOSE_FILE
  execFileSync(
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
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      RESET_STATEMENT,
    ],
    { stdio: 'inherit' },
  )
}
