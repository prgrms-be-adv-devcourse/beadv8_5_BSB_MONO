import { addDays, format } from 'date-fns'
import { z } from 'zod'

import { parseRaceDateInput } from '@/entities/race'
import { productModel, productZod } from '@/shared/api'

export const RACE_FORM_ERRORS = {
  titleRequired: '대회 이름을 입력해 주세요.',
  titleMax: `대회 이름은 ${productZod.registerBodyTitleMax}자 이하로 입력해 주세요.`,
  locationRequired: '장소를 입력해 주세요.',
  locationMax: `장소는 ${productZod.registerBodyLocationMax}자 이하로 입력해 주세요.`,
  sidoRequired: '시·도를 입력해 주세요.',
  sidoMax: `시·도는 ${productZod.registerBodyRegionSidoMax}자 이하로 입력해 주세요.`,
  sigunguRequired: '시·군·구를 입력해 주세요.',
  sigunguMax: `시·군·구는 ${productZod.registerBodyRegionSigunguMax}자 이하로 입력해 주세요.`,
  // 달력 칸에서 날짜만 고른다. 시각은 보낼 때 RACE_DEFAULT_TIMES로 붙인다
  dateRequired: '날짜를 골라 주세요.',
  closeAfterOpen: '접수 마감일은 접수 시작일보다 빠를 수 없어요.',
  closeBeforeRace: '접수 마감은 대회 전날까지 정할 수 있어요.',
  refundBeforeRace: '환불 기한은 대회 날짜보다 늦을 수 없어요.',
  coursesRequired: '종목을 1개 이상 추가해 주세요.',
  coverRequired: '대표 이미지를 올려 주세요.',
} as const

/** 판매 시작일(접수 시작)은 오늘로부터 이 날 수 뒤부터 (서버 Race.SALE_OPEN_MIN_DAYS와 같다) */
export const SALE_OPEN_MIN_DAYS = 2

/** 접수 시작으로 고를 수 있는 가장 이른 날 */
export function earliestSaleOpenDate(today: Date): Date {
  return addDays(today, SALE_OPEN_MIN_DAYS)
}

export function saleOpenTooEarlyMessage(earliest: Date): string {
  return `접수 시작은 오늘로부터 이틀 뒤(${format(earliest, 'M/d')})부터 정할 수 있어요.`
}

/** 소개 이미지 최대 장수 (파일 서비스 DETAIL 용도의 개수 제한) */
export const MAX_INTRO_IMAGES = 5

/**
 * 크루 최소 인원. 세미는 크루 예매가 없어 쓰이지 않지만 API가 5 이상을 꼭 받는다.
 * 크루 예매(최종)를 열 때 입력 칸으로 바꾼다.
 */
const CREW_MIN_COUNT = productZod.registerBodyCrewMinCountMin

export type RaceCourseInput = Omit<productModel.RaceCourseSaveRequestDto, 'images'>

// 종목 한 줄은 orval이 만든 요청 스키마에서 이미지만 뺀 모양 (이미지는 등록할 때 올려서 붙인다)
const courseSchema = productZod.RegisterBody.shape.courses.element.omit({ images: true })

/**
 * 판매자는 날짜만 고르고, 서버(LocalDateTime)에는 아래 시각을 붙여 보낸다.
 * - 접수 시작: 그날 00:00부터 판다
 * - 접수 마감: 그날 23:59:59까지 받는다 (환불 기한 '그날 23:59:59까지'와 같은 하루 끝 기준)
 * - 대회: 시작 시각을 받지 않으므로 00:00으로 두고, 화면은 00:00을 날짜만으로 보여 준다
 */
export const RACE_DEFAULT_TIMES = {
  raceDate: '00:00:00',
  individualOpenAt: '00:00:00',
  applicationCloseAt: '23:59:59',
} as const

/** 날짜 칸('yyyy-MM-dd') */
const date = z
  .string()
  .refine((value) => parseRaceDateInput(value) !== null, RACE_FORM_ERRORS.dateRequired)

/**
 * 대회 등록 폼 규칙. 칸끼리의 순서는 서버(RaceRequestValidator · Race)와 같게 본다.
 * today를 주면 "판매 시작일은 오늘로부터 이틀 뒤부터" 규칙도 본다(화면은 오늘을 넘긴다).
 */
