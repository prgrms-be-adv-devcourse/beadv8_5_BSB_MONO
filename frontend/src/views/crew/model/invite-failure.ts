import { ApiError } from '@/shared/api'

export type InviteFailure = { title: string; description: string }

const EXPIRED: InviteFailure = {
  title: '초대 링크가 끝났어요',
  description: '링크는 만든 뒤 24시간 동안만 쓸 수 있어요. 크루장에게 새 링크를 받아 주세요.',
}

/**
 * 쓸 수 없는 초대 링크의 안내 문구(시안 M10 「링크 끝남」). 오류 코드로 고른다(백엔드 결정 BE-028).
 * 취소된 링크 문구는 시안 노트에 있다. 이미 가입에 쓰인 링크 · 없는 링크는 시안 문구가 없어 서버 문구를 쓴다.
 * 쓸 수 없는 링크가 아니면(네트워크 오류 등) null.
 */
export function inviteFailureOf(error: unknown): InviteFailure | null {
  if (!(error instanceof ApiError)) return null
  switch (error.code) {
    case 'CREW_INVITE_EXPIRED':
      return EXPIRED
    case 'CREW_INVITE_CANCELED':
      return {
        title: EXPIRED.title,
        description: '크루장이 이 초대를 취소했어요. 크루장에게 새 링크를 받아 주세요.',
      }
    case 'CREW_INVITE_ALREADY_USED':
      return { title: EXPIRED.title, description: error.serverMessage ?? EXPIRED.description }
  }
  if (error.status === 404 || error.status === 410) {
    return { title: EXPIRED.title, description: error.serverMessage ?? EXPIRED.description }
  }
  return null
}
