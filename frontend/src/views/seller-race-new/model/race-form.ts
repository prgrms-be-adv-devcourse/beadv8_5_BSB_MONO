import { format } from 'date-fns'
import { z } from 'zod'

import {
  checkRefundAfterClose,
  checkScheduleOrder,
  earliestSaleOpenDate,
  parseDateTimeInput,
  parseRaceDateInput,
  raceAfterCloseMessage,
  refundAfterCloseMessage,
  SALE_OPEN_MIN_DAYS,
  saleOpenTooEarlyMessage,
  SCHEDULE_ERRORS,
  TIME_INPUT,
  toLocalDateTime,
} from '@/entities/race'
import { productModel } from '@/shared/api'
import { productZod } from '@/shared/api-zod'

export const RACE_FORM_ERRORS = {
  titleRequired: '대회 이름을 입력해 주세요.',
  titleMax: `대회 이름은 ${productZod.registerBodyTitleMax}자 이하로 입력해 주세요.`,
  locationDetailMax: '상세 주소는 100자 이하로 입력해 주세요.',
  descriptionMax: '대회 소개는 2,000자 이하로 입력해 주세요.',
  locationRequired: '장소를 입력해 주세요.',
  locationMax: `장소는 ${productZod.registerBodyLocationMax}자 이하로 입력해 주세요.`,
  sidoRequired: '시·도를 입력해 주세요.',
  sidoMax: `시·도는 ${productZod.registerBodyRegionSidoMax}자 이하로 입력해 주세요.`,
  sigunguRequired: '시·군·구를 입력해 주세요.',
  sigunguMax: `시·군·구는 ${productZod.registerBodyRegionSigunguMax}자 이하로 입력해 주세요.`,
  dateRequired: '날짜를 골라 주세요.',
  timeRequired: '출발 시각을 입력해 주세요.',
  ...SCHEDULE_ERRORS,
  crewMaxCount: `크루 최대 인원은 ${productZod.registerBodyCrewMinCountMin}명 이상의 숫자로 입력해 주세요.`,
  refundBeforeRace: '환불 기한은 대회 날짜보다 늦을 수 없어요.',
  coursesRequired: '종목을 1개 이상 추가해 주세요.',
  coverRequired: '대표 이미지를 올려 주세요.',
} as const

export {
  earliestSaleOpenDate,
  raceAfterCloseMessage,
  refundAfterCloseMessage,
  SALE_OPEN_MIN_DAYS,
  saleOpenTooEarlyMessage,
}

/** 소개 이미지 최대 장수 (파일 서비스 DETAIL 용도의 개수 제한) */
export const MAX_INTRO_IMAGES = 5

/**
 * 크루 최소 인원. 세미는 크루 예매가 없어 쓰이지 않지만 API가 4 이상을 꼭 받는다.
 * 크루 예매(최종)를 열 때 입력 칸으로 바꾼다.
 */
const CREW_MIN_COUNT = productZod.registerBodyCrewMinCountMin

export const COURSE_FORM_ERRORS = {
  nameRequired: '종목 이름을 입력해 주세요.',
  nameMax: `종목 이름은 ${productZod.registerBodyCoursesItemNameMax}자 이하로 입력해 주세요.`,
  courseTypeRequired: '종목 구분을 골라 주세요.',
  number: '숫자만 입력해 주세요.',
  quotaNumber: '0 이상의 숫자로 입력해 주세요.',
  timeLimitPositive: '제한 시간은 1분 이상이어야 해요.',
  quotaPositive: '정원은 1명 이상이어야 해요.',
  quotaSum: '크루 정원과 일반 정원을 합쳐 1명 이상이어야 해요.',
  pricePositive: '참가비는 1원 이상이어야 해요.',
} as const

/** 종목 나이는 이 두 값뿐이다 (서버 검사와 같다) */
export const NO_LIMIT_MIN_AGE = 14
export const ADULT_MIN_AGE = 19

