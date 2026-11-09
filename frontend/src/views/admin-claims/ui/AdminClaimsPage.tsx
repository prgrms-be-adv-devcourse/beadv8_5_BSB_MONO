'use client'

import { keepPreviousData, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { formatMonthDay } from '@/entities/payout'
import { ApiError, memberApi, payoutApi, unwrapData, type payoutModel } from '@/shared/api'
import { formatNumber, formatWon } from '@/shared/lib'
import {
  Alert,
  Badge,
  Button,
  DataTable,
  Dialog,
  EmptyState,
  KeyValue,
  Pagination,
  Segmented,
  Skeleton,
  StatTile,
  TextField,
  useToast,
} from '@/shared/ui'

type Status = payoutModel.PayoutClaimStatus
type Claim = payoutModel.PayoutClaimDto

const PAGE_SIZE = 50

const STATUS_LABEL: Record<Status, string> = { 청구_중: '청구 중', 입금_확인: '입금 확인' }

function errorText(error: unknown) {
  return (error instanceof ApiError && error.serverMessage) || '잠시 뒤 다시 시도해 주세요.'
}

/**
 * 판매자 ID → 이름. 회원 서비스의 판매자 신청 목록에서 맞춘다 (C6: 정산 API에는 이름이 없다)
 * 상태로 거르지 않는다. 승인 뒤 상태가 바뀐 판매자의 청구도 이름으로 보여야 한다 (#375 리뷰)
 */
function useSellerNames() {
  const sellers = memberApi.useFindAll(undefined, { query: { select: unwrapData } })
  return useMemo(
    () => new Map((sellers.data ?? []).map((s) => [s.id, s.businessName || s.representativeName])),
    [sellers.data],
  )
}

// 관리자 회수 관리 (BUK-107). 판매자가 청구 금액을 회사 계좌로 보냈는지 확인하고 회수 완료로 바꾼다
export function AdminClaimsPage() {
  const [status, setStatus] = useState<Status>('청구_중')
  const [codeText, setCodeText] = useState('')
  const [code, setCode] = useState<string>()
  const [page, setPage] = useState(0)
  const [selectedId, setSelectedId] = useState<number>()
  const sellerNames = useSellerNames()

  const claims = payoutApi.useClaims(
    code ? { code, page: 0, size: PAGE_SIZE } : { status, page, size: PAGE_SIZE },
    { query: { select: unwrapData, placeholderData: keepPreviousData } },
  )
  const rows = claims.data?.claims.content ?? []
  const selected = rows.find((c) => c.id === selectedId) ?? rows[0]
  const sellerName = (c: Claim) => sellerNames.get(c.sellerId) ?? `판매자 #${c.sellerId}`

  const changeStatus = (next: Status) => {
    setStatus(next)
    setPage(0)
    setCode(undefined)
  }
  const search = () => setCode(codeText.trim() || undefined)
  const clearSearch = () => {
    setCodeText('')
    setCode(undefined)
  }

  const summary = claims.data?.summary
  return (
    <ConsolePage
      title="회수 관리"
      description="판매자가 청구 금액을 회사 계좌로 보내면, 입금을 확인한 뒤 회수 완료로 바꿔요."
    >
      <div className="flex gap-4">
        {summary ? (
          <>
            <StatTile label="청구 중" value={formatNumber(summary.claimingCount)} unit="건" />
            <StatTile label="회수할 금액" value={formatWon(summary.claimingAmount)} />
            <StatTile
              label="이번 달 입금 확인"
              value={formatNumber(summary.confirmedThisMonthCount)}
              unit="건"
            />
          </>
        ) : (
          <Skeleton className="h-24 flex-1" />
        )}
      </div>

      <Segmented
        label="청구 상태"
        layout="hug"
        value={status}
        onChange={changeStatus}
        items={[
          { value: '청구_중', label: '청구 중', count: summary?.claimingCount },
          { value: '입금_확인', label: '입금 확인' },
        ]}
      />

      <ConsoleCard title={code ? `청구 ID ${code}` : `${STATUS_LABEL[status]} 청구`}>
        <form
          className="flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault()
            search()
          }}
        >
          <TextField
            label="청구 ID"
            placeholder="RC-20261215-0001"
            value={codeText}
            onChange={(e) => setCodeText(e.target.value)}
          />
          <Button type="submit" variant="secondary">
            찾기
          </Button>
          {code && (
            <Button type="button" variant="ghost" onClick={clearSearch}>
              검색 지우기
            </Button>
          )}
        </form>

        {claims.isError ? (
          <Alert
            tone="danger"
            title="청구 목록을 불러오지 못했어요"
            description={errorText(claims.error)}
          />
        ) : !claims.data ? (
          <Skeleton className="h-40 w-full" />
        ) : rows.length === 0 ? (
          <EmptyState
            title={
              code ? '이 청구 ID로 찾은 청구가 없어요' : `${STATUS_LABEL[status]}인 건이 없어요`
            }
          />
        ) : (
          <>
            <DataTable
              label="청구"
              rows={rows}
              rowKey={(c) => String(c.id)}
              selected={selected ? String(selected.id) : undefined}
              onRowClick={(c) => setSelectedId(c.id)}
              columns={[
                { key: 'code', header: '청구 ID', width: 'w-42.5', render: (c) => c.claimCode },
                { key: 'seller', header: '판매자', render: sellerName },
                { key: 'race', header: '대회 ID', width: 'w-20', render: (c) => c.raceId },
                {
                  key: 'amount',
                  header: '청구 금액',
                  align: 'right',
                  width: 'w-30',
                  render: (c) => formatWon(c.amount),
                },
                {
                  key: 'due',
                  header: '입금 기한',
                  width: 'w-22.5',
                  render: (c) => formatMonthDay(c.dueDate),
                },
                {
                  key: 'status',
                  header: '상태',
                  width: 'w-24',
                  render: (c) => (
                    <Badge tone={c.status === '청구_중' ? 'warning' : 'neutral'}>
                      {STATUS_LABEL[c.status]}
                    </Badge>
                  ),
                },
              ]}
            />
            {!code && claims.data.claims.totalPages > 1 && (
              <Pagination
                className="self-center"
                page={page + 1}
                totalPages={claims.data.claims.totalPages}
                onPageChange={(next) => setPage(next - 1)}
              />
            )}
          </>
        )}
      </ConsoleCard>

      {selected && <ClaimPanel claim={selected} sellerName={sellerName(selected)} />}
    </ConsolePage>
  )
}

