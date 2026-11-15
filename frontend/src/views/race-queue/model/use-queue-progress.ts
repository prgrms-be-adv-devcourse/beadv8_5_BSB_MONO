import { useState } from 'react'

/**
 * 줄에 섰을 때의 순번 대비 지금 순번으로 진행 정도(0~100)를 만든다. 숫자는 화면에 보이지 않는다.
 * 순번을 아직 모르면 undefined. 처음 순번이 1이면 곧 입장이라 100이다.
 */
export function useQueueProgress(rank: number | undefined) {
  const [first, setFirst] = useState<number>()
  if (rank !== undefined && (first === undefined || rank > first)) setFirst(rank)
  if (rank === undefined || first === undefined) return undefined
  return Math.min(100, Math.max(8, Math.round(((first - rank + 1) / first) * 100)))
}
