import { useCallback, useEffect, useState } from 'react'

export type AsyncState<T> =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; data: T }

/** 비동기 조회 상태를 관리한다. key가 바뀌면 다시 조회하고, 늦게 온 이전 응답은 버린다. */
export function useAsync<T>(load: () => Promise<T>, key: string) {
  const [state, setState] = useState<AsyncState<T>>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    setState({ status: 'loading' })
    load()
      .then((data) => active && setState({ status: 'done', data }))
      .catch(() => active && setState({ status: 'error' }))
    return () => {
      active = false
    }
    // load는 key로 대표되므로 의존성에서 제외한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, attempt])

  const retry = useCallback(() => setAttempt((n) => n + 1), [])
  return { state, retry }
}
