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
 * - 크루 관리(2그룹): 1번 크루는 크루원이 3명이라 없애기가 409, 6번 크루는 크루장 혼자라 없앨 수 있다.
 *   크루장(1 · 6번 크루)이 나가면 409, 크루장 자신(회원 1)을 내보내거나 넘기면 409,
 *   한강페이서(회원 4)를 내보내면 다른 사람이 먼저 내보낸 것처럼 404를 돌려준다.
 *   서버 메모리에 상태를 두지 않아 내보내거나 넘긴 뒤에도 목록은 그대로다.
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
/** 크루장 혼자인 크루. 크루 없애기를 끝까지 볼 때 쓴다. */
const SOLO_CREW_ID = 6
/** 크루 홈 예시에서 크루장인 회원 */
const LEADER_MEMBER_ID = 1
/** 내보내기를 누르면 이미 떠난 크루원(404)으로 답하는 회원 */
const ALREADY_GONE_MEMBER_ID = 4

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
    const example = getFindCrewResponseMock()
    if (Number(crewId) === SOLO_CREW_ID) {
      const leader = example.data!.members.filter((member) => member.role === 'LEADER')
      return HttpResponse.json({
        ...example,
        data: {
          ...example.data,
          crewId: SOLO_CREW_ID,
          name: '성수런',
          memberCount: 1,
          members: leader,
        },
      })
    }
    if (Number(crewId) !== MEMBER_VIEW_CREW_ID) return undefined
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
  // 「me」가 회원 번호 자리에 걸리지 않도록 내보내기보다 앞에 둔다
  http.delete('*/api/v1/member/crews/:crewId/members/me', ({ params }) => {
    const crewId = String(params.crewId)
    const failure = crewFailure(crewId)
    if (failure) return failure
    if (Number(crewId) !== MEMBER_VIEW_CREW_ID) {
      return crewError(
        409,
        'CREW_LEADER_CANNOT_LEAVE',
        '크루장은 크루장을 넘긴 뒤 탈퇴할 수 있습니다. 다른 크루원이 없으면 크루를 없애주세요.',
      )
    }
    return HttpResponse.json({ status: 200, message: '크루에서 탈퇴했습니다.', data: null })
  }),
  http.delete('*/api/v1/member/crews/:crewId/members/:memberId', ({ params }) => {
    const failure = crewFailure(String(params.crewId))
    if (failure) return failure
    const memberId = Number(params.memberId)
    if (memberId === LEADER_MEMBER_ID) {
      return crewError(409, 'CREW_CANNOT_REMOVE_LEADER', '크루장은 내보낼 수 없습니다.')
    }
    if (memberId === ALREADY_GONE_MEMBER_ID) {
      return crewError(404, 'CREW_MEMBER_NOT_FOUND', '이 크루의 크루원이 아닙니다.')
    }
    return HttpResponse.json({ status: 200, message: '크루원을 내보냈습니다.', data: null })
  }),
  http.put('*/api/v1/member/crews/:crewId/leader', async ({ params, request }) => {
    const failure = crewFailure(String(params.crewId))
    if (failure) return failure
    const { memberId } = (await request.json()) as { memberId: number }
    if (memberId === LEADER_MEMBER_ID) {
      return crewError(409, 'CREW_ALREADY_LEADER', '이미 크루장입니다.')
    }
    return undefined
  }),
  http.delete('*/api/v1/member/crews/:crewId', ({ params }) => {
    const crewId = String(params.crewId)
    const failure = crewFailure(crewId)
    if (failure) return failure
    if (Number(crewId) !== SOLO_CREW_ID) {
      return crewError(
        409,
        'CREW_HAS_OTHER_MEMBERS',
        '다른 크루원이 있어 크루를 없앨 수 없습니다. 크루장을 넘긴 뒤 탈퇴해주세요.',
      )
    }
    return HttpResponse.json({ status: 200, message: '크루를 없앴습니다.', data: null })
  }),
]
