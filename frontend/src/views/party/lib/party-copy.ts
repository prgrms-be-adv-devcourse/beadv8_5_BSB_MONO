import type { PartyFailure } from '@/entities/party'

export type FailureCopy = { title: string; description: string; action: 'crew' | 'crews' | 'races' }

// 합류 · 만들기가 막힌 화면(피그마 파티 09~14 · 25~27)의 문구
export const JOIN_FAILURE: Record<Exclude<PartyFailure, 'INDIVIDUAL_EXISTS'>, FailureCopy> = {
  NOT_CREW_MEMBER: {
    title: '크루원만 합류할 수 있어요',
    description: '크루장에게 초대 링크를 받아 크루에 먼저 가입해 주세요.',
    action: 'crews',
  },
  LINK_EXPIRED: {
    title: '참가 링크 기간이 끝났어요',
    description: '파티장에게 새 링크를 받아 주세요.',
    action: 'crew',
  },
  LINK_NOT_FOUND: {
    title: '참가 링크를 찾을 수 없어요',
    description: '파티장에게 새 링크를 받아 주세요.',
    action: 'crews',
  },
  NOT_RECRUITING: {
    title: '모집이 끝난 파티예요',
    description: '크루 홈에서 진행 중인 다른 파티를 확인해 주세요.',
    action: 'crew',
  },
  ALREADY_JOINED: {
    title: '이미 합류한 파티예요',
    description: '크루 홈에서 내 파티를 확인해 주세요.',
    action: 'crew',
  },
  OTHER_PARTY: {
    title: '다른 파티로 신청했어요',
    description: '한 대회에는 한 번만 신청할 수 있어요. 그 파티에서 나간 뒤에 합류해 주세요.',
    action: 'crew',
  },
  FULL: {
    title: '파티 인원이 다 찼어요',
    description: '크루 홈에서 내 파티를 만들 수 있어요.',
    action: 'crew',
  },
  JOINED_LATE: {
    title: '크루 가입이 늦어 합류할 수 없어요',
    description: '파티 예매 시작 7일 전까지 크루에 가입한 크루원만 합류할 수 있어요.',
    action: 'crew',
  },
}

export const CREATE_JOINED_LATE: FailureCopy = {
  title: '크루 가입이 늦어 파티를 만들 수 없어요',
  description: '파티 예매 시작 7일 전까지 크루에 가입한 크루원만 파티를 만들 수 있어요.',
  action: 'races',
}

export const PARTY_STATUS_STEPS = ['모집 중', '결제 진행 중', '결제 끝'] as const
export const PARTY_STEP_INDEX = { RECRUITING: 0, PAYING: 1, PAID: 2, CLOSED: 0 } as const
