import { z } from 'zod'

import { BANKS, type BankCode } from '@/shared/config'

// 판매자 등록 신청(S01) 입력 규칙. 시안에 적힌 입력 칸·안내문에서 확실한 것만 검사한다.
// 길이 제한은 서버(SellerApplyRequestDto)와 같다: 상호 100자, 대표자·성명 50자, 계좌번호 숫자 6~16자리.

export const SELLER_APPLY_ERRORS = {
  businessNumberRequired: '사업자등록번호를 입력해 주세요.',
  businessNumberFormat: '사업자등록번호를 123-45-67890처럼 입력해 주세요.',
  businessNameRequired: '상호를 입력해 주세요.',
  businessNameTooLong: '상호는 100자 이하로 입력해 주세요.',
  representativeRequired: '대표자를 입력해 주세요.',
  representativeTooLong: '대표자는 50자 이하로 입력해 주세요.',
  nameRequired: '성명을 입력해 주세요.',
  nameTooLong: '성명은 50자 이하로 입력해 주세요.',
  phoneRequired: '연락처를 입력해 주세요.',
  agreedRequired: '판매자 이용약관에 동의해 주세요.',
  accountNumberRequired: '계좌번호를 입력해 주세요.',
  accountNumberFormat: '계좌번호는 숫자 6~16자리로 입력해 주세요.',
  businessHolderNotAllowed: '사업자 명의 계좌는 사업자만 고를 수 있어요.',
} as const

const BUSINESS_NUMBER = /^\d{3}-\d{2}-\d{5}$/
const ACCOUNT_NUMBER = /^\d{6,16}$/

/** 계좌번호는 하이픈 · 공백을 넣어 적어도 되고, 서버에는 숫자만 보낸다. */
export const accountDigits = (value: string) => value.replace(/[\s-]/g, '')

/** 화면 입력값. 정산 계좌는 신청 때 받아 서버가 오픈뱅킹으로 예금주를 확인한다. */
export type SellerApplyFormValues = {
  business: 'business' | 'personal'
  businessNumber: string
  businessName: string
  representative: string
  name: string
  phone: string
  bankCode: BankCode
  accountNumber: string
  // 본인 명의는 본인인증 이름과 예금주가 같아야 하고, 사업자 명의는 사업자만 고른다
  accountHolder: 'business' | 'personal'
  agreed: boolean
}

export const EMPTY_SELLER_APPLY_FORM: SellerApplyFormValues = {
  business: 'business',
  businessNumber: '',
  businessName: '',
  representative: '',
  name: '',
  phone: '',
  bankCode: '004',
  accountNumber: '',
  accountHolder: 'personal',
  agreed: false,
}

// 사업자면 사업자등록번호·상호·대표자를, 아니면 성명·연락처를 꼭 받는다.
export const sellerApplyFormSchema = z
  .object({
    business: z.enum(['business', 'personal']),
    businessNumber: z.string().trim(),
    businessName: z.string().trim(),
    representative: z.string().trim(),
    name: z.string().trim(),
    phone: z.string().trim(),
    bankCode: z.enum(BANKS.map((b) => b.value) as [BankCode, ...BankCode[]]),
    accountNumber: z.string().trim(),
    accountHolder: z.enum(['business', 'personal']),
    agreed: z.boolean().refine((v) => v, SELLER_APPLY_ERRORS.agreedRequired),
  })
  .superRefine((values, ctx) => {
    const need = (path: keyof SellerApplyFormValues, message: string) =>
      ctx.addIssue({ code: 'custom', path: [path], message })
    if (values.business === 'business') {
      if (!values.businessNumber) {
        need('businessNumber', SELLER_APPLY_ERRORS.businessNumberRequired)
      } else if (!BUSINESS_NUMBER.test(values.businessNumber)) {
        need('businessNumber', SELLER_APPLY_ERRORS.businessNumberFormat)
      }
      if (!values.businessName) {
        need('businessName', SELLER_APPLY_ERRORS.businessNameRequired)
      } else if (values.businessName.length > 100) {
        need('businessName', SELLER_APPLY_ERRORS.businessNameTooLong)
      }
      if (!values.representative) {
        need('representative', SELLER_APPLY_ERRORS.representativeRequired)
      } else if (values.representative.length > 50) {
        need('representative', SELLER_APPLY_ERRORS.representativeTooLong)
      }
    } else {
      if (!values.name) {
        need('name', SELLER_APPLY_ERRORS.nameRequired)
      } else if (values.name.length > 50) {
        need('name', SELLER_APPLY_ERRORS.nameTooLong)
      }
      if (!values.phone) {
        need('phone', SELLER_APPLY_ERRORS.phoneRequired)
      }
      if (values.accountHolder === 'business') {
        need('accountHolder', SELLER_APPLY_ERRORS.businessHolderNotAllowed)
      }
    }
    const account = accountDigits(values.accountNumber)
    if (!account) {
      need('accountNumber', SELLER_APPLY_ERRORS.accountNumberRequired)
    } else if (!ACCOUNT_NUMBER.test(account)) {
      need('accountNumber', SELLER_APPLY_ERRORS.accountNumberFormat)
    }
  }) satisfies z.ZodType<SellerApplyFormValues, SellerApplyFormValues>
