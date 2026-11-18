'use client'

import { useMutation } from '@tanstack/react-query'
import type { Route } from 'next'
import { useRouter } from 'next/navigation'

import { ApiError, queueApi, unwrapData } from '@/shared/api'

/**
 * 대회 상세의 신청하기. 대기열 등록을 요청하고, 서버가 바로 입장시키면(canEnter) 신청서로, 아니면 대기열 화면으로 간다.
 * 바로 입장할지는 서버가 정한다. 이미 입장한 회원(409)도 바로 입장으로 본다.
 * 등록이 실패하면 대기열 화면으로 보낸다. 그 화면이 오류 이유(로그인 · 접수 기간 등)를 안내한다.
 * 등록을 기다리는 동안 pending이 true이고, 화면은 그대로 둔다.
 */
export function useEnterQueue(raceId: string, optionId: string) {
  const router = useRouter()
  const query = `option=${encodeURIComponent(optionId)}`
  const register = useMutation({
    mutationFn: () => queueApi.register({ raceId: Number(raceId), raceCourseId: Number(optionId) }),
  })

  async function enter() {
    let canEnter = false
    // 숫자가 아닌 ID(시안 대회)는 등록하지 않고 대기열 화면이 안내하게 둔다
    const valid = Number.isInteger(Number(raceId)) && Number.isInteger(Number(optionId))
    try {
      if (valid) canEnter = unwrapData(await register.mutateAsync()).canEnter === true
    } catch (error) {
      canEnter = error instanceof ApiError && error.status === 409
    }
    router.push(
      (canEnter ? `/races/${raceId}/apply?${query}` : `/races/${raceId}/queue?${query}`) as Route,
    )
  }

  return { enter, pending: register.isPending }
}
