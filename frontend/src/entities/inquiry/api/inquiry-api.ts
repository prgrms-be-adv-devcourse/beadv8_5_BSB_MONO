import { ApiError } from '@/shared/api'

import type { InquiryAssignee, InquiryStatus } from '../model/types'

// 백엔드 inquiry-service ApiV1InquiryController (/api/v1/inquiry). 게이트웨이가 X-Member-Id를 넣는다.
export const BASE = '/api/v1/inquiry'
export const KEY = ['inquiry']

export type ManageRole = 'seller' | 'admin'
export type ManageFilters = { status?: InquiryStatus; assignee?: InquiryAssignee }

export const json = (method: string, body: unknown) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

/** 서버 문구가 있으면 그대로, 없으면 기본 문구 */
export function inquiryErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.serverMessage) return error.serverMessage
  return '요청을 처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.'
}
