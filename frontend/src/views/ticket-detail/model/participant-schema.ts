import { z } from 'zod'

import type { orderModel } from '@/shared/api'

// 참가자 정보 수정 폼. 이름 · 생년월일 · 휴대폰은 회원 정보에서 바꾸므로 여기서는 고치지 않고 그대로 보낸다.
// 휴대폰 형식은 명세(ParticipantInfoRequestDto)의 패턴과 같다.

const PHONE = /^01[016789]-?\d{3,4}-?\d{4}$/

export const PARTICIPANT_ERRORS = {
  contactName: '비상 연락처 이름을 입력해 주세요.',
  phoneFormat: '휴대폰 번호를 010-1234-5678처럼 입력해 주세요.',
  address: '키트를 받을 주소를 입력해 주세요.',
  zipcodeFormat: '우편번호 5자리를 입력해 주세요.',
} as const

export const participantFormSchema = z.object({
  gender: z.enum(['MALE', 'FEMALE']),
  tshirtSize: z.string().max(10).optional(),
  emergencyContactName: z.string().trim().min(1, PARTICIPANT_ERRORS.contactName).max(50),
  emergencyContactPhone: z.string().trim().regex(PHONE, PARTICIPANT_ERRORS.phoneFormat),
  // 키트가 없는 종목은 빈 칸이다
  zipcode: z
    .string()
    .trim()
    .regex(/^(\d{5})?$/, PARTICIPANT_ERRORS.zipcodeFormat),
  address: z.string().trim().max(255),
  addressDetail: z.string().trim().max(255),
})

export type ParticipantFormValues = z.infer<typeof participantFormSchema>

export function toFormValues(participant: orderModel.ParticipantInfoDto): ParticipantFormValues {
  return {
    gender: participant.gender ?? 'MALE',
    tshirtSize: participant.tshirtSize ?? undefined,
    emergencyContactName: participant.emergencyContactName ?? '',
    emergencyContactPhone: participant.emergencyContactPhone ?? '',
    zipcode: participant.shippingAddress?.zipcode ?? '',
    address: participant.shippingAddress?.address ?? '',
    addressDetail: participant.shippingAddress?.addressDetail ?? '',
  }
}

/** 폼 값 + 고치지 않는 기존 값 → 수정 요청 본문 (PUT은 참가자 정보 전체를 보낸다) */
export function toRequestBody(
  participant: orderModel.ParticipantInfoDto,
  values: ParticipantFormValues,
): orderModel.ParticipantInfoRequestDto {
  const shipping = participant.shippingAddress
  return {
    name: participant.name ?? '',
    birth: participant.birth ?? '',
    phone: participant.phone ?? '',
    gender: values.gender,
    tshirtSize: values.tshirtSize || undefined,
    emergencyContactName: values.emergencyContactName,
    emergencyContactPhone: values.emergencyContactPhone,
    // 키트가 없는 종목(배송지 없음)이면 보내지 않는다
    shippingAddress: shipping
      ? {
          recipientName: shipping.recipientName ?? '',
          recipientPhone: shipping.recipientPhone ?? '',
          zipcode: values.zipcode,
          address: values.address,
          addressDetail: values.addressDetail || undefined,
        }
      : undefined,
  }
}
