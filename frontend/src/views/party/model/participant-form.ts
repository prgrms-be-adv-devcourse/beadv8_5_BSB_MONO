import { z } from 'zod'

import type { orderModel } from '@/shared/api'

// 파티 만들기 · 합류의 내 참가 정보. 규칙은 서버(ParticipantInfoRequestDto)와 같다.
// 키트 옵션과 배송지는 결제 전 「키트 고르기」에서 받는 시안(11/9)이라 여기서 받지 않는다.

const PHONE = /^01[016789]-?\d{3,4}-?\d{4}$/
const phone = z.string().trim().regex(PHONE, '휴대폰 번호를 010-1234-5678처럼 입력해 주세요.')
const name = z
  .string()
  .trim()
  .min(1, '이름을 입력해 주세요.')
  .max(50, '이름은 50자까지 쓸 수 있어요.')

export const participantSchema = z.object({
  name,
  birth: z.string().min(1, '생년월일을 입력해 주세요.'),
  gender: z.enum(['MALE', 'FEMALE'], { message: '성별을 골라 주세요.' }),
  phone,
  emergencyContactName: name,
  emergencyContactPhone: phone,
  consent: z.literal(true, { message: '개인정보 제3자 제공에 동의해 주세요.' }),
})

export type ParticipantValues = z.input<typeof participantSchema>

export const EMPTY_PARTICIPANT: ParticipantValues = {
  name: '',
  birth: '',
  gender: undefined as unknown as 'MALE',
  phone: '',
  emergencyContactName: '',
  emergencyContactPhone: '',
  consent: false as unknown as true,
}

export function toParticipantRequest(
  values: z.output<typeof participantSchema>,
): orderModel.ParticipantInfoRequestDto {
  const rest: Partial<typeof values> = { ...values }
  delete rest.consent
  return rest as orderModel.ParticipantInfoRequestDto
}
