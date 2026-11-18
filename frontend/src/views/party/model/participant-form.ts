import { z } from 'zod'

import type { orderModel } from '@/shared/api'
import { orderZod } from '@/shared/api-zod'

// 파티 만들기 · 합류의 내 참가 정보. 규칙은 서버(ParticipantInfoRequestDto)와 같다.
// 키트가 든 종목은 서버가 키트 배송지를 검사하므로 배송지를 함께 받는다(participantSchemaFor).
// 키트 옵션은 결제 전 「키트 고르기」에서 받는 시안(11/9)이라 여기서 받지 않는다.

const PHONE = /^01[016789]-?\d{3,4}-?\d{4}$/
const phone = z.string().trim().regex(PHONE, '휴대폰 번호를 010-1234-5678처럼 입력해 주세요.')
const name = z
  .string()
  .trim()
  .min(1, '이름을 입력해 주세요.')
  .max(50, '이름은 50자까지 쓸 수 있어요.')

const ADDRESS_ERRORS = {
  zipcode: '우편번호 5자리를 입력해 주세요.',
  required: '키트를 받을 주소를 입력해 주세요.',
  length: '주소가 너무 길어요.',
}

export const participantSchema = z.object({
  name,
  birth: z.string().min(1, '생년월일을 입력해 주세요.'),
  gender: z.enum(['MALE', 'FEMALE'], { message: '성별을 골라 주세요.' }),
  phone,
  emergencyContactName: name,
  emergencyContactPhone: phone,
  // 키트가 없는 종목은 비워 두고 보내지 않는다
  zipcode: z.string(),
  address: z.string(),
  addressDetail: z.string(),
  consent: z.literal(true, { message: '개인정보 제3자 제공에 동의해 주세요.' }),
})

/** 키트가 든 종목은 배송지를 꼭 받는다. 받는 사람은 보낼 때 참가자 본인으로 채운다. */
export function participantSchemaFor(kit: boolean) {
  if (!kit) return participantSchema
  return participantSchema.extend({
    zipcode: z
      .string()
      .trim()
      .regex(
        orderZod.createPartyBodyParticipantShippingAddressZipcodeRegExp,
        ADDRESS_ERRORS.zipcode,
      ),
    address: z
      .string()
      .trim()
      .min(1, ADDRESS_ERRORS.required)
      .max(orderZod.createPartyBodyParticipantShippingAddressAddressMax, ADDRESS_ERRORS.length),
    addressDetail: z
      .string()
      .trim()
      .max(
        orderZod.createPartyBodyParticipantShippingAddressAddressDetailMax,
        ADDRESS_ERRORS.length,
      ),
  })
}

export type ParticipantValues = z.input<typeof participantSchema>

export const EMPTY_PARTICIPANT: ParticipantValues = {
  name: '',
  birth: '',
  gender: undefined as unknown as 'MALE',
  phone: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  zipcode: '',
  address: '',
  addressDetail: '',
  consent: false as unknown as true,
}

export function toParticipantRequest(
  values: z.output<typeof participantSchema>,
  kit: boolean,
): orderModel.ParticipantInfoRequestDto {
  const { consent, zipcode, address, addressDetail, ...rest } = values
  void consent
  const detail = addressDetail.trim()
  return {
    ...rest,
    ...(kit
      ? {
          shippingAddress: {
            recipientName: rest.name,
            recipientPhone: rest.phone,
            zipcode: zipcode.trim(),
            address: address.trim(),
            ...(detail ? { addressDetail: detail } : {}),
          },
        }
      : {}),
  }
}
