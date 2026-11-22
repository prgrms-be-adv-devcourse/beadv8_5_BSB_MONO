import { format, parseISO } from 'date-fns'

import type { productModel } from '@/shared/api'

import { daysUntil, formatRaceDateTime, parseKstDateTime } from '../lib/race-date'

/** 홈 메인 배너 한 장 (피그마 v4 · 홈 배너 · 실제 대회, 2253:19903) */
export type HomeBanner = {
  id: string
  title: string
  /** 대표 이미지. 없으면 색 포스터에 종목(posterLabel)을 크게 쓴다 */
  image?: string
  posterLabel: string
  chip: string
  /** 예: 12/20까지 · 5K · 10K · 서울숲 일대 */
  description: string
}

/** 관리자 홈 배너 목록 한 줄 */
export type HomeBannerRow = {
  raceId: number
  title: string
  /** 판매 상태. 홈에 안 보이면 이유를 붙인다(판매 중지 · 홈에 안 보임) */
  stage: string
  closeDate: string
  visible: boolean
}

/** 대회 추가 창의 한 줄 */
export type BannerCandidate = { raceId: number; title: string; meta: string }

type Stage = 'crew' | 'open' | 'upcoming' | 'closed'

type StageInput = {
  saleStatus?: string
  crewSaleStartAt?: string | null
  crewSaleEndAt?: string | null
  individualOpenAt?: string
  applicationCloseAt?: string
}

const STAGE_TEXT: Record<Exclude<Stage, 'closed'>, string> = {
  crew: '크루 예매 중',
  open: '접수 중',
  upcoming: '오픈 예정',
}

/** 판매가 멈췄거나 끝난 상태. 시각과 상관없이 배너에서 빠진다 */
const HIDDEN_STATUS = new Set(['STOPPED', 'CLOSED', 'CANCELED'])

const SALE_STATUS_LABEL: Record<productModel.HomeBannerAdminDto['saleStatus'], string> = {
  SCHEDULED: '판매 예정',
  ON_SALE: '판매 중',
  STOPPED: '판매 중지',
  CLOSED: '판매 종료',
  CANCELED: '대회 취소',
}

// "이번 주 접수 마감": 오늘부터 7일 안에 접수가 끝난다
const THIS_WEEK_DAYS = 7

function crewEnd(race: StageInput) {
  // 크루 예매 마감이 없으면(관리자 대회 목록) 개인 예매 오픈 전까지를 크루 예매 기간으로 본다
  return race.crewSaleEndAt ?? race.individualOpenAt
}

function stageOf(race: StageInput, now: Date): Stage {
  if (race.saleStatus && HIDDEN_STATUS.has(race.saleStatus)) return 'closed'
  if (race.applicationCloseAt && now >= parseKstDateTime(race.applicationCloseAt)) return 'closed'
  const end = crewEnd(race)
  if (
    race.crewSaleStartAt &&
    now >= parseKstDateTime(race.crewSaleStartAt) &&
    (!end || now < parseKstDateTime(end))
  ) {
    return 'crew'
  }
  if (!race.individualOpenAt || now < parseKstDateTime(race.individualOpenAt)) return 'upcoming'
  return 'open'
}

const monthDay = (value: string) => format(parseISO(value), 'M/d')

export function toHomeBanner(race: productModel.RaceSummaryDto, now: Date): HomeBanner {
  const stage = stageOf(race, now)
  const courses = (race.courses ?? []).map((c) => c.name ?? '').filter(Boolean)
  const closeAt = race.applicationCloseAt
  const when =
    stage === 'upcoming' && race.individualOpenAt
      ? `${formatRaceDateTime(race.individualOpenAt)} 오픈`
      : stage === 'crew' && crewEnd(race)
        ? `${monthDay(crewEnd(race)!)}까지`
        : closeAt
          ? `${monthDay(closeAt)}까지`
          : ''
  const chip =
    stage === 'open' && closeAt && daysUntil(closeAt, now) <= THIS_WEEK_DAYS
      ? '이번 주 접수 마감'
      : stage === 'closed'
        ? '접수 마감'
        : STAGE_TEXT[stage]
  return {
    id: String(race.raceId),
    title: race.title ?? '',
    image: race.imageUrl ?? undefined,
    posterLabel: courses.join(' · '),
    chip,
    description: [when, ...courses, race.location].filter(Boolean).join(' · '),
  }
}

/** 관리자 줄: 홈에 보이면 판매 상태, 안 보이면 서버가 준 이유 이름(판매 중지 · 홈에 안 보임) */
export function toHomeBannerRow(race: productModel.HomeBannerAdminDto): HomeBannerRow {
  const stage = race.visible
    ? SALE_STATUS_LABEL[race.saleStatus]
    : [race.hiddenReasonLabel, '홈에 안 보임'].filter(Boolean).join(' · ')
  return {
    raceId: race.raceId,
    title: race.title,
    stage,
    closeDate: monthDay(race.applicationCloseAt),
    visible: race.visible,
  }
}

/** 배너에 올릴 수 있는 대회(접수가 끝나지 않았고 판매가 멈추지 않음)만 줄로 만든다 */
export function toBannerCandidate(
  race: productModel.SellerRaceDto,
  now: Date,
): BannerCandidate | undefined {
  const stage = stageOf(race, now)
  if (stage === 'closed' || race.raceId === undefined) return undefined
  const close = race.applicationCloseAt ? ` · ${monthDay(race.applicationCloseAt)} 마감` : ''
  return { raceId: race.raceId, title: race.title ?? '', meta: `${STAGE_TEXT[stage]}${close}` }
}
