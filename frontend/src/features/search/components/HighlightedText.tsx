import type { HighlightSegment } from '../model/searchHitText.ts'
import styles from './HighlightedText.module.css'

type HighlightedTextProps = { segments: HighlightSegment[] }

export function HighlightedText({ segments }: HighlightedTextProps) {
  return (
    <>
      {segments.map((segment, index) =>
        segment.isMatch ? (
          <mark key={index} className={styles.mark}>
            {segment.text}
          </mark>
        ) : (
          <span key={index}>{segment.text}</span>
        ),
      )}
    </>
  )
}
