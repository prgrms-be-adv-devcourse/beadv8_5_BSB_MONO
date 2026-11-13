import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { productApi } from '@/shared/api'

import { errorSections, useRaceFormTracking } from './use-race-form-tracking'

describe('errorSections', () => {
  it('오류 칸을 화면의 항목 묶음 이름으로 바꾸고, 화면 순서대로 겹치지 않게 돌려준다', () => {
    const errors = {
      refundDeadline: { type: 'custom', message: '' },
      title: { type: 'too_small', message: '' },
      location: { type: 'too_small', message: '' },
    }
    expect(errorSections(errors, true)).toEqual(['기본 정보', '이미지', '취소 · 환불 정책'])
  })

  it('오류가 없으면 빈 목록', () => {
    expect(errorSections({}, false)).toEqual([])
  })
})

describe('useRaceFormTracking', () => {
  // 대회 관리·대시보드·판매 현황이 같은 키를 대회 배열로 읽는다(myRacesQueryOptions).
  // 등록 화면이 응답 전체를 넣으면, 등록 뒤 그 화면들의 목록이 깨진다.
  it('내 대회 목록 캐시에 다른 판매자 화면과 같은 대회 배열을 넣는다', async () => {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    const key = productApi.getFindMineQueryKey()

    renderHook(() => useRaceFormTracking('direct'), { wrapper })

    await waitFor(() => expect(client.getQueryState(key)?.status).toBe('success'))
    expect(Array.isArray(client.getQueryData(key))).toBe(true)
  })
})
