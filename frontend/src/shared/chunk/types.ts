export type ChunkResponse = {
  chunkId: number
  chunkNo: number
  heading?: string | null
  content: string
  tokenCount: number
  embeddingModel: string
}
