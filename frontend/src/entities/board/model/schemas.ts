import { z } from 'zod'

import { COMMENT_MAX, CONTENT_MAX, TITLE_MAX } from '../api/types'

// 게시판은 생성된 명세에 없어 화면이 정한 제한(입력 칸 maxLength와 같은 값)을 그대로 쓴다.
// 오류 문구는 화면에 보이지 않는다. 비어 있으면 등록 버튼이 꺼져 있다.

export const postWriteSchema = z.object({
  category: z.enum(['FREE', 'RACE_REVIEW', 'QUESTION', 'RUN_TOGETHER']),
  title: z.string().trim().min(1).max(TITLE_MAX),
  content: z.string().trim().min(1).max(CONTENT_MAX),
})

export type PostWriteValues = z.input<typeof postWriteSchema>

export const commentSchema = z.string().trim().min(1).max(COMMENT_MAX)

/** 글 검색어. 앞뒤 공백을 뺀다. 빈 검색어는 검색을 지우는 뜻이라 허용한다. */
export const postSearchSchema = z.string().trim()
