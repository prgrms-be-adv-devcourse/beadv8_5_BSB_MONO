import type { orderModel } from '@/shared/api'

/**
 * 내 참가권 = 결제한 주문 상품 하나. 주문 상품과 참가 신청은 1:1이다.
 * 대회 이름·날짜·사진은 대회 API(raceId)에서 따로 가져온다.
 */
export type MyTicket = {
  /** 화면 주소에 쓰는 ID (`주문ID-주문상품ID`) */
  id: string
  orderId: number
  orderItemId: number
  orderNo: string
  raceId: number
  raceCourseId: number
  participationId: number
  /** 주문 당시 상품명 (대회명 + 종목명) */
  itemName: string
  /** 결제 금액 (취소하면 돌려받는 금액) */
  amount: number
  /** PAID(결제 완료) 또는 CANCELED(취소). 결제 확인 중 주문이면 ORDERED */
  itemStatus: orderModel.OrderItemDtoStatus
  /** 주문이 결제 확인 중(PAYING)이다. 결과가 나오기 전이라 취소·수정할 수 없다 */
  paying: boolean
}
