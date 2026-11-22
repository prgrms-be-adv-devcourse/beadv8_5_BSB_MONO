import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import { orderApi, unwrapData } from '@/shared/api'
import { orderMock, server } from '@/shared/testing'

import {
  myTicketCountQueryOptions,
  myTicketsQueryOptions,
  orderQueryOptions,
  participationQueryOptions,
} from './ticket-api'

// 결제 · 완료 화면(orderApi.useFindById1)과 참가권 상세(orderQueryOptions)는 같은 주문 캐시를 쓴다.
// 결제 직후 참가권을 열 때 모양이 다르면 주문 상품을 못 읽어 "참가권을 찾을 수 없어요"가 나온다.
describe('orderQueryOptions', () => {
  it('결제 · 완료 화면이 받아 둔 주문 캐시에서도 주문 상품을 읽는다', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
    })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    const complete = renderHook(() => orderApi.useFindById1(1, { query: { select: unwrapData } }), {
      wrapper,
    })
    await waitFor(() => expect(complete.result.current.data?.items?.length).toBeGreaterThan(0))
    complete.unmount()

    const ticket = renderHook(() => useQuery(orderQueryOptions(1)), { wrapper })
    await waitFor(() => expect(ticket.result.current.data?.items?.length).toBeGreaterThan(0))
    expect(ticket.result.current.isError).toBe(false)
  })
})

// 참가 신청도 결제 · 완료 화면(orderApi.useFindById)과 같은 캐시 키를 쓴다. 모양이 다르면 결제 직후 참가자 정보가 빈다.
describe('participationQueryOptions', () => {
  it('결제 · 완료 화면이 받아 둔 참가 신청 캐시에서도 참가자를 읽는다', async () => {
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false, staleTime: 60_000 } },
    })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    const complete = renderHook(() => orderApi.useFindById(1, { query: { select: unwrapData } }), {
      wrapper,
    })
    await waitFor(() => expect(complete.result.current.data?.participant?.name).toBeTruthy())
    complete.unmount()

    const ticket = renderHook(() => useQuery(participationQueryOptions(1)), { wrapper })
    await waitFor(() => expect(ticket.result.current.data?.participant?.name).toBeTruthy())
  })
})

// 결제 대기 주문은 참가권 목록에는 나오지만 마이 화면의 참가 대회 수에는 들어가지 않는다.
describe('결제 대기 주문', () => {
  it('목록에는 결제 기한과 함께 나오고 참가 대회 수에는 들어가지 않는다', async () => {
    server.use(
      http.get('*/api/v1/order/orders', () =>
        HttpResponse.json({
          status: 200,
          message: '주문 내역을 조회했습니다.',
          data: {
            orders: [
              { id: 3, status: 'CREATED', paymentDueAt: '2999-01-01T00:00:00' },
              { id: 1, status: 'PAID' },
            ],
            page: 0,
            size: 100,
            totalElements: 2,
            totalPages: 1,
          },
        }),
      ),
      http.get('*/api/v1/order/orders/:orderId', ({ params }) => {
        const example = orderMock.getFindById1ResponseMock()
        if (params.orderId !== '3') return HttpResponse.json(example)
        return HttpResponse.json({
          ...example,
          data: {
            ...example.data,
            id: 3,
            status: 'CREATED',
            paymentDueAt: '2999-01-01T00:00:00',
            items: example.data?.items?.map((item) => ({ ...item, id: 5, status: 'ORDERED' })),
          },
        })
      }),
    )
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
    const list = renderHook(() => useQuery(myTicketsQueryOptions()), { wrapper })
    await waitFor(() => expect(list.result.current.data).toHaveLength(2))
    expect(list.result.current.data?.find((t) => t.orderId === 3)?.dueAt).toBe(
      '2999-01-01T00:00:00',
    )

    const count = renderHook(() => useQuery(myTicketCountQueryOptions(true)), { wrapper })
    await waitFor(() => expect(count.result.current.data).toBe(1))
  })
})
