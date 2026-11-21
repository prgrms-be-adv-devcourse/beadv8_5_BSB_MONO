import { z } from 'zod'

import { courseRowSchema, type RaceFormValues } from './race-form'

/** 임시 저장 키. 판매자(회원)마다 달라 같은 브라우저를 다른 계정이 써도 남의 작성 내용이 보이지 않는다 */
export const raceDraftKey = (memberId: number) => `seller-race-draft:${memberId}`

// 저장소의 값은 믿지 않고 모양을 확인한다. 빈 칸도 허용해야 해서 칸마다 글자만 본다
const text = z.string()
const draftSchema = z.object({
  title: text,
  description: text,
  location: text,
  locationDetail: text,
  regionSido: text,
  regionSigungu: text,
  raceDate: text,
  raceTime: text,
  crewOpen: z.boolean(),
  crewSaleStartAt: text,
  crewSaleEndAt: text,
  individualOpenAt: text,
  applicationCloseAt: text,
  refundDeadline: text,
  courses: z.array(courseRowSchema),
  kitOptions: z.array(z.object({ name: text, value: text, stock: text })),
})

const finite = (value: number) => (Number.isFinite(value) ? value : 0)

/**
 * 작성 중인 값을 저장한다. 이미지는 등록할 때 올리므로(올리기 전에는 파일뿐) 저장하지 않는다.
 * 저장소를 쓸 수 없어도(차단 · 용량 초과) 화면은 그대로 동작해야 해서 실패는 삼킨다.
 */
export function saveRaceDraft(memberId: number, values: RaceFormValues): void {
  try {
    const courses = values.courses.map((course) => ({
      ...course,
      crewQuota: finite(course.crewQuota),
      individualQuota: finite(course.individualQuota),
    }))
    localStorage.setItem(raceDraftKey(memberId), JSON.stringify({ ...values, courses }))
  } catch {
    // 저장소를 못 써도 작성은 계속할 수 있다
  }
}

/** 저장된 작성 내용. 없거나 깨졌거나 읽을 수 없으면 null */
export function loadRaceDraft(memberId: number): RaceFormValues | null {
  try {
    const raw = localStorage.getItem(raceDraftKey(memberId))
    if (raw === null) return null
    const parsed = draftSchema.safeParse(JSON.parse(raw))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

export function clearRaceDraft(memberId: number): void {
  try {
    localStorage.removeItem(raceDraftKey(memberId))
  } catch {
    // 지우지 못해도 다음에 열 때 다시 덮어쓴다
  }
}
