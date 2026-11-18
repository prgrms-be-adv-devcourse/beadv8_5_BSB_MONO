import { z } from 'zod'

import { payoutZod } from '@/shared/api-zod'

// 청구 ID 찾기. 앞뒤 공백을 빼고 서버 조회 규칙(code)에 맞춘다. 빈 값은 검색을 지우는 뜻이라 통과한다.
export const claimSearchSchema = z
  .string()
  .trim()
  .pipe(payoutZod.AdminClaimsQueryParams.shape.code.unwrap())
