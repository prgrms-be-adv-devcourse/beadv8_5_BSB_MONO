import { useMutation } from '@tanstack/react-query'

import type { KitFilters } from '@/entities/seller-kit'
import { orderApi } from '@/shared/api'
import { csvFileName, saveCsv } from '@/shared/lib'

/** 키트 배송 목록을 CSV로 받아 저장한다. 서버가 사유와 함께 누가 몇 줄 받았는지 기록한다. */
export function useExportKits() {
  return useMutation({
    mutationFn: async ({ filters, reason }: { filters: KitFilters; reason: string }) => {
      // 성공 응답은 RsData가 아니라 CSV 글 그대로다
      const csv = await orderApi.exportKitShipments({ ...filters, reason })
      saveCsv(String(csv ?? ''), csvFileName('kit-shipments', filters.raceId, new Date()))
    },
  })
}
