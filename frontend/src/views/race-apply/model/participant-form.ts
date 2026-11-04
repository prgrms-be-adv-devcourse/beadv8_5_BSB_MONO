import { z } from 'zod'

import type { BookingParticipant } from '@/features/booking'
import type { RaceOption } from '@/entities/race'
import { orderModel } from '@/shared/api'
import { orderZod } from '@/shared/api-zod'

// 참가자 정보 입력(M05). 입력 규칙은 orval이 만든 주문 API zod 스키마의 값(정규식·길이)을 그대로 쓰고,
// 화면에 보일 문구만 붙인다. 나이 제한·접수 기간처럼 서버만 아는 규칙은 서버 오류 문구로 보여 준다.

export const PARTICIPANT_ERRORS = {
  nameRequired: '이름을 입력해 주세요.',
  nameLength: `이름은 ${orderZod.applyBodyParticipantNameMax}자까지 쓸 수 있어요.`,
  birthRequired: '생년월일을 입력해 주세요.',
  genderRequired: '성별을 골라 주세요.',
  phoneFormat: '휴대폰 번호를 010-1234-5678처럼 입력해 주세요.',
  sizeRequired: '티셔츠 사이즈를 골라 주세요.',
  zipcodeFormat: '우편번호 5자리를 입력해 주세요.',
  addressRequired: '키트를 받을 주소를 입력해 주세요.',
  addressLength: '주소가 너무 길어요.',
  // 서버에 닿지 못했거나 서버 문구가 없을 때
  unavailable: '지금은 신청할 수 없어요. 잠시 후 다시 시도해 주세요.',
} as const

/** 화면 입력값. 입력 칸은 모두 글자로 받고, 보낼 때 API 모양으로 바꾼다. */
export type ParticipantFormValues = {
  name: string
  birth: string
  gender: string
  phone: string
  emergencyContactName: string
  emergencyContactPhone: string
  tshirtSize: string
  zipcode: string
  address: string
  addressDetail: string
}

const GENDERS: string[] = Object.values(orderModel.ParticipantInfoRequestDtoGender)

const phone = z
  .string()
  .trim()
  .regex(orderZod.applyBodyParticipantPhoneRegExp, PARTICIPANT_ERRORS.phoneFormat)

const name = z
  .string()
  .trim()
  .min(1, PARTICIPANT_ERRORS.nameRequired)
  .max(orderZod.applyBodyParticipantNameMax, PARTICIPANT_ERRORS.nameLength)

/** 종목에 따라 받는 칸이 다르다. 티셔츠가 있으면 사이즈를, 키트가 있으면 배송 주소를 꼭 받는다. */
export function participantFormSchema(option: Pick<RaceOption, 'kitIncluded' | 'shirtSizes'>) {
  const sizes = option.shirtSizes ?? []
  const kit = option.kitIncluded ?? false
  return z.object({
    name,
    birth: z.string().min(1, PARTICIPANT_ERRORS.birthRequired),
    gender: z.string().refine((v) => GENDERS.includes(v), PARTICIPANT_ERRORS.genderRequired),
    phone,
    emergencyContactName: name,
    emergencyContactPhone: phone,
    tshirtSize:
      sizes.length > 0
        ? z.string().refine((v) => sizes.includes(v), PARTICIPANT_ERRORS.sizeRequired)
        : z.string(),
    zipcode: kit
      ? z
          .string()
          .trim()
          .regex(
            orderZod.applyBodyParticipantShippingAddressZipcodeRegExp,
            PARTICIPANT_ERRORS.zipcodeFormat,
          )
      : z.string(),
    address: kit
      ? z
          .string()
          .trim()
          .min(1, PARTICIPANT_ERRORS.addressRequired)
          .max(
            orderZod.applyBodyParticipantShippingAddressAddressMax,
            PARTICIPANT_ERRORS.addressLength,
          )
      : z.string(),
    addressDetail: z
      .string()
      .trim()
      .max(
        orderZod.applyBodyParticipantShippingAddressAddressDetailMax,
        PARTICIPANT_ERRORS.addressLength,
      ),
  }) satisfies z.ZodType<ParticipantFormValues, ParticipantFormValues>
}

export const EMPTY_PARTICIPANT_FORM: ParticipantFormValues = {
  name: '',
  birth: '',
  gender: '',
  phone: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  tshirtSize: '',
  zipcode: '',
  address: '',
  addressDetail: '',
}

/** 지난 신청 값(서버) 또는 이번에 입력해 둔 값(스토어)으로 입력 칸을 채운다. */
export function toParticipantFormValues(
  participant: BookingParticipant | orderModel.ParticipantInfoDto,
  option: Pick<RaceOption, 'shirtSizes'>,
): ParticipantFormValues {
  const address = participant.shippingAddress
  const size = participant.tshirtSize ?? ''
  return {
    name: participant.name ?? '',
    birth: participant.birth ?? '',
    gender: participant.gender ?? '',
    phone: participant.phone ?? '',
    emergencyContactName: participant.emergencyContactName ?? '',
    emergencyContactPhone: participant.emergencyContactPhone ?? '',
    // 지난 대회에서 고른 사이즈가 이번 종목에 없으면 다시 고르게 한다
    tshirtSize: (option.shirtSizes ?? []).includes(size) ? size : '',
    zipcode: address?.zipcode ?? '',
    address: address?.address ?? '',
    addressDetail: address?.addressDetail ?? '',
  }
}

/**
 * 입력값을 주문 API의 참가자 정보로 바꾼다.
 * 티셔츠가 없는 종목은 사이즈를, 키트가 없는 종목은 배송지를 보내지 않는다.
 * 키트 받는 사람은 참가자 본인으로 둔다.
 */
export function toParticipantRequest(
  values: ParticipantFormValues,
  option: Pick<RaceOption, 'kitIncluded' | 'shirtSizes'>,
): BookingParticipant {
  const participantName = values.name.trim()
  const participantPhone = values.phone.trim()
  const detail = values.addressDetail.trim()
  return {
    name: participantName,
    birth: values.birth,
    gender: values.gender as orderModel.ParticipantInfoRequestDtoGender,
    phone: participantPhone,
    emergencyContactName: values.emergencyContactName.trim(),
    emergencyContactPhone: values.emergencyContactPhone.trim(),
    ...((option.shirtSizes ?? []).length > 0 ? { tshirtSize: values.tshirtSize } : {}),
    ...(option.kitIncluded
      ? {
          shippingAddress: {
            recipientName: participantName,
            recipientPhone: participantPhone,
            zipcode: values.zipcode.trim(),
            address: values.address.trim(),
            ...(detail ? { addressDetail: detail } : {}),
          },
        }
      : {}),
  }
}
