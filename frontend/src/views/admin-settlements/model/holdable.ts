import { payoutStatusLabel } from '@/entities/payout'
import type { payoutModel } from '@/shared/api'

/**
 * 보류를 걸 수 없는 정산이면 줄에 보여 줄 문구, 걸 수 있으면 null.
 * 백엔드는 지급 전(지급 예정 · 지급보류)에만 보류를 걸고, 그 밖의 상태는 409로 거절한다 (Payout.hold).
 * 화면은 거기서 한 걸음 더 막는다: 이미 보류 중이면 사유가 달라도 고르지 못하게 한다.
 * 정산 ID로 찾은 줄은 보류 목록이 없어 상태(지급보류)로 보류 중임을 알아본다.
 */
export function holdBlockLabel(
  payout: payoutModel.PayoutDto,
  holds: readonly payoutModel.PayoutHoldDto[],
): string | null {
  if (holds.length > 0 || payout.status === '지급보류') return '보류 중'
  if (payout.status !== '지급_예정') return payoutStatusLabel(payout.status).label
  return null
}
