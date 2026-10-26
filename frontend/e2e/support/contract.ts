import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { Ajv2020 } from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import { parse } from 'yaml'

const OPENAPI_FILE = fileURLToPath(new URL('../../../docs/api/openapi.yaml', import.meta.url))
const DOCUMENT_ID = 'openapi.json'

type Schema = Record<string, unknown>

function strictenObjectSchemas(schemas: Record<string, Schema>) {
  for (const schema of Object.values(schemas)) {
    if (schema.type === 'object' && schema.properties !== undefined) {
      schema.additionalProperties = false
    }
  }
}

function createValidator(): Ajv2020 {
  const document = parse(readFileSync(OPENAPI_FILE, 'utf-8')) as { components: { schemas: Record<string, Schema> } }
  strictenObjectSchemas(document.components.schemas)
  const ajv = new Ajv2020({ strict: false, allErrors: true })
  addFormats.default(ajv)
  for (const format of ['int32', 'int64', 'float', 'double', 'binary']) {
    ajv.addFormat(format, true)
  }
  ajv.addSchema({ ...document, $id: DOCUMENT_ID })
  return ajv
}

const validator = createValidator()

export function expectMatchesSchema(body: unknown, schemaName: string): void {
  const validate = validator.compile({ $ref: `${DOCUMENT_ID}#/components/schemas/${schemaName}` })
  if (!validate(body)) {
    throw new Error(
      `Response does not match schema ${schemaName}:\n${JSON.stringify(validate.errors, null, 2)}\nBody: ${JSON.stringify(body).slice(0, 1500)}`,
    )
  }
}
