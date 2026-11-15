import { ApiError, type memberModel } from '@/shared/api'

import {
  accountDigits,
  EMPTY_SELLER_APPLY_FORM,
  type SellerApplyFormValues,
} from './seller-apply-form'

// 판매자 신청 API(PRO-129)와 화면 값을 이어 주는 곳. 오류 문구는 서버가 주는 code로 정한다.

export type SellerApplyStatus = 'pending' | 'rejected' | 'approved'

const STATUS_BY_SERVER: Record<memberModel.SellerDtoStatus, SellerApplyStatus> = {
  PENDING: 'pending',
  REJECTED: 'rejected',
  APPROVED: 'approved',
}

export function toApplyStatus(status: memberModel.SellerDtoStatus): SellerApplyStatus {
  return STATUS_BY_SERVER[status]
}

/**
 * 폼 값을 신청 요청으로 바꾼다.
 * 비사업자는 성명이 대표자 칸에 들어가고, 상호·사업자등록번호는 보내지 않는다.
 * 정산 계좌는 숫자만 남겨 보낸다. 연락처·약관 동의는 서버가 아직 받지 않는다.
 */
export function toApplyRequest(values: SellerApplyFormValues): memberModel.SellerApplyRequestDto {
  const account = {
    bankCode: values.bankCode,
    accountNumber: accountDigits(values.accountNumber),
    accountHolderType: values.accountHolder === 'business' ? 'BUSINESS' : 'PERSONAL',
  } as const
  if (values.business === 'business') {
    return {
      sellerType: 'BUSINESS',
      businessName: values.businessName,
      businessNumber: values.businessNumber,
      representativeName: values.representative,
      ...account,
    }
  }
  return { sellerType: 'INDIVIDUAL', representativeName: values.name, ...account }
}

/**
 * 내가 낸 신청 → 폼 값. 반려 뒤 다시 신청할 때 전에 적은 값을 채운다(시안 S01-1 메모).
 * 서버에 없는 연락처 · 약관 동의와 다시 확인해야 하는 정산 계좌는 빈 값으로 둔다.
 */
export function toFormValues(seller: memberModel.SellerDto): SellerApplyFormValues {
  if (seller.sellerType === 'BUSINESS') {
    return {
      ...EMPTY_SELLER_APPLY_FORM,
      business: 'business',
      businessNumber: seller.businessNumber ?? '',
      businessName: seller.businessName ?? '',
      representative: seller.representativeName,
    }
  }
  return { ...EMPTY_SELLER_APPLY_FORM, business: 'personal', name: seller.representativeName }
}

export const SELLER_API_ERRORS = {
  alreadyApplied: '이미 심사 중이거나 승인된 신청이 있어요. 신청 결과에서 확인해 주세요.',
  unavailable: '신청하지 못했어요. 잠시 후 다시 시도해 주세요.',
  loadFailed: '신청 상태를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
} as const

/**
 * 신청(POST) 실패 문구. 409 SELLER_ALREADY_APPLIED는 따로 안내하고, 그 밖에는 서버 문구를 그대로 쓴다.
 * 계좌 확인 실패(400 SELLER_ACCOUNT_NOT_VERIFIED, 429, 503)도 서버 문구 그대로다.
 */
export function applyErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'SELLER_ALREADY_APPLIED') return SELLER_API_ERRORS.alreadyApplied
    if (error.serverMessage) return error.serverMessage
  }
  return SELLER_API_ERRORS.unavailable
}
