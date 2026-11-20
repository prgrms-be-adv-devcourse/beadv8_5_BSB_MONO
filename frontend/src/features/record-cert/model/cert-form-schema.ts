import { z } from 'zod'

import { netSeconds } from '../lib/cert-form'
import { COURSE_CLASSES } from './types'

/** P2 확인 칸. 넷타임 · 대회일 · 종목은 필수, 배번은 선택 (submit-spec) */
export const certFormSchema = z.object({
  netTime: z
    .string()
    .refine((v) => netSeconds(v) !== null, '넷타임을 시:분:초로 적어 주세요 (예: 1:30:05)'),
  raceDate: z.iso.date('대회일을 골라 주세요'),
  courseClass: z.enum(COURSE_CLASSES, '종목을 골라 주세요'),
  bib: z.string().trim().max(30, '배번은 30자까지 적을 수 있어요'),
})

export type CertFormInput = z.input<typeof certFormSchema>
