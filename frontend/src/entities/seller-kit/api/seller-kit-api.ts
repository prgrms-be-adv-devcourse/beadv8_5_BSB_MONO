import { ApiError, orderModel } from '@/shared/api'

/** 한 번에 받는 키트 주문 수 (API 최대 100) */
export const KIT_PAGE_SIZE = 100

export type KitFilters = {
  raceId?: number
  status?: orderModel.FindKitsStatus
}

export type KitShipment = { orderItemId: number; carrier: string; trackingNumber: string }

export type KitFailure = { orderItemId: number; error: unknown }

/** 서버가 준 문구. 없으면 (연결 끊김 등) 다시 시도하라는 말 */
export function kitErrorMessage(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}
