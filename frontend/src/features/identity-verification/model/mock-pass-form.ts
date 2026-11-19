import { z } from 'zod'

import { memberZod } from '@/shared/api-zod'

// 체험용 본인인증 창의 입력 규칙. 서버 스키마를 그대로 쓰고, 이름·휴대폰 번호의 앞뒤 공백만 뺀다.
export const mockPassFormSchema = memberZod.VerifyIdentityWithMockPassBody.extend({
  name: z.string().trim().min(1),
  phone: z.string().trim().regex(memberZod.verifyIdentityWithMockPassBodyPhoneRegExp),
})

export type MockPassFormValues = z.input<typeof mockPassFormSchema>
