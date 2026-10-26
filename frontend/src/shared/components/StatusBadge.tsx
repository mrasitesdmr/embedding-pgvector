import type { ReactNode } from 'react'
import styles from './StatusBadge.module.css'

export type StatusTone = 'ok' | 'run' | 'bad' | 'neutral'

type StatusBadgeProps = {
  tone: StatusTone
  children: ReactNode
}

export function StatusBadge({ tone, children }: StatusBadgeProps) {
  return <span className={`${styles.badge} ${styles[tone]}`}>{children}</span>
}
