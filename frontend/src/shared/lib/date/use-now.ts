import { useEffect, useState } from 'react'

/**
 * 마운트 뒤의 현재 시각. 서버 렌더와 첫 hydration에서는 undefined라 두 값이 어긋나지 않는다.
 * 렌더 중에 `new Date()` · `Date.now()`를 읽는 대신 쓴다. 이후 갱신하지 않는다.
 */
export function useNow(): Date | undefined {
  const [now, setNow] = useState<Date>()
  // 마운트 뒤 한 번만 읽는 것이 목적이라 effect 안의 setState가 맞다
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => setNow(new Date()), [])
  return now
}
