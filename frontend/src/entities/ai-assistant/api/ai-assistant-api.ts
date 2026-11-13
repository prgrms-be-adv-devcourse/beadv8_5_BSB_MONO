import { queryOptions } from '@tanstack/react-query'

import { ApiError, customFetch, unwrapData, type RsData } from '@/shared/api'

/** 크루런 AI가 놓인 화면. 주소 /api/v1/ai/{screen}/... 에 그대로 들어간다 (ai-service ApiV1AiChatController) */
export type AiScreen = 'seller' | 'admin'

/** 답의 출처 한 줄. asOf가 없으면 조회에 실패한 것이라 화면은 버튼만 보여 준다 */
export type AiSource = {
  screen: string
  asOf: string | null
  buttonLabel: string | null
  path: string | null
}

/** 답 아래 목록 카드. moreCount는 카드에 담지 않은 나머지 건수 */
export type AiList = {
  rows: { orderNo: string; name: string; detail: string }[]
  moreCount: number
}

export type AiAnswer = { answer: string; sources?: AiSource[]; list?: AiList | null }

/** 서버가 준 문구. 없으면 (연결 끊김 등) 다시 시도하라는 말 */
export function aiErrorMessage(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

export const chatPath = (screen: AiScreen) => `/api/v1/ai/${screen}/chat`

/** 대시보드 AI 브리핑 한 줄. href는 눌러서 가는 화면 */
export type AxBriefingTodo = {
  title: string
  description: string
  /** 이상 거래 의심처럼 붙는 짧은 표시 */
  flag?: string
  linkLabel: string
  href: string
}

export type AxBriefingMetric = {
  label: string
  value: string
  note: string
  /** 증감 표시 (좋음 / 나쁨) */
  noteTone?: 'good' | 'bad'
}

export type AxBriefing = {
  /** 한 문장 요약 */
  headline: string
  /** 기준 시각 문구 (예: 오전 9:00 기준) */
  basis: string
  todos: AxBriefingTodo[]
  metrics: AxBriefingMetric[]
}

/** 대시보드 브리핑: GET /api/v1/ai/{screen}/briefing */
export function axBriefingQueryOptions(screen: AiScreen) {
  return queryOptions({
    queryKey: ['ai', screen, 'briefing'],
    queryFn: async ({ signal }) =>
      unwrapData(
        await customFetch<RsData<AxBriefing>>(`/api/v1/ai/${screen}/briefing`, { signal }),
      ),
  })
}

/** 문의 답변 초안. missing은 초안에 [확인 필요: …]로 비워 둔 곳의 안내 (title 예: 발송 날짜를 넣지 못했어요) */
export type InquiryDraft = {
  draft: string
  references: string[]
  missing: { title: string; reason: string }[]
}
