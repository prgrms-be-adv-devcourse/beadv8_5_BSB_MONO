import { z } from 'zod'

/** 서버가 받는 질문 최대 글자 수 (AiChatRequestDto) */
export const QUESTION_MAX = 1000

export const questionSchema = z.string().trim().min(1).max(QUESTION_MAX)
