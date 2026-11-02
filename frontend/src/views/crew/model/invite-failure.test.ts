import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'

import { inviteFailureOf } from './invite-failure'

const apiError = (status: number, code: string, message = '서버 문구') =>
  new ApiError(status, { status, code, message, data: null }, '/api/v1/member/crews/invites/x')

describe('inviteFailureOf', () => {
  it('24시간이 지난 링크는 기간 안내를 보여 준다', () => {
    expect(inviteFailureOf(apiError(410, 'CREW_INVITE_EXPIRED'))?.description).toBe(
      '링크는 만든 뒤 24시간 동안만 쓸 수 있어요. 크루장에게 새 링크를 받아 주세요.',
    )
  })

  it('취소된 링크는 같은 제목에 취소 안내를 보여 준다', () => {
    const failure = inviteFailureOf(apiError(410, 'CREW_INVITE_CANCELED'))
    expect(failure?.title).toBe('초대 링크가 끝났어요')
    expect(failure?.description).toBe(
      '크루장이 이 초대를 취소했어요. 크루장에게 새 링크를 받아 주세요.',
    )
  })

  it('이미 쓰인 링크와 없는 링크는 서버 문구를 보여 준다', () => {
    expect(inviteFailureOf(apiError(410, 'CREW_INVITE_ALREADY_USED'))?.description).toBe(
      '서버 문구',
    )
    expect(inviteFailureOf(apiError(404, 'CREW_INVITE_NOT_FOUND'))?.description).toBe('서버 문구')
  })

  it('서버 오류나 네트워크 오류는 링크 문제가 아니다', () => {
    expect(inviteFailureOf(apiError(500, 'INTERNAL_ERROR'))).toBeNull()
    expect(inviteFailureOf(new TypeError('Failed to fetch'))).toBeNull()
  })
})