const quota = z
  .number({ error: COURSE_FORM_ERRORS.quotaNumber })
  .int(COURSE_FORM_ERRORS.quotaNumber)
  .min(0, COURSE_FORM_ERRORS.quotaNumber)

// 종목 한 줄은 orval이 만든 요청 스키마에서 이미지만 빼고, 정원 · 나이의 안내 문구와 값을 좁힌 모양 (이미지는 등록할 때 올려서 붙인다)
export const courseRowSchema = productZod.RegisterBody.shape.courses.element
  .omit({ images: true })
  .extend({
    minAge: z.union([z.literal(NO_LIMIT_MIN_AGE), z.literal(ADULT_MIN_AGE)]),
    crewQuota: quota,
    individualQuota: quota,
  })

export type RaceCourseInput = z.input<typeof courseRowSchema>

/** 날짜 칸('yyyy-MM-dd') */
const date = z
  .string()
  .refine((value) => parseRaceDateInput(value) !== null, RACE_FORM_ERRORS.dateRequired)

export { parseDateTimeInput }

const dateTime = z
  .string()
  .refine((value) => parseDateTimeInput(value) !== null, RACE_FORM_ERRORS.dateTimeRequired)

export const KIT_OPTION_ERRORS = {
  nameRequired: '옵션 이름을 입력해 주세요.',
  nameMax: `옵션 이름은 ${productZod.registerKitOptionsBodyKitOptionsItemNameMax}자 이하로 입력해 주세요.`,
  valueRequired: '옵션 값을 입력해 주세요.',
  valueMax: `옵션 값은 ${productZod.registerKitOptionsBodyKitOptionsItemValueMax}자 이하로 입력해 주세요.`,
  stockNumber: '준비 수량은 0 이상의 숫자로 입력해 주세요. 비우면 맞춤 제작이에요.',
  duplicate: '같은 옵션을 두 번 넣을 수 없어요.',
  nameCase: '같은 옵션 이름은 대소문자까지 똑같이 써 주세요.',
} as const

/** 키트 옵션 한 줄의 입력값. 준비 수량도 입력 그대로(글자) 받고, 비우면 맞춤 제작이다 */
export type KitOptionInput = { name: string; value: string; stock: string }

/**
 * 키트 옵션 목록 규칙. 대회를 먼저 등록한 뒤 옵션을 따로 보내므로, 서버(RaceRequestValidator.validateKitOptions)가
 * 400으로 막을 입력은 여기서 먼저 막는다. 그래야 옵션 없는 대회만 남는 일이 줄어든다.
 */
const kitOptionsSchema = z
  .array(
    z.object({
      name: z
        .string()
        .trim()
        .min(1, KIT_OPTION_ERRORS.nameRequired)
        .max(productZod.registerKitOptionsBodyKitOptionsItemNameMax, KIT_OPTION_ERRORS.nameMax),
      value: z
        .string()
        .trim()
        .min(1, KIT_OPTION_ERRORS.valueRequired)
        .max(productZod.registerKitOptionsBodyKitOptionsItemValueMax, KIT_OPTION_ERRORS.valueMax),
      // 서버는 int라 9자리까지만 받는다
      stock: z
        .string()
        .trim()
        .regex(/^\d{0,9}$/, KIT_OPTION_ERRORS.stockNumber),
    }),
  )
  .superRefine((options, ctx) => {
    // 서버 KitOption.Key와 같이 앞뒤 공백을 지우고 대소문자를 무시해 비교한다
    const seen = new Set<string>()
    const nameOf = new Map<string, string>()
    options.forEach((option, i) => {
      const name = option.name.trim()
      const key = JSON.stringify([name.toLowerCase(), option.value.trim().toLowerCase()])
      if (seen.has(key)) {
        ctx.addIssue({ code: 'custom', path: [i, 'value'], message: KIT_OPTION_ERRORS.duplicate })
        return
      }
      seen.add(key)
      const first = nameOf.get(name.toLowerCase())
      if (first !== undefined && first !== name) {
        ctx.addIssue({ code: 'custom', path: [i, 'name'], message: KIT_OPTION_ERRORS.nameCase })
      }
      if (first === undefined) nameOf.set(name.toLowerCase(), name)
    })
  })

