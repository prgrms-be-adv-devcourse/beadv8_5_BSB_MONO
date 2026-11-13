import { useMutation, useQuery } from '@tanstack/react-query'

import { customFetch, unwrapData, type RsData } from '@/shared/api'

import {
  axBriefingQueryOptions,
  chatPath,
  type AiAnswer,
  type AiScreen,
  type InquiryDraft,
} from './ai-assistant-api'

/** 질문 보내기: POST /api/v1/ai/{screen}/chat { message } → { answer, sources, list } */
export function useAiChat(screen: AiScreen) {
  return useMutation({
    mutationFn: async (message: string) =>
      unwrapData(
        await customFetch<RsData<AiAnswer>>(chatPath(screen), {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ message }),
        }),
      ),
  })
}

/** 새 대화: DELETE /api/v1/ai/{screen}/chat. 서버에 남은 내 대화 기록을 지운다 */
export function useAiClear(screen: AiScreen) {
  return useMutation({
    mutationFn: async () => customFetch<RsData<null>>(chatPath(screen), { method: 'DELETE' }),
  })
}

/** 대시보드 브리핑: GET /api/v1/ai/{screen}/briefing */
export function useAxBriefing(screen: AiScreen) {
  return useQuery(axBriefingQueryOptions(screen))
}

/** 문의 답변 초안: POST /api/v1/ai/{screen}/inquiries/{inquiryId}/draft */
export function useInquiryDraft(screen: AiScreen) {
  return useMutation({
    mutationFn: async (inquiryId: number | string) =>
      unwrapData(
        await customFetch<RsData<InquiryDraft>>(
          `/api/v1/ai/${screen}/inquiries/${inquiryId}/draft`,
          { method: 'POST' },
        ),
      ),
  })
}
