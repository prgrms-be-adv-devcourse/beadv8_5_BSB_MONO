import { z } from 'zod'

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']

/** 숫자만 있는 송장 번호는 네 자리씩 띄운다(6849 1201 3344). 글자가 섞이면 그대로 둔다 */
export function formatTrackingNumber(value?: string | null) {
  if (!value) return ''
  return /^\d+$/.test(value) ? value.replace(/(\d{4})(?=\d)/g, '$1 ') : value
}

/** 서버 시각(한국 시간, 시간대 없음)을 「11/16 (월)」로. 브라우저 시간대와 상관없이 글자에서 읽는다 */
export function formatMonthDay(value?: string | null) {
  const match = value?.match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return ''
  const [, y, m, d] = match.map(Number)
  const weekday = WEEKDAY[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]
  return `${m}/${d} (${weekday})`
}

/** 상세 창의 택배사 · 송장 번호. 송장 번호 규칙은 시안(2070:68912)의 「숫자 10~12자리」 */
export const detailFormSchema = z.object({
  carrier: z.string().min(1, '택배사를 골라 주세요'),
  trackingNumber: z
    .string()
    .trim()
    .regex(/^\d{10,12}$/, '송장 번호는 숫자 10~12자리예요'),
})

export type DetailFormValues = z.infer<typeof detailFormSchema>