/** 키트 옵션 등록 · 수정 요청. 준비 수량을 비우면 null(맞춤 제작)로 보낸다 */
export function toKitOptionsRequest(
  options: KitOptionInput[],
): productModel.KitOptionsSaveRequestDto {
  return {
    kitOptions: options.map((option) => ({
      name: option.name.trim(),
      value: option.value.trim(),
      stock: option.stock.trim() === '' ? null : Number(option.stock),
    })),
  }
}

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
      locationDetail: z.string().trim().max(100, RACE_FORM_ERRORS.locationDetailMax),
      description: z.string().trim().max(2000, RACE_FORM_ERRORS.descriptionMax),
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
      raceTime: z.string().refine((value) => TIME_INPUT.test(value), RACE_FORM_ERRORS.timeRequired),
      // 크루 선예매를 열 때만 아래 크루 예매 칸을 본다. 열지 않으면 서버에는 기간 없이, 크루 정원 0으로 보낸다
      crewOpen: z.boolean(),
      crewSaleStartAt: z.string(),
      crewSaleEndAt: z.string(),
      // 비우면 상한 없음. 값이 있으면 서버처럼 크루 최소 인원 이상이어야 한다
      crewMaxCount: z.string(),
      individualOpenAt: dateTime,
      applicationCloseAt: dateTime,
      refundDeadline: date,
      courses: z.array(courseRowSchema).min(1, RACE_FORM_ERRORS.coursesRequired),
      kitOptions: kitOptionsSchema,
    })
    .superRefine((values, ctx) => {
      const issue = (path: (string | number)[], message: string) =>
        ctx.addIssue({ code: 'custom', path, message })
      // 형식이 맞는 칸끼리만 순서를 본다. 'yyyy-MM-dd HH:mm' 글자라 글자 비교가 곧 시각 비교다
      const raceDate = parseRaceDateInput(values.raceDate)
      const raceAt =
        raceDate && TIME_INPUT.test(values.raceTime) ? `${raceDate} ${values.raceTime}` : null
      const openAt = parseDateTimeInput(values.individualOpenAt)
      const refund = parseRaceDateInput(values.refundDeadline)
      const crewStart = values.crewOpen ? parseDateTimeInput(values.crewSaleStartAt) : null
      checkScheduleOrder(values, raceAt, (path, message) => issue([path], message))
      const max = values.crewMaxCount.trim()
      if (values.crewOpen && max !== '' && !(/^\d+$/.test(max) && Number(max) >= CREW_MIN_COUNT)) {
        issue(['crewMaxCount'], RACE_FORM_ERRORS.crewMaxCount)
      }
      if (refund && raceDate && refund > raceDate) {
        issue(['refundDeadline'], RACE_FORM_ERRORS.refundBeforeRace)
      }
      checkRefundAfterClose(refund, parseDateTimeInput(values.applicationCloseAt), (message) =>
        issue(['refundDeadline'], message),
      )
      // 판매 시작일은 크루 예매가 있으면 크루 예매 시작일, 없으면 일반 판매 시작일
      if (today) {
        const earliest = earliestSaleOpenDate(today)
        const first = values.crewOpen ? crewStart : openAt
        if (first && first.slice(0, 10) < format(earliest, 'yyyy-MM-dd')) {
          issue(
            [values.crewOpen ? 'crewSaleStartAt' : 'individualOpenAt'],
            saleOpenTooEarlyMessage(earliest),
          )
        }
      }
      // 서버 validateCourse: 크루 예매가 없으면 크루 정원은 0으로 보내므로 합에 넣지 않는다
      values.courses.forEach((course, i) => {
        const crew = values.crewOpen ? course.crewQuota : 0
        const total = crew + course.individualQuota
        if (Number.isFinite(total) && total < 1) {
          issue(['courses', i, 'individualQuota'], COURSE_FORM_ERRORS.quotaSum)
        }
      })
    })
}

