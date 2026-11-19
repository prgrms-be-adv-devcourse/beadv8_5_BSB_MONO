import { z } from 'zod'

import { ANSWER_MAX, BODY_MAX, TITLE_MAX } from './types'

// 문의 API가 생성 명세에 없어 서버 글자 수 제한(Inquiry.MAX_*_LENGTH)을 그대로 쓴다.
// 비어 있으면 보내기 버튼이 꺼져 있어서 오류 문구는 화면에 보이지 않는다.

export const inquiryFormSchema = z.object({
  title: z.string().trim().min(1).max(TITLE_MAX),
  body: z.string().trim().min(1).max(BODY_MAX),
})

export const answerSchema = z.string().trim().min(1).max(ANSWER_MAX)
