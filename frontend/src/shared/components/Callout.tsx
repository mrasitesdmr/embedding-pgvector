import type { ReactNode } from 'react'
import styles from './Callout.module.css'

type CalloutProps = {
  tone?: 'info' | 'warning'
  children: ReactNode
}

export function Callout({ tone = 'info', children }: CalloutProps) {
  return (
    <div className={`${styles.callout} ${styles[tone]}`}>
      <InfoIcon />
      <p className={styles.text}>{children}</p>
    </div>
  )
}

function InfoIcon() {
  return (
    <svg className={styles.icon} width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v6M12 7.5h.01" />
    </svg>
  )
}
