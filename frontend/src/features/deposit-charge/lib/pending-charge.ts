import { z } from 'zod'

/**
 * 토스 결제창으로 떠나기 전에 적어 두는 충전 요청. 결제창이 페이지를 떠났다 돌아와도 sessionStorage에 남는다.
 * 돌아온 주소의 orderId·amount가 여기 적은 값과 같을 때만 충전 확정을 부른다 (토스 문서: 금액 위변조 확인).
 * 탭을 닫으면 사라진다. 저장이 막힌 브라우저(사생활 보호 모드 등)에서는 확정을 부르지 않고 실패로 본다.
 */
const PendingCharge = z.object({
  /** 토스 orderId이자 충전 확정 API의 멱등키 */
  orderId: z.string(),
  amount: z.number().int().positive(),
  /** 결제 중 충전이면 돌아가 다시 결제할 주문 */
  returnOrderId: z.number().int().positive().optional(),
  /**
   * 충전 확정을 보낸 시각(ms). 확정 결과(충전됨·거절)를 받으면 기록째 지우므로, 남아 있으면 승인 결과를 모르는 충전이다.
   * 이 값이 있으면 돌아온 화면을 다시 열어도 확정을 다시 부르지 않고, 잠시 새 충전도 막는다.
   */
  uncertainAt: z.number().optional(),
})

export type PendingCharge = z.infer<typeof PendingCharge>

const KEY = 'crewrun:pending-charge'

export function savePendingCharge(charge: PendingCharge) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(charge))
  } catch {
    // 저장이 막혀 있으면 돌아왔을 때 확인할 수 없어 실패로 처리된다
  }
}

export function readPendingCharge(): PendingCharge | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    if (raw === null) return null
    const parsed = PendingCharge.safeParse(JSON.parse(raw))
    return parsed.success ? parsed.data : null
  } catch {
    return null
  }
}

export function clearPendingCharge() {
  try {
    sessionStorage.removeItem(KEY)
  } catch {
    // 지울 수 없어도 다음 충전이 덮어쓴다
  }
}
