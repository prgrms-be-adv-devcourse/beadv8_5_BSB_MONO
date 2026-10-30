'use client'

import { differenceInCalendarDays, parseISO } from 'date-fns'
import type { FieldErrors } from 'react-hook-form'

import { memberApi, productApi, unwrapData } from '@/shared/api'
import { trackEvent, useTrackOnce, type SellerRaceFormEntry } from '@/shared/lib'

import type { RaceFormValues } from './race-form'

// 입력 칸 → 화면의 항목 묶음(ConsoleCard 제목). 어느 묶음에서 막히는지 보려고 묶음 이름만 보낸다
const SECTION_OF: Record<keyof RaceFormValues, string> = {
  title: '기본 정보',
  raceDate: '기본 정보',
  location: '기본 정보',
  regionSido: '기본 정보',
  regionSigungu: '기본 정보',
  individualOpenAt: '기본 정보',
  applicationCloseAt: '기본 정보',
  courses: '종목 · 참가권',
  refundDeadline: '취소 · 환불 정책',
}
const IMAGE_SECTION = '이미지'

/** 오류가 난 항목 묶음 이름. 화면 순서대로, 겹치지 않게 */
export function errorSections(errors: FieldErrors<RaceFormValues>, imageError: boolean) {
  const sections = new Set(
    (Object.keys(errors) as (keyof RaceFormValues)[]).flatMap((key) =>
      SECTION_OF[key] ? [SECTION_OF[key]] : [],
    ),
  )
  if (imageError) sections.add(IMAGE_SECTION)
  const order = ['기본 정보', IMAGE_SECTION, '종목 · 참가권', '취소 · 환불 정책']
  return order.filter((name) => sections.has(name))
}

/**
 * 대회 등록 화면의 이벤트. 들어올 때 seller_race_form_start, 입력 오류로 막히면 seller_race_form_error,
 * 등록이 성공하면 seller_race_register를 보낸다.
 * 첫 대회인지는 등록 전 내 대회 목록으로, 승인 뒤 지난 날수는 판매자 정보의 심사 시각으로 계산한다.
 */
export function useRaceFormTracking(entry: SellerRaceFormEntry) {
  useTrackOnce('seller_race_form_start', { entry })
  const seller = memberApi.useFindMine({ query: { select: unwrapData, retry: false } })
  const races = productApi.useFindMine({ query: { select: unwrapData } })

  return {
    formError(sections: string[]) {
      if (sections.length > 0) trackEvent('seller_race_form_error', { sections })
    },
    /** 등록 버튼을 누를 때 부른다. 등록 뒤에는 목록이 바뀌므로 그 전에 첫 대회인지 정해 둔다 */
    beforeRegister() {
      const isFirstRace = races.data ? races.data.length === 0 : undefined
      const reviewedAt = seller.data?.status === 'APPROVED' ? seller.data.reviewedAt : undefined
      return (raceId: number | undefined, courseCount: number) =>
        trackEvent('seller_race_register', {
          raceId: String(raceId ?? ''),
          courseCount,
          isFirstRace,
          daysSinceApproval: reviewedAt
            ? differenceInCalendarDays(new Date(), parseISO(reviewedAt))
            : undefined,
        })
    },
  }
}
