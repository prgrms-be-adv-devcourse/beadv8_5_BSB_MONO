'use client'

import { useMutation } from '@tanstack/react-query'

import { recentPayoutMonths, type DetailPeriod } from '@/entities/payout'
import { ApiError, payoutApi } from '@/shared/api'
import { csvFileName, saveCsv } from '@/shared/lib'
import { Button, useToast } from '@/shared/ui'

type Props = {
  tab: string
  today: Date
  period: DetailPeriod
}

/**
 * 정산 엑셀(CSV) 내려받기. 월별 정산 탭은 정산 내역(최근 12개월), 건별 정산 탭은 고른 기간(1개월)을 받는다.
 * 성공 응답은 RsData가 아니라 CSV 글 그대로다. 다른 탭에는 받을 자료가 없어 막아 둔다.
 */
export function SettlementExportButton({ tab, today, period }: Props) {
  const showToast = useToast()
  const exportCsv = useMutation({
    mutationFn: async () => {
      const csv =
        tab === 'monthly'
          ? await payoutApi.exportPayouts(recentPayoutMonths(today))
          : await payoutApi.exportPayoutItems({ from: period.from, to: period.to })
      saveCsv(
        String(csv ?? ''),
        csvFileName(tab === 'monthly' ? 'payouts' : 'payout-items', undefined, new Date()),
      )
    },
    onError: (error) =>
      showToast({
        message: (error instanceof ApiError && error.serverMessage) || '정산 자료를 받지 못했어요',
        tone: 'error',
      }),
  })
  const available = tab === 'monthly' || tab === 'orders'

  return (
    <Button
      variant="secondary"
      disabled={!available}
      title={available ? undefined : '월별 정산과 건별 정산 탭에서 받을 수 있어요'}
      loading={exportCsv.isPending}
      onClick={() => exportCsv.mutate()}
    >
      엑셀로 내려받기
    </Button>
  )
}
