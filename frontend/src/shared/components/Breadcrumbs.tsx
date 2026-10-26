import { Link } from 'react-router'
import styles from './Breadcrumbs.module.css'

export type BreadcrumbItem = { label: string; to?: string }

type BreadcrumbsProps = { items: BreadcrumbItem[] }

export function Breadcrumbs({ items }: BreadcrumbsProps) {
  return (
    <nav aria-label="Konum">
      <ol className={styles.crumbs}>
        {items.map((item, index) => {
          const isLast = index === items.length - 1
          return (
            <li key={`${index}-${item.label}`} className={styles.item}>
              {index > 0 ? (
                <span className={styles.separator} aria-hidden="true">
                  ›
                </span>
              ) : null}
              {isLast || item.to === undefined ? (
                <span className={isLast ? styles.current : undefined} aria-current={isLast ? 'page' : undefined}>
                  {item.label}
                </span>
              ) : (
                <Link className={styles.link} to={item.to}>
                  {item.label}
                </Link>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
