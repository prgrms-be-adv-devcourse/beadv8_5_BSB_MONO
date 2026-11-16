'use client'

import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'

import { ApiError, queueApi, unwrapData } from '@/shared/api'

import { ADMISSION_POLL_MS } from './use-queue-admission'

/** 줄을 잃은 것(404)으로 보고 다시 등록해 볼 수 있는 횟수. 끝없이 되풀이하지 않게 막는다 */
const MAX_REREGISTER = 2

const isId = (id: number | undefined): id is number => Number.isInteger(id) && (id ?? 0) > 0

const statusOf = (error: unknown) => (error instanceof ApiError ? error.status : undefined)

/**
 * 대기열 줄서기 (POST /api/v1/queue → GET /api/v1/queue/races/{raceId}/racecourses/{raceCourseId}).
 * 화면에 들어오면 한 번 등록하고, 입장(canEnter)할 때까지 ADMISSION_POLL_MS마다 순번을 다시 묻는다.
 * 입장 시간이 끝나는 시각은 주문 서비스의 입장 확인(useQueueAdmission)이 따로 알려 주므로,
 * 이 훅은 "이제 입장 확인을 물어도 되는지(canEnter)"만 알려 준다.
 *
 * - 이미 입장한 회원의 등록(409)은 입장 가능으로 본다.
 * - 줄에서 사라지면(순번 조회 404) 다시 등록한다. MAX_REREGISTER번 해도 404면 failed로 알리고 멈춘다.
 * - 등록이 401이 아닌 오류로 실패하면(접수 기간 아님 403 · 대회 없음 404 · 대기열 장애 503 등) 줄을 서지 못한 것이다.
 *   입장 확인(TTL)은 대기열을 정상으로 거친 뒤에만 묻고 백엔드도 그때만 입장을 내주므로 canEnter로 두지 않고,
 *   화면이 skippedStatus로 이유를 안내한다.
 */
export function useQueuePosition(raceId: number | undefined, raceCourseId: number | undefined) {
  const enabled = isId(raceId) && isId(raceCourseId)
  const id = { raceId: raceId ?? 0, raceCourseId: raceCourseId ?? 0 }

  const register = useQuery({
    queryKey: ['queue-register', id.raceId, id.raceCourseId],
    queryFn: () => queueApi.register({ raceId: id.raceId, raceCourseId: id.raceCourseId }),
    select: unwrapData,
    enabled,
    retry: false,
    // 화면에 들어올 때마다 새로 줄을 선다 (끝난 입장 · 이전 순번을 이어 쓰지 않는다)
    gcTime: 0,
    staleTime: Infinity,
    refetchOnMount: 'always',
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  })
  const registerStatus = statusOf(register.error)
  const alreadyActive = registerStatus === 409
  const registered = register.isSuccess

  const position = useQuery({
    ...queueApi.getGetStatusQueryOptions(id.raceId, id.raceCourseId),
    select: unwrapData,
    enabled: enabled && registered,
    retry: false,
    gcTime: 0,
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    refetchInterval: (query) => {
      if (query.state.data?.data?.canEnter) return false
      const status = statusOf(query.state.error)
      // 401은 로그인으로, 404는 아래에서 다시 등록하며 멈춘다. 그 밖의 일시 오류는 다음 간격에 다시 묻는다.
      return status === 401 || status === 404 ? false : ADMISSION_POLL_MS
    },
  })

  const lost = statusOf(position.error) === 404
  const attempts = useRef(0)
  const [exhausted, setExhausted] = useState(false)
  // 화면을 떠난 뒤에는 다시 등록하지 않는다
  const alive = useRef(true)
  useEffect(() => {
    alive.current = true
    return () => {
      alive.current = false
    }
  }, [])
  const reregister = () => {
    if (!alive.current) return
    attempts.current += 1
    void register.refetch().then(async (result) => {
      if (!alive.current) return
      const again = result.isSuccess ? await position.refetch() : undefined
      if (again?.isSuccess === true) return
      // 아직 줄에 서지 못했다. 횟수가 남았으면 다시 등록하고, 다 썼으면 failed로 알린다.
      if (attempts.current < MAX_REREGISTER) reregister()
      else setExhausted(true)
    })
  }
  useEffect(() => {
    if (!lost || attempts.current >= MAX_REREGISTER) return
    reregister()
    // register·position은 매 렌더 새 객체지만 refetch는 같은 쿼리를 가리킨다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lost])

  const skipped = register.isError && registerStatus !== 401 && !alreadyActive
  const status = position.data ?? register.data
  return {
    /** 이제 입장 확인(주문 서비스)을 물어도 되는지 */
    canEnter: alreadyActive || position.data?.canEnter === true,
    /** 줄을 서지 못한 등록 오류의 HTTP 상태(403 · 404 · 503 등). 아니면 undefined. 응답을 못 받은 오류는 0 */
    skippedStatus: skipped ? (registerStatus ?? 0) : undefined,
    /** 내 순번(1부터) · 내 앞 인원. 아직 모르면 undefined */
    rank: status && !status.canEnter ? status.myRank : undefined,
    ahead: status && !status.canEnter ? status.ahead : undefined,
    /** 다시 등록해도 줄에 서지 못해 기다릴 수 없음. retry()로 처음부터 다시 줄을 선다 */
    failed: lost && exhausted,
    retry: () => {
      attempts.current = 0
      setExhausted(false)
      reregister()
    },
    /** 로그인이 필요함(401)이면 true */
    unauthorized: registerStatus === 401 || statusOf(position.error) === 401,
  }
}
