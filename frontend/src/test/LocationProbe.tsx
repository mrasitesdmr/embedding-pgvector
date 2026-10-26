import { useLayoutEffect } from 'react'
import { useLocation } from 'react-router'
import { locationState } from './locationState.ts'

export function LocationProbe() {
  const { pathname, search } = useLocation()

  useLayoutEffect(() => {
    locationState.url = `${pathname}${search}`
  }, [pathname, search])

  return null
}
