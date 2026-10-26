import type { SearchBranch } from '../model/types.ts'
import styles from './SearchHitTags.module.css'

type SearchHitTagsProps = { matchedBy: SearchBranch[] }

export function SearchHitTags({ matchedBy }: SearchHitTagsProps) {
  const isSemantic = matchedBy.includes('SEMANTIC')
  const isKeyword = matchedBy.includes('KEYWORD')
  if (!isSemantic && !isKeyword) {
    return null
  }
  return (
    <>
      {isSemantic ? <span className={`${styles.tag} ${styles.semantic}`}>anlam</span> : null}
      {isKeyword ? <span className={`${styles.tag} ${styles.keyword}`}>kelime</span> : null}
    </>
  )
}
