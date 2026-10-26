import { useId } from 'react'
import { Link } from 'react-router'
import styles from './NotFoundState.module.css'

type NotFoundStateProps = {
  title: string
  description?: string
  linkTo: string
  linkLabel: string
}

export function NotFoundState({ title, description, linkTo, linkLabel }: NotFoundStateProps) {
  const titleId = useId()
  return (
    <section className={styles.notFound} aria-labelledby={titleId}>
      <title>{`${title} · yanıt.`}</title>
      <h1 id={titleId} className={styles.title}>
        {title}
      </h1>
      {description ? <p className={styles.description}>{description}</p> : null}
      <Link className={styles.link} to={linkTo}>
        {linkLabel}
      </Link>
    </section>
  )
}
