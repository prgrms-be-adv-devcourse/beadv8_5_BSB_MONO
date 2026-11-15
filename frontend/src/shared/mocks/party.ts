import { http, HttpResponse } from 'msw'

/**
 * 파티 목. 백엔드 파티 API(ApiV1PartyController 등)와 같은 경로 · 모양이다. 명세(openapi)에 아직 없어 손으로 쓴다.
 * 주소 값으로 상태를 고른다.
 * - 파티 번호 403: 파티원이 아님, 404: 없는 파티, 2: 파티원 입장, 3: 결제 진행 중, 4: 4명이 모임, 5: 마지막 남은 사람
 * - 참가 링크 토큰 expired · notfound · closed · full · late · other · joined · notmember · individual: 그 이유로 막힘
 * - 파티 번호 9로 신청하면 크루 전용 자리가 모자라다(409)
 */

const fail = (status: number, code: string, message: string, data: unknown = null) =>
  HttpResponse.json({ status, code, message, data }, { status })
const ok = (data: unknown, status = 200) =>
  HttpResponse.json({ status, message: '성공', data }, { status })

const FAR = '2099-01-01T00:00:00'
const member = (memberId: number, nickname: string, leader: boolean, paid = false) => ({
  memberId,
  nickname,
  leader,
  joinedAt: '2026-11-04T09:00:00',
  participationStatus: paid ? 'CONFIRMED' : 'PENDING',
  paid,
})

function partyDetail(id: number) {
  const base = {
    id,
    crewId: 7,
    raceId: 100,
    raceCourseId: 2,
    leaderMemberId: 1,
    status: 'RECRUITING',
    paymentDueAt: null,
    joinToken: id === 2 ? null : 'k3Jx9',
    joinTokenExpiresAt: FAR,
    memberCount: 3,
    paidCount: 0,
    members: [
      member(1, '달리는곰', true),
      member(2, '새벽러너', false),
      member(3, '한강러너', false),
    ],
  }
  if (id === 3) return { ...base, status: 'PAYING', paymentDueAt: FAR, paidCount: 1 }
  if (id === 4)
    return { ...base, members: [...base.members, member(4, '주말장거리', false)], memberCount: 4 }
  if (id === 5) return { ...base, memberCount: 1, members: [base.members[0]] }
  return base
}

const JOIN_FAILURES: Record<string, [number, string, string, unknown?]> = {
  expired: [409, 'ORDER_PARTY_LINK_EXPIRED', '참가 링크가 만료됐습니다.'],
  notfound: [404, 'ORDER_PARTY_NOT_FOUND', '참가 링크를 찾을 수 없습니다.'],
  closed: [409, 'ORDER_PARTY_NOT_RECRUITING', '모집 중인 파티가 아닙니다.'],
  full: [409, 'ORDER_PARTY_FULL', '파티 인원이 다 찼습니다.'],
  late: [409, 'ORDER_PARTY_MEMBER_JOINED_LATE', '크루 가입이 늦어 합류할 수 없습니다.'],
  other: [409, 'ORDER_PARTY_OTHER_PARTY_JOINED', '같은 대회에 다른 파티 신청이 있습니다.'],
  joined: [409, 'ORDER_PARTY_ALREADY_JOINED', '이미 합류한 파티입니다.'],
  notmember: [403, 'ORDER_CREW_MEMBER_REQUIRED', '크루원만 합류할 수 있습니다.'],
  individual: [
    409,
    'ORDER_INDIVIDUAL_PARTICIPATION_EXISTS',
    '개인 신청이 있습니다.',
    { participationId: 12 },
  ],
}

const participation = (partyId: number) => ({
  id: 31,
  raceId: 100,
  raceCourseId: 2,
  memberId: 2,
  participationType: 'PARTY',
  partyId,
  status: 'PENDING',
  thirdPartyConsent: true,
})

export const partyHandlers = [
  http.get('*/api/v1/order/parties/me/race-status', () =>
    ok({
      role: 'LEADER',
      canApply: true,
      partyId: 1,
      raceCourseId: 2,
      partyStatus: 'RECRUITING',
      memberCount: 3,
      minCount: 4,
      maxCount: 10,
      paidCount: 0,
      myPaid: false,
      participationId: 31,
    }),
  ),
  http.get('*/api/v1/order/parties/me', () => ok([])),
  http.get('*/api/v1/order/parties/crews/:crewId/active', () => ok([])),
  http.post('*/api/v1/order/parties/preview', async ({ request }) => {
    const { joinToken } = (await request.json()) as { joinToken: string }
    const failure = JOIN_FAILURES[joinToken]
    if (joinToken === 'expired' || joinToken === 'closed') {
      return ok({
        id: 1,
        crewId: 7,
        raceId: 100,
        raceCourseId: 2,
        status: joinToken === 'closed' ? 'PAYING' : 'RECRUITING',
        leaderNickname: '달리는곰',
        memberCount: 3,
        joinTokenExpiresAt: '2026-01-01T00:00:00',
        joinLinkAlive: joinToken !== 'expired',
      })
    }
    if (joinToken === 'notfound') return fail(...(failure as [number, string, string]))
    return ok({
      id: 1,
      crewId: 7,
      raceId: 100,
      raceCourseId: 2,
      status: 'RECRUITING',
      leaderNickname: '달리는곰',
      memberCount: 3,
      joinTokenExpiresAt: FAR,
      joinLinkAlive: true,
    })
  }),
  http.post('*/api/v1/order/parties/join', async ({ request }) => {
    const { joinToken } = (await request.json()) as { joinToken: string }
    const failure = JOIN_FAILURES[joinToken]
    if (failure) return fail(...(failure as [number, string, string, unknown]))
    return ok(participation(1), 201)
  }),
  http.post('*/api/v1/order/parties', () =>
    ok({ party: partyDetail(1), participation: participation(1) }, 201),
  ),
  http.post('*/api/v1/order/parties/:partyId/join-link', ({ params }) =>
    ok(partyDetail(Number(params.partyId))),
  ),
  http.post('*/api/v1/order/parties/:partyId/leave', ({ params }) =>
    ok(participation(Number(params.partyId))),
  ),
  http.post('*/api/v1/order/parties/:partyId/apply', ({ params }) =>
    Number(params.partyId) === 9
      ? fail(409, 'ORDER_PARTY_CREW_SEAT_SHORTAGE', '남은 크루 자리가 모자랍니다.', {
          partyId: 9,
          raceCourseId: 2,
          needed: 6,
          remaining: 4,
        })
      : ok({ ...partyDetail(Number(params.partyId)), status: 'PAYING' }),
  ),
  http.get('*/api/v1/order/parties/:partyId', ({ params }) => {
    const id = Number(params.partyId)
    if (id === 403) return fail(403, 'ORDER_PARTY_NOT_MEMBER', '이 파티의 파티원이 아닙니다.')
    if (id === 404) return fail(404, 'ORDER_PARTY_NOT_FOUND', '없는 파티입니다.')
    return ok(partyDetail(id))
  }),
  http.post('*/api/v1/queue/parties', () => ok({ myRank: 1, ahead: 0, total: 1, canEnter: true })),
  http.get('*/api/v1/queue/parties/races/:raceId/racecourses/:courseId', () =>
    ok({ myRank: 0, ahead: 0, total: 0, canEnter: true }),
  ),
]
