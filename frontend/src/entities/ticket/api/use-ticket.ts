import { useMutation, useQueryClient } from '@tanstack/react-query'

import { orderApi, orderModel, unwrapData } from '@/shared/api'

import { invalidateTicketQueries } from './ticket-api'

/**
 * 결제한 참가권 취소 (POST /api/v1/order/orders/{orderId}/items/{orderItemId}/cancel).
 * 환불 기한이 지났으면 409, 정산 서비스에 못 붙으면 503 → ticketErrorMessage로 문구를 보여 준다.
 */
export function useCancelTicket() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ orderId, orderItemId }: { orderId: number; orderItemId: number }) =>
      unwrapData(await orderApi.cancelItem(orderId, orderItemId)),
    onSuccess: () => invalidateTicketQueries(queryClient),
  })
}

/** 참가자 정보 수정 (PUT /api/v1/order/participations/{participationId}/participant). 접수 마감 뒤에는 409 */
export function useChangeParticipant() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({
      participationId,
      body,
    }: {
      participationId: number
      body: orderModel.ParticipantInfoRequestDto
    }) => unwrapData(await orderApi.changeParticipant(participationId, body)),
    onSuccess: () => invalidateTicketQueries(queryClient),
  })
}
