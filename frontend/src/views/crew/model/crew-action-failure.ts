import { ApiError } from '@/shared/api'

const RETRY = '잠시 후 다시 시도해 주세요.'

// 크루 관리 API(탈퇴 · 내보내기 · 크루장 넘기기 · 없애기)의 오류 코드별 안내. 계약 openapi/member.json의 오류 예시와 하나씩 맞춘다.
// 대부분 다른 사람이 먼저 바꾼 뒤 오래된 화면에서 누른 경우라, 화면은 문구를 보여 준 뒤 크루 정보를 다시 불러온다.
export const CREW_ACTION_FAILURE: Record<string, string> = {
  CREW_LEADER_ONLY: '크루장만 할 수 있어요. 바뀐 크루 정보를 다시 불러왔어요.',
  CREW_NOT_MEMBER: '이 크루의 크루원이 아니에요.',
  CREW_NOT_FOUND: '없는 크루예요. 크루장이 크루를 없앴을 수 있어요.',
  CREW_MEMBER_NOT_FOUND: '이미 크루를 떠난 크루원이에요.',
  CREW_CANNOT_REMOVE_LEADER: '크루장은 내보낼 수 없어요.',
  CREW_ALREADY_LEADER: '이미 크루장이에요.',
  CREW_HAS_OTHER_MEMBERS:
    '다른 크루원이 있어 크루를 없앨 수 없어요. 크루장을 넘긴 뒤 크루를 떠나 주세요.',
  CREW_LEADER_CANNOT_LEAVE: '크루장을 넘긴 뒤에 나갈 수 있어요.',
}

/** 크루 관리 요청이 실패했을 때 보여 줄 문구. 모르는 코드 · 서버 오류 · 네트워크 오류는 다시 시도해 달라고 한다. */
export function crewActionMessage(error: unknown): string {
  return (error instanceof ApiError && error.code && CREW_ACTION_FAILURE[error.code]) || RETRY
}