function ClaimPanel({ claim, sellerName }: { claim: Claim; sellerName: string }) {
  const toast = useToast()
  const queryClient = useQueryClient()
  // 첫 청구가 자동으로 골라져 있어 잘못 누를 수 있으므로, 되돌릴 수 없는 입금 확인은 확인 창을 거친다 (#375 리뷰)
  const [asking, setAsking] = useState(false)
  const confirm = payoutApi.useConfirm<unknown>({
    mutation: {
      onSettled: () => setAsking(false),
      onSuccess: async () => {
        toast({ message: '회수 완료로 바꿨어요.', tone: 'success' })
        await queryClient.invalidateQueries({ queryKey: payoutApi.getClaimsQueryKey() })
      },
      onError: (error) => toast({ message: errorText(error), tone: 'error' }),
    },
  })

  return (
    <ConsoleCard title="회수 처리">
      <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
        <p className="text-label-m">
          {claim.claimCode} · {sellerName}
        </p>
        <KeyValue label="청구 금액" value={formatWon(claim.amount)} />
        <KeyValue label="입금 기한" value={formatMonthDay(claim.dueDate)} />
        {claim.confirmedAt && (
          <KeyValue label="입금 확인일" value={formatMonthDay(claim.confirmedAt)} />
        )}
        {claim.status === '청구_중' && (
          <>
            <Button className="w-full" disabled={confirm.isPending} onClick={() => setAsking(true)}>
              회수 완료
            </Button>
            <p className="text-caption text-text-tertiary">
              회사 계좌에 청구 금액이 들어온 것을 확인한 뒤 눌러 주세요. 되돌릴 수 없어요.
            </p>
            <Dialog
              open={asking}
              onOpenChange={setAsking}
              type="danger"
              title="입금을 확인했나요?"
              body={`${claim.claimCode} · ${sellerName}, ${formatWon(claim.amount)} 입금을 확인했나요? 회수 완료로 바꾸면 되돌릴 수 없어요.`}
              confirmLabel="입금 확인"
              confirming={confirm.isPending}
              onConfirm={() => confirm.mutate({ claimId: claim.id })}
            />
          </>
        )}
      </div>
    </ConsoleCard>
  )
}
