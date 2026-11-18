import { z } from 'zod'

import { memberZod } from '@/shared/api-zod'

import { ADMIN_SELLER_ERRORS } from './admin-sellers-api'

// 반려 사유는 서버 스키마(500자 이하)에 「비어 있으면 안 됨」만 더한다. 앞뒤 공백은 뺀다.
export const rejectFormSchema = memberZod.RejectBody.extend({
  reason: z
    .string()
    .trim()
    .min(1, ADMIN_SELLER_ERRORS.reasonRequired)
    .max(memberZod.rejectBodyReasonMax),
})
