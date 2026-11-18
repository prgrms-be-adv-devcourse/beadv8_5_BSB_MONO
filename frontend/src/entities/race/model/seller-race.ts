import type { fileModel, productModel } from '@/shared/api'

import { formatRaceDate } from '../lib/race-date'

// 판매자 대회 관리(S06 · S09)가 쓰는 대회 모양과 변환

export type SellerSaleStatus = NonNullable<productModel.SellerRaceDto['saleStatus']>

export type SellerRaceBadge = {
  label: string
  tone: 'brand' | 'warning' | 'danger' | 'neutral'
}

/** 판매 상태 배지. 작성 중(DRAFT)은 없어져 등록하면 바로 판매 예정이 된다. */
export const SELLER_SALE_STATUS: Record<SellerSaleStatus, SellerRaceBadge> = {
  SCHEDULED: { label: '판매 예정', tone: 'neutral' },
  ON_SALE: { label: '판매 중', tone: 'brand' },
  STOPPED: { label: '판매 중지', tone: 'warning' },
  CLOSED: { label: '종료', tone: 'neutral' },
  CANCELED: { label: '대회 취소', tone: 'danger' },
}

/** 판매가 시작된 대회. 참가비·키트 금액을 바꿀 수 없고 정원은 늘리기만, 환불 기한은 늦추기만 된다. */
export function isSaleStarted(status: string | undefined): boolean {
  return status === 'ON_SALE' || status === 'STOPPED' || status === 'CLOSED'
}

/** 수정·일정 변경·취소를 할 수 있는 대회 (판매가 끝났거나 취소된 대회는 안 된다) */
export function isRaceEditable(status: string | undefined): boolean {
  return status !== undefined && status !== 'CLOSED' && status !== 'CANCELED'
}

export type SellerRaceRow = {
  id: number
  title: string
  /** 대회일 11/1 (일) */
  date: string
  /** 접수 10/14~10/31 · 대회 11/1 */
  period: string
  sold: number
  capacity: number
  saleStatus?: SellerSaleStatus
  badge: SellerRaceBadge
}

function monthDay(value: string | undefined): string {
  const match = value ? /^\d{4}-(\d{2})-(\d{2})/.exec(value) : null
  return match ? `${Number(match[1])}/${Number(match[2])}` : '-'
}

function toPeriodText(race: productModel.SellerRaceDto): string {
  return `접수 ${monthDay(race.saleOpenDate)}~${monthDay(race.applicationCloseAt)} · 대회 ${monthDay(race.raceDate)}`
}

export function toSellerRaceRow(race: productModel.SellerRaceDto): SellerRaceRow {
  const courses = race.courses ?? []
  return {
    id: race.raceId ?? 0,
    title: race.title ?? '',
    date: race.raceDate ? formatRaceDate(race.raceDate) : '-',
    period: toPeriodText(race),
    sold: courses.reduce((sum, c) => sum + (c.sold ?? 0), 0),
    capacity: courses.reduce((sum, c) => sum + (c.quota ?? 0), 0),
    saleStatus: race.saleStatus,
    badge: race.saleStatus ? SELLER_SALE_STATUS[race.saleStatus] : { label: '-', tone: 'neutral' },
  }
}

// 대표 이미지는 대회에만, 나머지 이미지는 종목에만 붙는다 (CRU-6)
const RACE_IMAGE_TYPES = new Set<string>(['THUMBNAIL'])
const COURSE_IMAGE_TYPES = new Set<string>(['DETAIL', 'COURSE', 'KIT', 'INTRO_VIDEO'])

/** 연결된 파일 → 수정 요청의 이미지 목록. 수정은 이미지도 통째로 바꾸므로 지금 연결된 것을 그대로 다시 보낸다. */
function toImageRequests(
  files: fileModel.FileDto[],
  types: Set<string>,
): productModel.RaceImageRequestDto[] {
  return files.flatMap((file) =>
    file.id !== undefined && file.fileType && types.has(file.fileType) && file.status !== 'DELETED'
      ? [
          {
            fileId: file.id,
            fileType: file.fileType as productModel.RaceImageRequestDtoFileType,
            sortNo: file.sortNo ?? 0,
          },
        ]
      : [],
  )
}

/**
 * 대회 상세 + 대회 파일 + 종목별 파일 → 지금 상태 그대로의 수정 요청 (PUT은 대회 전체를 보낸다).
 * 화면은 여기서 바꿀 값만 덮어써 보낸다.
 */
export function toRaceEditRequest(
  race: productModel.RaceDetailDto,
  raceFiles: fileModel.FileDto[],
  filesByCourse: Record<number, fileModel.FileDto[]>,
): productModel.RaceSaveRequestDto {
  return {
    title: race.title ?? '',
    description: race.description ?? undefined,
    location: race.location ?? '',
    regionSido: race.regionSido ?? '',
    regionSigungu: race.regionSigungu ?? '',
    raceDate: race.raceDate ?? '',
    crewSaleStartAt: race.crewSaleStartAt ?? undefined,
    crewSaleEndAt: race.crewSaleEndAt ?? undefined,
    individualOpenAt: race.individualOpenAt ?? '',
    applicationCloseAt: race.applicationCloseAt ?? '',
    refundDeadline: race.refundDeadline ?? '',
    refundPolicy: race.refundPolicy ?? undefined,
    crewMinCount: race.crewMinCount ?? 5,
    crewMaxCount: race.crewMaxCount ?? undefined,
    images: toImageRequests(raceFiles, RACE_IMAGE_TYPES),
    courses: (race.courses ?? []).map((course) => ({
      courseId: course.raceCourseId,
      name: course.name ?? '',
      courseType: course.courseType ?? 'KM_10',
      surface: course.surface ?? 'ROAD',
      distanceKm: course.distanceKm ?? 0,
      timeLimitMinutes: course.timeLimitMinutes ?? 0,
      price: course.price ?? 0,
      kitIncluded: course.kitIncluded ?? false,
      kitPrice: course.kitPrice ?? 0,
      tshirtSizeOption: course.tshirtSizeOption ?? [],
      minAge: course.minAge ?? 14,
      maxAge: course.maxAge ?? undefined,
      crewQuota: course.crewQuota ?? 0,
      individualQuota: course.individualQuota ?? 0,
      images: toImageRequests(
        course.raceCourseId === undefined ? [] : (filesByCourse[course.raceCourseId] ?? []),
        COURSE_IMAGE_TYPES,
      ),
    })),
  }
}
