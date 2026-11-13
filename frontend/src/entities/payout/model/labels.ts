import type { payoutModel } from '@/shared/api'
import type { BadgeTone } from '@/shared/ui'

// 정산 API의 enum 값(한글 상수, BUK-68)을 화면 문구로 바꾼다. `_`를 띄어 쓰거나 화면에 맞게 다듬는다.

type Label = { label: string; tone: BadgeTone }

export const PAYOUT_STATUS: Record<payoutModel.PayoutStatus, Label> = {
  지급_예정: { label: '지급 예정', tone: 'neutral' },
  지급보류: { label: '지급보류', tone: 'warning' },
  지급_중: { label: '지급 중', tone: 'neutral' },
  지급_완료: { label: '지급 완료', tone: 'success' },
  취소: { label: '취소', tone: 'neutral' },
  지급_불일치: { label: '지급 불일치', tone: 'warning' },
  // 지급 결과를 3번 몰라 사람이 확인 중이다(BUK-166). 판매자에게는 지급이 아직 진행 중인 것과 같아 이 문구로 보여 준다(11/12 결정)
  지급_확인_필요: { label: '지급 처리 중', tone: 'neutral' },
}

/**
 * 정산 상태 배지 문구. 백엔드에 상태가 먼저 생겨 매핑에 없는 값이 오면 화면이 깨지지 않게 원래 값을 그대로 보여 준다.
 */
export function payoutStatusLabel(status: string): Label {
  return Object.hasOwn(PAYOUT_STATUS, status)
    ? PAYOUT_STATUS[status as payoutModel.PayoutStatus]
    : { label: status, tone: 'neutral' }
}

export const PAYOUT_STAGE: Record<payoutModel.PayoutStage, string> = {
  일차_정산: '1차',
  최종_정산: '최종',
}

export const HOLD_STATUS: Record<payoutModel.PayoutHoldStatus, Label> = {
  지급보류: { label: '지급보류', tone: 'warning' },
  해제: { label: '해제', tone: 'neutral' },
}

export const HOLD_REASON: Record<payoutModel.PayoutHoldReason, string> = {
  정산_계좌_미등록: '정산 계좌 미등록',
  대회_취소_처리: '대회 취소 처리',
  대사_불일치: '대사 불일치',
}

export const RECORD_TYPE: Record<payoutModel.PayoutRecordType, string> = {
  선금: '선금',
  판매: '판매',
  수수료: '수수료',
  선지급_차감: '선지급 차감',
  회수_요청: '회수 요청',
  갚을_돈_추가: '갚을 돈 추가',
  회수_상계: '회수 상계',
  회수_입금: '회수 입금',
  선금_취소: '선금 취소',
  판매_취소: '판매 취소',
  수수료_취소: '수수료 취소',
  선지급_차감_취소: '선지급 차감 취소',
  회수_상계_취소: '회수 상계 취소',
}

/** 관리자 보류 걸기 사유 고르기 */
export const HOLD_REASON_ITEMS: { value: payoutModel.PayoutHoldReason; label: string }[] = [
  { value: '대회_취소_처리', label: HOLD_REASON.대회_취소_처리 },
  { value: '정산_계좌_미등록', label: HOLD_REASON.정산_계좌_미등록 },
]
