import styles from './Pagination.module.css'

type PaginationProps = {
  page: number
  totalPages: number
  isDisabled?: boolean
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, isDisabled = false, onPageChange }: PaginationProps) {
  const isFirst = page <= 0
  const isLast = page + 1 >= totalPages
  return (
    <nav className={styles.pagination} aria-label="Sayfalar">
      <button
        type="button"
        className={styles.button}
        disabled={isFirst || isDisabled}
        onClick={() => onPageChange(page - 1)}
      >
        Önceki
      </button>
      <span className={styles.status} aria-live="polite">{`Sayfa ${page + 1} / ${totalPages}`}</span>
      <button
        type="button"
        className={styles.button}
        disabled={isLast || isDisabled}
        onClick={() => onPageChange(page + 1)}
      >
        Sonraki
      </button>
    </nav>
  )
}
