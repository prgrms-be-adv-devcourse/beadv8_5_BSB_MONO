export {
  clearAccessToken,
  getAuthStatus,
  onSessionExpired,
  setAccessToken,
  subscribeAuthStatus,
  type AuthStatus,
} from './access-token'
export { ApiError } from './api-error'
export { restoreSession } from './auth-session'
export { customFetch, type CustomFetchInit } from './fetcher'
export { createIdempotencyKey } from './idempotency'
export { useIdempotencyKey } from './use-idempotency-key'
export { getQueryClient } from './query-client'
export { unwrapData, type RsData } from './rs-data'

// orval로 만든 서비스별 요청 함수·훅(xxxApi), 타입(xxxModel), zod 스키마(xxxZod).
// 서비스마다 operationId가 겹쳐서(findById 등) 서비스 이름으로 묶는다.
export * as cashApi from './generated/cash/cash'
export * as cashModel from './generated/cash/cash.schemas'
export * as cashZod from './generated/cash/cash.zod'
export * as fileApi from './generated/file/file'
export * as fileModel from './generated/file/file.schemas'
export * as fileZod from './generated/file/file.zod'
export * as memberApi from './generated/member/member'
export * as memberModel from './generated/member/member.schemas'
export * as memberZod from './generated/member/member.zod'
export * as orderApi from './generated/order/order'
export * as orderModel from './generated/order/order.schemas'
export * as orderZod from './generated/order/order.zod'
export * as payoutApi from './generated/payout/payout'
export * as payoutModel from './generated/payout/payout.schemas'
export * as payoutZod from './generated/payout/payout.zod'
export * as productApi from './generated/product/product'
export * as productModel from './generated/product/product.schemas'
export * as productZod from './generated/product/product.zod'
export * as queueApi from './generated/queue/queue'
export * as queueModel from './generated/queue/queue.schemas'
export * as queueZod from './generated/queue/queue.zod'
