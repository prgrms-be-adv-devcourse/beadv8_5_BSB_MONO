import { describe, expect, it } from 'vitest'

import { BIRTH_UNDER_14_MESSAGE } from '@/entities/member'

import { EMPTY_PARTICIPANT, participantSchema } from './participant-form'

const VALID = {
  ...EMPTY_PARTICIPANT,
  name: '김러너',
  birth: '1994-06-25',
  gender: 'MALE' as const,
  phone: '010-1234-5678',
  emergencyContactName: '박보호',
  emergencyContactPhone: '010-9876-5432',
  consent: true as const,
}

function birthError(birth: string) {
  const result = participantSchema.safeParse({ ...VALID, birth })
  return result.success
    ? undefined
    : result.error.issues.find((issue) => issue.path[0] === 'birth')?.message
}

describe('participantSchema 생년월일', () => {
  it('만 14세 이상이면 통과한다', () => {
    expect(birthError('1994-06-25')).toBeUndefined()
  })

  it('만 14세 미만이거나 미래 날짜면 가입과 같은 문구로 막는다', () => {
    expect(birthError('2999-01-01')).toBe(BIRTH_UNDER_14_MESSAGE)
    expect(birthError(`${new Date().getFullYear() - 5}-01-01`)).toBe(BIRTH_UNDER_14_MESSAGE)
  })

  it('없는 날짜면 막는다', () => {
    expect(birthError('2000-02-31')).toBeDefined()
  })
})
