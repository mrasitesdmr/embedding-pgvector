import styles from './ErrorAlert.module.css'

type ErrorAlertProps = {
  message: string
  onRetry?: () => void
}

export function ErrorAlert({ message, onRetry }: ErrorAlertProps) {
  return (
    <div className={styles.alert} role="alert">
      <p>{message}</p>
      {onRetry ? (
        <button type="button" className={styles.retry} onClick={onRetry}>
          Tekrar dene
        </button>
      ) : null}
    </div>
  )
}
