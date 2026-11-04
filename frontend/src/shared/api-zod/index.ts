// orval로 만든 서비스별 zod 스키마. 모든 화면이 받는 @/shared/api 묶음에 넣으면 zod(압축 후 약 90KB)가 같이 실려서 따로 둔다.
// 폼 검사가 있는 화면만 여기서 가져온다.
export * as cashZod from '../api/generated/cash/cash.zod'
export * as fileZod from '../api/generated/file/file.zod'
export * as memberZod from '../api/generated/member/member.zod'
export * as orderZod from '../api/generated/order/order.zod'
export * as payoutZod from '../api/generated/payout/payout.zod'
export * as productZod from '../api/generated/product/product.zod'
export * as queueZod from '../api/generated/queue/queue.zod'
