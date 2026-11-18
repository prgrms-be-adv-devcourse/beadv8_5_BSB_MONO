import { z } from 'zod'

/** 대회 검색어. 앞뒤 공백을 뺀다. 빈 검색어는 검색을 지우는 뜻이라 통과한다. */
export const searchTermSchema = z.string().trim()