export function createRaceFormSchema({ today }: { today?: Date } = {}) {
  return z
    .object({
      title: z
        .string()
        .trim()
        .min(1, RACE_FORM_ERRORS.titleRequired)
        .max(productZod.registerBodyTitleMax, RACE_FORM_ERRORS.titleMax),
      location: z
        .string()
        .trim()
        .min(1, RACE_FORM_ERRORS.locationRequired)
        .max(productZod.registerBodyLocationMax, RACE_FORM_ERRORS.locationMax),
      regionSido: z
        .string()
        .trim()
        .min(1, RACE_FORM_ERRORS.sidoRequired)
        .max(productZod.registerBodyRegionSidoMax, RACE_FORM_ERRORS.sidoMax),
      regionSigungu: z
        .string()
        .trim()
        .min(1, RACE_FORM_ERRORS.sigunguRequired)
        .max(productZod.registerBodyRegionSigunguMax, RACE_FORM_ERRORS.sigunguMax),
      raceDate: date,
      individualOpenAt: date,
      applicationCloseAt: date,
      refundDeadline: date,
      courses: z.array(courseSchema).min(1, RACE_FORM_ERRORS.coursesRequired),
    })
    .superRefine((values, ctx) => {
      // 형식이 맞는 칸끼리만 순서를 본다. 'yyyy-MM-dd' 글자라 글자 비교가 곧 날짜 비교다
      const raceDate = parseRaceDateInput(values.raceDate)
      const openAt = parseRaceDateInput(values.individualOpenAt)
      const closeAt = parseRaceDateInput(values.applicationCloseAt)
      const refund = parseRaceDateInput(values.refundDeadline)
      // 같은 날이면 00:00 시작 ~ 23:59:59 마감이라 하루짜리 접수가 된다
      if (openAt && closeAt && closeAt < openAt) {
        ctx.addIssue({
          code: 'custom',
          path: ['applicationCloseAt'],
          message: RACE_FORM_ERRORS.closeAfterOpen,
        })
      }
      // 마감이 대회 날 23:59:59면 대회(00:00)보다 늦어 서버가 막는다. 그래서 대회 전날까지만 받는다
      if (closeAt && raceDate && closeAt >= raceDate) {
        ctx.addIssue({
          code: 'custom',
          path: ['applicationCloseAt'],
          message: RACE_FORM_ERRORS.closeBeforeRace,
        })
      }
      if (refund && raceDate && refund > raceDate) {
        ctx.addIssue({
          code: 'custom',
          path: ['refundDeadline'],
          message: RACE_FORM_ERRORS.refundBeforeRace,
        })
      }
      // 세미는 크루 예매가 없어 개인 예매 오픈(접수 시작) 날짜가 판매 시작일이다
      if (today && openAt) {
        const earliest = earliestSaleOpenDate(today)
        if (openAt < format(earliest, 'yyyy-MM-dd')) {
          ctx.addIssue({
            code: 'custom',
            path: ['individualOpenAt'],
            message: saleOpenTooEarlyMessage(earliest),
          })
        }
      }
    })
}

/** 오늘 규칙 없이 칸 형식과 순서만 보는 규칙 (타입과 테스트용) */
export const raceFormSchema = createRaceFormSchema()

export type RaceFormValues = z.input<typeof raceFormSchema>

export const COURSE_FORM_ERRORS = {
  nameRequired: '종목 이름을 입력해 주세요.',
  nameMax: `종목 이름은 ${productZod.registerBodyCoursesItemNameMax}자 이하로 입력해 주세요.`,
  courseTypeRequired: '종목 구분을 골라 주세요.',
  number: '숫자만 입력해 주세요.',
  distancePositive: '거리는 0보다 커야 해요.',
  timeLimitPositive: '제한 시간은 1분 이상이어야 해요.',
  quotaPositive: '정원은 1명 이상이어야 해요.',
  pricePositive: '참가비는 1원 이상이어야 해요.',
  minAge: `최소 나이는 만 ${productZod.registerBodyCoursesItemMinAgeMin}세 이상이어야 해요.`,
  maxAgeOrder: '최대 나이는 최소 나이보다 작을 수 없어요.',
} as const

export const COURSE_TYPE_LABELS: Record<RaceCourseInput['courseType'], string> = {
  KM_5: '5K',
  KM_10: '10K',
  HALF: '하프',
  FULL: '풀',
  ULTRA: '울트라',
}

/** 종목 추가 창의 입력값. 숫자 칸도 입력 그대로(글자) 받는다 */
export type CourseDraft = {
  name: string
  courseType: RaceCourseInput['courseType'] | ''
  distanceKm: string
  timeLimitMinutes: string
  price: string
  individualQuota: string
  minAge: string
  maxAge: string
}

export const COURSE_DRAFT_DEFAULTS: CourseDraft = {
  name: '',
  courseType: '',
  distanceKm: '',
  timeLimitMinutes: '',
  price: '',
  individualQuota: '',
  minAge: String(productZod.registerBodyCoursesItemMinAgeMin),
  maxAge: '',
}

const numberText = (pattern: RegExp) =>
  z.string().trim().regex(pattern, COURSE_FORM_ERRORS.number).transform(Number)
const wholeNumber = numberText(/^\d+$/)

const courseDraftSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, COURSE_FORM_ERRORS.nameRequired)
      .max(productZod.registerBodyCoursesItemNameMax, COURSE_FORM_ERRORS.nameMax),
    courseType: z.enum(['KM_5', 'KM_10', 'HALF', 'FULL', 'ULTRA'], {
      error: COURSE_FORM_ERRORS.courseTypeRequired,
    }),
    // 서버는 소수 둘째 자리까지 받는다
    distanceKm: numberText(/^\d+(\.\d{1,2})?$/).pipe(
      z.number().positive(COURSE_FORM_ERRORS.distancePositive),
    ),
    timeLimitMinutes: wholeNumber.pipe(z.number().min(1, COURSE_FORM_ERRORS.timeLimitPositive)),
    // 서버 RaceCourseSaveRequestDto가 @Positive라 0원은 막는다 (orval zod는 min(0)뿐)
    price: wholeNumber.pipe(z.number().min(1, COURSE_FORM_ERRORS.pricePositive)),
    individualQuota: wholeNumber.pipe(z.number().min(1, COURSE_FORM_ERRORS.quotaPositive)),
    minAge: wholeNumber.pipe(
      z.number().min(productZod.registerBodyCoursesItemMinAgeMin, COURSE_FORM_ERRORS.minAge),
    ),
    // 비우면 상한 없음
    maxAge: z.union([
      z
        .string()
        .trim()
        .length(0)
        .transform(() => undefined),
      wholeNumber,
    ]),
  })
  .superRefine((v, ctx) => {
    if (v.maxAge !== undefined && v.maxAge < v.minAge) {
      ctx.addIssue({ code: 'custom', path: ['maxAge'], message: COURSE_FORM_ERRORS.maxAgeOrder })
    }
  })

export type CourseDraftErrors = Partial<Record<keyof CourseDraft, string>>

/**
 * 종목 추가 창의 입력을 종목 한 줄로 바꾼다. 틀린 칸이 있으면 칸별 오류를 돌려준다.
 * 노면 · 키트는 세미 시안대로 도로 · 기념 티셔츠 포함으로 고정한다(키트 배송 표와 같다).
 */
export function toCourseInput(
  draft: CourseDraft,
): { course: RaceCourseInput } | { errors: CourseDraftErrors } {
  const parsed = courseDraftSchema.safeParse(draft)
  if (!parsed.success) {
    const errors: CourseDraftErrors = {}
    for (const issue of parsed.error.issues) {
      const key = issue.path[0] as keyof CourseDraft
      errors[key] ??= issue.message
    }
    return { errors }
  }
  return {
    course: {
      ...parsed.data,
      surface: 'ROAD',
      kitIncluded: true,
      kitPrice: 0,
      tshirtSizeOption: ['S', 'M', 'L', 'XL', 'XXL'],
      crewQuota: 0,
    },
  }
}

// 시안의 두 종목을 기본으로 두고, 판매자가 종목 추가 · 삭제로 바꾼다
const DRAFT_COURSES: RaceCourseInput[] = [
  {
    name: '10K',
    courseType: 'KM_10',
    surface: 'ROAD',
    distanceKm: 10,
    timeLimitMinutes: 90,
    price: 45000,
    kitIncluded: true,
    kitPrice: 0,
    tshirtSizeOption: ['S', 'M', 'L', 'XL', 'XXL'],
    minAge: 16,
    maxAge: 80,
    crewQuota: 0,
    individualQuota: 2000,
  },
  {
    name: '5K',
    courseType: 'KM_5',
    surface: 'ROAD',
    distanceKm: 5,
    timeLimitMinutes: 60,
    price: 35000,
    kitIncluded: true,
    kitPrice: 0,
    tshirtSizeOption: ['S', 'M', 'L', 'XL', 'XXL'],
    minAge: 14,
    maxAge: 80,
    crewQuota: 0,
    individualQuota: 1000,
  },
]

export const RACE_FORM_DEFAULTS: RaceFormValues = {
  title: '',
  location: '',
  regionSido: '',
  regionSigungu: '',
  raceDate: '',
  individualOpenAt: '',
  applicationCloseAt: '',
  refundDeadline: '',
  courses: DRAFT_COURSES,
}

/** 검사를 마친 입력과 종목별 이미지로 대회 등록 요청을 만든다. images는 courses와 같은 순서다. */
export function toRaceSaveRequest(
  values: RaceFormValues,
  images: productModel.RaceImageRequestDto[][],
): productModel.RaceSaveRequestDto {
  const at = (text: string, time: string) => `${parseRaceDateInput(text) ?? text}T${time}`
  return {
    title: values.title.trim(),
    location: values.location.trim(),
    regionSido: values.regionSido.trim(),
    regionSigungu: values.regionSigungu.trim(),
    raceDate: at(values.raceDate, RACE_DEFAULT_TIMES.raceDate),
    individualOpenAt: at(values.individualOpenAt, RACE_DEFAULT_TIMES.individualOpenAt),
    applicationCloseAt: at(values.applicationCloseAt, RACE_DEFAULT_TIMES.applicationCloseAt),
    // 환불 기한은 서버도 날짜(LocalDate)로 받는다
    refundDeadline: parseRaceDateInput(values.refundDeadline) ?? values.refundDeadline,
    crewMinCount: CREW_MIN_COUNT,
    // 세미는 크루 예매가 없어 크루 예매 기간을 비우고 크루 몫 정원은 0으로 보낸다
    courses: values.courses.map((course, i) => ({
      ...course,
      crewQuota: 0,
      images: images[i] ?? [],
    })),
  }
}