/** 오늘 규칙 없이 칸 형식과 순서만 보는 규칙 (타입과 테스트용) */
export const raceFormSchema = createRaceFormSchema()

export type RaceFormValues = z.input<typeof raceFormSchema>

/**
 * 종목 구분이 거리를 정한다. 서버 CourseType에는 거리 값이 없고 요청에는 distanceKm이 따로 있어서 여기서 정해 보낸다.
 * 하프 21.0975km · 풀 42.195km는 서버가 소수 둘째 자리까지만 받아 반올림한다. 울트라는 새로 등록하지 않는다(저장된 대회는 서버가 그대로 둔다).
 */
export const COURSE_DISTANCE_KM = { KM_5: 5, KM_10: 10, HALF: 21.1, FULL: 42.2 } as const
export type SellableCourseType = keyof typeof COURSE_DISTANCE_KM

export const COURSE_TYPE_LABELS: Record<SellableCourseType, string> = {
  KM_5: '5K',
  KM_10: '10K',
  HALF: '하프',
  FULL: '풀',
}

export const COURSE_TYPE_OPTION_NAMES = Object.keys(COURSE_DISTANCE_KM) as SellableCourseType[]

/** 종목 구분 선택지 글자. 거리를 함께 보여 준다 (예: 하프 (21.1km)) */
export function courseTypeOptionLabel(type: SellableCourseType): string {
  return `${COURSE_TYPE_LABELS[type]} (${COURSE_DISTANCE_KM[type]}km)`
}

/** 종목 추가 창의 입력값. 숫자 칸도 입력 그대로(글자) 받는다 */
export type CourseDraft = {
  name: string
  courseType: SellableCourseType | ''
  crewQuota: string
  individualQuota: string
  price: string
  timeLimitMinutes: string
  kitIncluded: 'included' | 'none'
  minAge: typeof NO_LIMIT_MIN_AGE | typeof ADULT_MIN_AGE
}

export const COURSE_DRAFT_DEFAULTS: CourseDraft = {
  name: '',
  courseType: '',
  crewQuota: '',
  individualQuota: '',
  price: '',
  timeLimitMinutes: '',
  kitIncluded: 'included',
  minAge: NO_LIMIT_MIN_AGE,
}

/** 천 단위 쉼표는 보기용이라 숫자로 읽기 전에 지운다 */
const numberText = (pattern: RegExp) =>
  z
    .string()
    .trim()
    .transform((value) => value.replaceAll(',', ''))
    .pipe(z.string().regex(pattern, COURSE_FORM_ERRORS.number))
    .transform(Number)
const wholeNumber = numberText(/^\d+$/)

/**
 * 종목 추가 창 규칙. 크루 선예매를 열지 않으면 크루 정원은 받지 않고 0으로 만든다.
 * 정원 규칙은 서버 validateCourse와 같다: 크루 + 일반이 1명 이상.
 */
export function createCourseDraftSchema({ crewOpen }: { crewOpen: boolean }) {
  return z
    .object({
      name: z
        .string()
        .trim()
        .min(1, COURSE_FORM_ERRORS.nameRequired)
        .max(productZod.registerBodyCoursesItemNameMax, COURSE_FORM_ERRORS.nameMax),
      courseType: z.enum(COURSE_TYPE_OPTION_NAMES as [SellableCourseType], {
        error: COURSE_FORM_ERRORS.courseTypeRequired,
      }),
      // 크루 선예매를 열지 않으면 칸이 없어 비어 있으므로 값을 보지 않고 0으로 둔다
      crewQuota: crewOpen ? wholeNumber : z.string().transform(() => 0),
      individualQuota: wholeNumber,
      timeLimitMinutes: wholeNumber.pipe(z.number().min(1, COURSE_FORM_ERRORS.timeLimitPositive)),
      // 서버 RaceCourseSaveRequestDto가 @Positive라 0원은 막는다 (orval zod는 min(0)뿐)
      price: wholeNumber.pipe(z.number().min(1, COURSE_FORM_ERRORS.pricePositive)),
      kitIncluded: z.enum(['included', 'none']).transform((value) => value === 'included'),
      minAge: z.union([z.literal(NO_LIMIT_MIN_AGE), z.literal(ADULT_MIN_AGE)]),
    })
    .superRefine((course, ctx) => {
      if (course.crewQuota + course.individualQuota >= 1) return
      ctx.addIssue({
        code: 'custom',
        path: ['individualQuota'],
        message: crewOpen ? COURSE_FORM_ERRORS.quotaSum : COURSE_FORM_ERRORS.quotaPositive,
      })
    })
}

