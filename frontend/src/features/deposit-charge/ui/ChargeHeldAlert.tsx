import { Alert } from '@/shared/ui'

/** 결과를 모르는 충전이 있어 새 충전(결제창)을 막았을 때 보여 주는 안내 */
export function ChargeHeldAlert() {
  return (
    <Alert
      tone="warning"
      title="앞선 충전 결과를 확인하고 있어요"
      description="결제가 됐다면 예치금에 자동으로 들어가요. 잠시 후 잔액을 확인해 주세요."
    />
  )
}
