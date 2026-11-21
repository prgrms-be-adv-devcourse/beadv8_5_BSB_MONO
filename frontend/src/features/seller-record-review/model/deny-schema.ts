import { z } from 'zod'

import { DENY_REASONS } from './types'

// member RecordCertDenyRequestDto: 사유는 꼭 하나, 메모는 200자 이하 선택. 공백만 쓴 메모는 보내지 않는다
export const denySchema = z.object({
  reason: z.enum(DENY_REASONS, { error: '승인 불가 사유를 골라 주세요.' }),
  memo: z
    .string()
    .trim()
    .max(200, '메모는 200자 이하로 써 주세요.')
    .transform((memo) => memo || undefined),
})
