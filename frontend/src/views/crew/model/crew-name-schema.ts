import { z } from 'zod'

/** 크루 이름 최대 길이. 서버는 앞뒤 공백을 뺀 1~20자를 받는다(백엔드 결정 BE-030). */
export const CREW_NAME_MAX = 20

// 20자를 넘는 입력은 입력칸 maxLength로 막는다. 글자 수 안내는 기능명세서 S-01이 정해지지 않아 쓰지 않는다.
export const crewNameSchema = z.object({
  name: z.string().trim().min(1, '크루 이름을 입력해 주세요').max(CREW_NAME_MAX),
})

export type CrewNameValues = z.input<typeof crewNameSchema>