export type CourseDraftInput = z.input<ReturnType<typeof createCourseDraftSchema>>
export type CourseDraftParsed = z.output<ReturnType<typeof createCourseDraftSchema>>

/** 검사를 통과한 종목 입력값에 고정값(거리 · 노면 · 키트 금액)을 붙여 종목 한 줄로 만든다 */
export function toCourseInputFromParsed(data: CourseDraftParsed): RaceCourseInput {
  return {
    ...data,
    distanceKm: COURSE_DISTANCE_KM[data.courseType],
    surface: 'ROAD',
    kitPrice: 0,
  }
}

export const RACE_FORM_DEFAULTS: RaceFormValues = {
  title: '',
  description: '',
  location: '',
  locationDetail: '',
  regionSido: '',
  regionSigungu: '',
  raceDate: '',
  raceTime: '',
  crewOpen: false,
  crewSaleStartAt: '',
  crewSaleEndAt: '',
  crewMaxCount: '',
  individualOpenAt: '',
  applicationCloseAt: '',
  refundDeadline: '',
  // 종목 · 키트 옵션은 비워 두고 판매자가 직접 추가한다
  courses: [],
  kitOptions: [],
}

/** 검사를 마친 입력과 대회 이미지(대표 이미지) · 종목별 이미지로 대회 등록 요청을 만든다. courseImages는 courses와 같은 순서다. */
export function toRaceSaveRequest(
  values: RaceFormValues,
  raceImages: productModel.RaceImageRequestDto[],
  courseImages: productModel.RaceImageRequestDto[][],
): productModel.RaceSaveRequestDto {
  return {
    title: values.title.trim(),
    description: values.description.trim() || undefined,
    location: [values.location.trim(), values.locationDetail.trim()].filter(Boolean).join(' '),
    regionSido: values.regionSido.trim(),
    regionSigungu: values.regionSigungu.trim(),
    raceDate: `${parseRaceDateInput(values.raceDate) ?? values.raceDate}T${values.raceTime}:00`,
    crewSaleStartAt: values.crewOpen ? toLocalDateTime(values.crewSaleStartAt) : undefined,
    crewSaleEndAt: values.crewOpen ? toLocalDateTime(values.crewSaleEndAt) : undefined,
    individualOpenAt: toLocalDateTime(values.individualOpenAt),
    applicationCloseAt: toLocalDateTime(values.applicationCloseAt),
    // 환불 기한은 서버도 날짜(LocalDate)로 받는다
    refundDeadline: parseRaceDateInput(values.refundDeadline) ?? values.refundDeadline,
    crewMinCount: CREW_MIN_COUNT,
    // 크루 예매를 열고 칸을 채웠을 때만 보낸다. 비우면 상한 없음
    crewMaxCount:
      values.crewOpen && values.crewMaxCount.trim() ? Number(values.crewMaxCount) : undefined,
    images: raceImages,
    // 크루 예매를 열지 않으면 크루 몫 정원은 0으로 보낸다
    courses: values.courses.map((course, i) => ({
      ...course,
      crewQuota: values.crewOpen ? course.crewQuota : 0,
      images: courseImages[i] ?? [],
    })),
  }
}
