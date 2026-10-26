export type MultipartFile = { field: string; fileName: string; content: string }

export type MultipartParts = {
  fields: Record<string, string>
  files: MultipartFile[]
}

export async function readMultipart(request: Request): Promise<MultipartParts> {
  const contentType = request.headers.get('content-type') ?? ''
  const boundary = /boundary=(.+)$/.exec(contentType)?.[1]
  if (boundary === undefined) {
    throw new Error(`not a multipart request: ${contentType}`)
  }
  const text = await request.text()
  const parts: MultipartParts = { fields: {}, files: [] }
  for (const rawPart of text.split(`--${boundary}`)) {
    const separatorIndex = rawPart.indexOf('\r\n\r\n')
    if (separatorIndex === -1) {
      continue
    }
    const headers = rawPart.slice(0, separatorIndex)
    const content = rawPart.slice(separatorIndex + 4).replace(/\r\n$/, '')
    const field = /name="([^"]*)"/.exec(headers)?.[1]
    if (field === undefined) {
      continue
    }
    const fileName = /filename="([^"]*)"/.exec(headers)?.[1]
    if (fileName === undefined) {
      parts.fields[field] = content
    } else {
      parts.files.push({ field, fileName, content })
    }
  }
  return parts
}
