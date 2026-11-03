import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import spec from '../../../../openapi/member.json'
import { CREW_ACTION_FAILURE, crewActionMessage } from './crew-action-failure'

const error = (status: number, code?: string) =>
  new ApiError(status, { status, code, message: '서버 문구', data: null }, '/api/v1/member/crews/1')

/** 계약에서 크루 관리 API(탈퇴 · 내보내기 · 크루장 넘기기 · 없애기)가 돌려주는 크루 오류 코드 */
function contractCodes(): string[] {
  const paths = spec.paths as Record<string, Record<string, { responses: object }>>
  const operations = [
    paths['/api/v1/member/crews/{crewId}/members/me'].delete,
    paths['/api/v1/member/crews/{crewId}/members/{memberId}'].delete,
    paths['/api/v1/member/crews/{crewId}/leader'].put,
    paths['/api/v1/member/crews/{crewId}'].delete,
  ]
  const codes = JSON.stringify(operations).match(/"CREW_[A-Z_]+"/g) ?? []
  return [...new Set(codes.map((code) => code.slice(1, -1)))]
}

describe('crewActionMessage', () => {
  it('계약의 크루 관리 오류 코드마다 보여 줄 문구가 있다', () => {
    const codes = contractCodes()
    expect(codes).toEqual(
      expect.arrayContaining([
        'CREW_LEADER_ONLY',
        'CREW_NOT_MEMBER',
        'CREW_NOT_FOUND',
        'CREW_MEMBER_NOT_FOUND',
        'CREW_CANNOT_REMOVE_LEADER',
        'CREW_ALREADY_LEADER',
        'CREW_HAS_OTHER_MEMBERS',
        'CREW_LEADER_CANNOT_LEAVE',
      ]),
    )
    for (const code of codes) expect(CREW_ACTION_FAILURE[code], code).toBeTruthy()
  })

  it('오류 코드에 맞는 문구를 고른다', () => {
    expect(crewActionMessage(error(403, 'CREW_LEADER_ONLY'))).toBe(
      CREW_ACTION_FAILURE.CREW_LEADER_ONLY,
    )
    expect(crewActionMessage(error(404, 'CREW_MEMBER_NOT_FOUND'))).toBe(
      CREW_ACTION_FAILURE.CREW_MEMBER_NOT_FOUND,
    )
  })

  it('모르는 코드 · 서버 오류 · 네트워크 오류는 다시 시도해 달라고 한다', () => {
    expect(crewActionMessage(error(503))).toBe('잠시 후 다시 시도해 주세요.')
    expect(crewActionMessage(error(400, 'SOMETHING_NEW'))).toBe('잠시 후 다시 시도해 주세요.')
    expect(crewActionMessage(new TypeError('Failed to fetch'))).toBe('잠시 후 다시 시도해 주세요.')
  })
})
