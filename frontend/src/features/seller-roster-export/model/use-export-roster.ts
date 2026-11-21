import { useMutation } from '@tanstack/react-query'

import { orderApi, type orderModel } from '@/shared/api'
import { csvFileName, saveCsv } from '@/shared/lib'

/** 참가 확정자 명단을 CSV로 받아 저장한다. 서버가 누가 언제 왜 몇 줄 받았는지 기록한다. */
export function useExportRoster() {
  return useMutation({
    mutationFn: async (body: orderModel.SellerRosterExportRequestDto) => {
      // 성공 응답은 RsData가 아니라 CSV 글 그대로다. 연락처가 담겨 있어 파일로만 저장하고 어디에도 남기지 않는다
      const csv = await orderApi.exportRoster(body)
      saveCsv(String(csv ?? ''), csvFileName('roster', body.raceId, new Date()))
    },
  })
}
