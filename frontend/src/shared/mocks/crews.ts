import { http, HttpResponse } from 'msw'

import {
  getCreateCrewInviteResponseMock,
  getFindCrewInviteResponseMock,
  getFindCrewInvitesResponseMock,
  getFindCrewResponseMock,
  getFindMyCrewsResponseMock,
  getJoinCrewResponseMock,
} from '@/shared/api/generated/member/member.msw'

import { freshMemberOf } from './mock-member'

/**
 * 크루 목. 명세 예시는 시각이 고정(2026-11-02)이라 초대 링크가 이미 끝난 것처럼 보인다.
 * 가짜 응답 모드에서 화면 상태를 다 볼 수 있게 시각은 지금 기준으로 바꾸고, 주소 값으로 실패 상태를 고른다.
 * - 크루 번호 403: 크루원이 아님(403 CREW_NOT_MEMBER), 404: 없는 크루, 5: 크루원 역할로 본 크루 홈
 * - 초대 토큰 expired · canceled · used: 쓸 수 없는 링크(410), invalid: 없는 링크(404), member: 이미 크루원
 * 방금 가입한 회원은 가입한 크루가 없다.
 * 생성된 핸들러보다 앞에 둬서 먼저 걸리게 한다.
 */

const HOUR_MS = 60 * 60 * 1000

/** 그 시각(ms)의 한국 시간 (백엔드처럼 시간대 없는 2026-11-02T20:10:00 모양) */
function toKstDateTime(ms: number): string {
  return new Date(ms + 9 * HOUR_MS).toISOString().slice(0, 19)
}

function crewError(status: number, code: string, message: string) {
  return HttpResponse.json({ status, code, message, data: null }, { status })
}

/** 크루 홈을 크루원 역할로 볼 때 쓰는 크루. 내 크루 목록 예시의 5번 크루와 같다. */
const MEMBER_VIEW_CREW_ID = 5

const INVITE_GONE: Record<string, { code: string; message: string }> = {
  expired: {
    code: 'CREW_INVITE_EXPIRED',
    message: '초대 링크의 유효 시간(24시간)이 지났습니다. 새 초대 링크를 받아주세요.',
  },
  canceled: {
    code: 'CREW_INVITE_CANCELED',
    message: '취소된 초대 링크입니다. 새 초대 링크를 받아주세요.',
  },
  used: {
    code: 'CREW_INVITE_ALREADY_USED',
    message: '이미 가입에 쓰인 초대 링크입니다. 새 초대 링크를 받아주세요.',
  },
}

function inviteFailure(token: string) {
  const gone = INVITE_GONE[token]
  if (gone) return crewError(410, gone.code, gone.message)
  if (token === 'invalid') {
    return crewError(404, 'CREW_INVITE_NOT_FOUND', '초대 링크를 찾을 수 없습니다.')
  }
  return undefined
}

function crewFailure(crewId: string) {
  if (crewId === '403') return crewError(403, 'CREW_NOT_MEMBER', '크루원이 아닙니다.')
  if (crewId === '404') return crewError(404, 'CREW_NOT_FOUND', '크루를 찾을 수 없습니다.')
  return undefined
}

export const crewHandlers = [
  http.get('*/api/v1/member/crews/me', ({ cookies }) => {
    if (!freshMemberOf(cookies)) return undefined
    return HttpResponse.json({ status: 200, message: '내 크루 목록을 조회했습니다.', data: [] })
  }),
  http.get('*/api/v1/member/crews/invites/:inviteToken', ({ params }) => {
    const token = String(params.inviteToken)
    const failure = inviteFailure(token)
    if (failure) return failure
    const example = getFindCrewInviteResponseMock()
    return HttpResponse.json({
      ...example,
      data: {
        ...example.data,
        expiresAt: toKstDateTime(Date.now() + 23 * HOUR_MS + 12 * 60 * 1000),
        alreadyMember: token === 'member',
      },
    })
  }),
  http.post('*/api/v1/member/crews/invites/:inviteToken/join', ({ params }) => {
    const token = String(params.inviteToken)
    const failure = inviteFailure(token)
    if (failure) return failure
    if (token === 'member') {
      return crewError(409, 'CREW_ALREADY_MEMBER', '이미 이 크루의 크루원입니다.')
    }
    const example = getJoinCrewResponseMock()
    return HttpResponse.json(
      { ...example, data: { ...example.data, joinedAt: toKstDateTime(Date.now()) } },
      { status: 201 },
    )
  }),
  http.get('*/api/v1/member/crews/:crewId', ({ params }) => {
    const crewId = String(params.crewId)
    const failure = crewFailure(crewId)
    if (failure) return failure
    if (Number(crewId) !== MEMBER_VIEW_CREW_ID) return undefined
    const example = getFindCrewResponseMock()
    const listed = getFindMyCrewsResponseMock().data?.find(
      (crew) => crew.crewId === MEMBER_VIEW_CREW_ID,
    )
    return HttpResponse.json({
      ...example,
      data: { ...example.data, crewId: MEMBER_VIEW_CREW_ID, name: listed?.name, myRole: 'MEMBER' },
    })
  }),
  http.post('*/api/v1/member/crews/:crewId/invites', ({ params }) => {
    const failure = crewFailure(String(params.crewId))
    if (failure) return failure
    const example = getCreateCrewInviteResponseMock()
    const now = Date.now()
    return HttpResponse.json(
      {
        ...example,
        data: {
          ...example.data,
          createdAt: toKstDateTime(now),
          expiresAt: toKstDateTime(now + 24 * HOUR_MS),
        },
      },
      { status: 201 },
    )
  }),
  http.get('*/api/v1/member/crews/:crewId/invites', ({ params }) => {
    const failure = crewFailure(String(params.crewId))
    if (failure) return failure
    const example = getFindCrewInvitesResponseMock()
    const createdAt = Date.now() - 3 * HOUR_MS
    return HttpResponse.json({
      ...example,
      data: example.data?.map((invite) => ({
        ...invite,
        createdAt: toKstDateTime(createdAt),
        expiresAt: toKstDateTime(createdAt + 24 * HOUR_MS),
      })),
    })
  }),
]
