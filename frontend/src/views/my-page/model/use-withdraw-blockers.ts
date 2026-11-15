import { useQueries, useQuery } from '@tanstack/react-query'

import { useAuthStatus } from '@/features/auth'
import { daysUntil, raceQueryOptions } from '@/entities/race'
import { ticketStatus, withdrawTicketsQueryOptions } from '@/entities/ticket'
import { useMyBalance } from '@/entities/wallet'
import { memberApi, unwrapData } from '@/shared/api'

export type WithdrawBlockers = {
  /** 아직 확인하는 중이면 탈퇴하기를 열지 않는다 */
  pending: boolean
  /** 남은 예치금(원). 없으면 0 */
  balance: number
  /** 대회가 끝나지 않은 참가권 수 */
  ticketCount: number
  /** 첫 번째 참가권의 안내용 이름: 용감한 쿠키RUN(11/1) */
  firstRace?: string
  /** 크루장인 크루 수. 서버는 크루장인 크루가 있으면 탈퇴를 거절한다 */
  leaderCrewCount: number
  /** 지갑 · 참가권 · 대회 · 크루 중 하나라도 못 불러왔다. 없다고 보지 않고 탈퇴를 막는다 */
  failed: boolean
  /** 못 불러온 것만 다시 불러온다 */
  retry: () => void
}

/**
 * 탈퇴 전에 마쳐야 할 일(피그마 M31). 남은 예치금과 끝나지 않은 참가권(취소하지 않았고 대회 날짜가 안 지난 것)을 센다.
 * 서버(countActiveTickets)와 같은 기준으로, 결제 완료 · 취소 중 참가권과 결제 기한 전 결제 대기 주문도 센다.
 * 탈퇴 요청은 서버가 다시 확인하므로, 여기서는 화면에서 미리 알려 주는 용도다.
 * 조회가 하나라도 실패하면 남은 일이 없는 것으로 보지 않고 failed로 알린다.
 */
export function useWithdrawBlockers(): WithdrawBlockers {
  const authenticated = useAuthStatus() === 'authenticated'
  const wallet = useMyBalance()
  const tickets = useQuery({ ...withdrawTicketsQueryOptions(), enabled: authenticated })
  const crews = memberApi.useFindMyCrews({ query: { select: unwrapData, enabled: authenticated } })
  const list = (tickets.data ?? []).filter((t) => t.itemStatus !== 'CANCELED')
  const raceIds = [...new Set(list.map((t) => t.raceId))].filter((id) => id > 0)
  const races = useQueries({ queries: raceIds.map((id) => raceQueryOptions(id)) })
  const raceById = new Map(raceIds.map((id, i) => [id, races[i]?.data]))

  const open = list.filter((t) => {
    const date = raceById.get(t.raceId)?.raceDate
    // 대회 날짜를 못 받았으면 끝나지 않은 것으로 보고 막는다(서버가 최종 확인한다)
    return ticketStatus(t, { raceOver: date ? daysUntil(date) < 0 : false }) === 'confirmed'
  })
  const first = raceById.get(open[0]?.raceId ?? 0)
  const firstDate = first?.raceDate?.slice(5, 10).split('-').map(Number)
  const failed = wallet.isError || tickets.isError || crews.isError || races.some((r) => r.isError)
  const retry = () => {
    if (wallet.isError) void wallet.refetch()
    if (tickets.isError) void tickets.refetch()
    if (crews.isError) void crews.refetch()
    races.filter((r) => r.isError).forEach((r) => void r.refetch())
  }
  return {
    pending:
      !failed &&
      (wallet.isPending || tickets.isPending || crews.isPending || races.some((r) => r.isPending)),
    failed,
    retry,
    leaderCrewCount: (crews.data ?? []).filter((c) => c.myRole === 'LEADER').length,
    balance: wallet.balance ?? 0,
    ticketCount: open.length,
    firstRace: first?.title
      ? `${first.title}${firstDate ? `(${firstDate[0]}/${firstDate[1]})` : ''}`
      : open[0]?.itemName,
  }
}
