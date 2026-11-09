'use client'

import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import {
  Alert,
  Badge,
  Button,
  DataTable,
  Dialog,
  Segmented,
  Textarea,
  TextField,
} from '@/shared/ui'

import {
  ADMIN_SELLER_ERRORS,
  loadErrorMessage,
  reviewErrorMessage,
  sellerDisplayName,
  sellerKindLabel,
  shouldRefetchAfter,
  toShortDate,
} from '../model/admin-sellers-api'

type Tab = 'pending' | 'approved' | 'rejected'

const STATUS_BY_TAB = {
  pending: 'PENDING',
  approved: 'APPROVED',
  rejected: 'REJECTED',
} as const satisfies Record<Tab, memberModel.FindAllStatus>

// 서버가 반려 사유를 500자까지 받는다.
const REASON_MAX_LENGTH = 500

// 피그마 V2 · A01 판매자 승인 (+ A01-1 반려 사유 입력)
// 신청 목록(GET /api/v1/member/admin/sellers?status=)을 탭별로 불러오고, 승인·반려(POST .../{id}/approval · rejection)를 보낸다.
// 서버가 주는 신청에 이메일·정산 계좌·약관 동의가 없어 시안의 그 칸들은 뺐다.
export function AdminSellersPage({ rejectOpen = false }: { rejectOpen?: boolean }) {
  const queryClient = useQueryClient()
  const authenticated = useAuthStatus() === 'authenticated'
  const [tab, setTab] = useState<Tab>('pending')
  const [selectedId, setSelectedId] = useState<number>()
  const [dialogOpen, setDialogOpen] = useState(rejectOpen)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string>()
  const [reviewError, setReviewError] = useState<string>()

  const list = memberApi.useFindAll(
    { status: STATUS_BY_TAB[tab] },
    { query: { select: unwrapData, enabled: authenticated } },
  )
  // 탭의 "승인 대기" 개수. 승인 대기 탭이면 위 목록과 같은 요청이라 한 번만 간다.
  const pending = memberApi.useFindAll(
    { status: 'PENDING' },
    { query: { select: unwrapData, enabled: authenticated } },
  )
  const approve = memberApi.useApprove()
  const reject = memberApi.useReject()

  const applications = list.data ?? []
  const app = applications.find((a) => a.id === selectedId) ?? applications[0]
  const reviewing = approve.isPending || reject.isPending

  const refetchLists = () =>
    queryClient.invalidateQueries({ queryKey: memberApi.getFindAllQueryKey() })

  // 다른 신청을 고르거나 탭을 바꾸면 앞 신청에 적던 사유가 남지 않게 비운다.
  const resetReviewInput = () => {
    setReviewError(undefined)
    setReason('')
    setReasonError(undefined)
  }

  const onReviewed = () => {
    resetReviewInput()
    setDialogOpen(false)
    return refetchLists()
  }
  const onReviewFailed = (error: unknown) => {
    setReviewError(reviewErrorMessage(error))
    setDialogOpen(false)
    if (shouldRefetchAfter(error)) void refetchLists()
  }

  const onApprove = () => {
    if (!app) return
    setReviewError(undefined)
    approve.mutate({ id: app.id }, { onSuccess: onReviewed, onError: onReviewFailed })
  }
  const onReject = () => {
    if (!app) return
    const trimmed = reason.trim()
    if (!trimmed) {
      setReasonError(ADMIN_SELLER_ERRORS.reasonRequired)
      return
    }
    setReviewError(undefined)
    reject.mutate(
      { id: app.id, data: { reason: trimmed } },
      { onSuccess: onReviewed, onError: onReviewFailed },
    )
  }

  return (
    <ConsolePage
      title="판매자 승인"
      description="승인하면 대회를 등록할 수 있어요. 반려할 때는 사유를 꼭 적어요."
    >
      <Segmented
        label="신청 상태"
        layout="hug"
        size="l"
        value={tab}
        onChange={(next) => {
          setTab(next)
          setSelectedId(undefined)
          resetReviewInput()
        }}
        items={[
          { value: 'pending', label: '승인 대기', count: pending.data?.length },
          { value: 'approved', label: '승인' },
          { value: 'rejected', label: '반려' },
        ]}
      />
      {list.isError && <Alert tone="danger" title={loadErrorMessage(list.error)} />}
      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard className="min-w-130 flex-1">
          <DataTable
            label="판매자 신청"
            rows={applications}
            rowKey={(a) => String(a.id)}
            selected={app ? String(app.id) : undefined}
            onRowClick={(a) => {
              setSelectedId(a.id)
              resetReviewInput()
            }}
            columns={[
              {
                key: 'requestedAt',
                header: '신청일',
                align: 'right',
                render: (a) => toShortDate(a.requestedAt),
              },
              { key: 'seller', header: '판매자', render: (a) => sellerDisplayName(a) },
              { key: 'kind', header: '구분', render: (a) => sellerKindLabel(a) },
              {
                key: 'businessNumber',
                header: '사업자등록번호',
                render: (a) => a.businessNumber || '—',
              },
            ]}
          />
          {list.isSuccess && applications.length === 0 && (
            <p className="text-body2-regular text-text-tertiary">이 상태의 신청이 없어요.</p>
          )}
        </ConsoleCard>

        {app && (
          <ConsoleCard title={sellerDisplayName(app)} className="w-100 shrink-0">
            <dl className="flex flex-col gap-3">
              {[
                ['대표자', app.representativeName],
                ['구분', sellerKindLabel(app)],
              ].map(([label, value]) => (
                <div key={label} className="flex items-start justify-between gap-4">
                  <dt className="text-body2-regular text-text-secondary">{label}</dt>
                  <dd className="text-body2-medium">{value}</dd>
                </div>
              ))}
            </dl>
            {app.status === 'PENDING' ? (
              <>
                <TextField
                  label="반려 사유"
                  helper="반려하면 이 사유가 신청자에게 보여요"
                  maxLength={REASON_MAX_LENGTH}
                  value={reason}
                  onChange={(event) => {
                    setReason(event.target.value)
                    setReasonError(undefined)
                  }}
                />
                {reviewError && <Alert tone="danger" title={reviewError} />}
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    className="flex-1"
                    disabled={reviewing}
                    onClick={() => setDialogOpen(true)}
                  >
                    반려
                  </Button>
                  <Button className="flex-1" loading={approve.isPending} onClick={onApprove}>
                    승인
                  </Button>
                </div>
              </>
            ) : (
              <div className="flex items-start justify-between gap-4">
                <Badge tone={app.status === 'APPROVED' ? 'success' : 'danger'}>
                  {app.status === 'APPROVED' ? '승인' : '반려'}
                </Badge>
                {app.status === 'REJECTED' && app.rejectReason && (
                  <p className="text-body2-regular text-text-secondary">사유: {app.rejectReason}</p>
                )}
              </div>
            )}
          </ConsoleCard>
        )}
      </div>

      <Dialog
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        type="danger"
        title="신청을 반려할까요?"
        body={
          <div className="flex flex-col gap-4">
            <p>
              반려하면 신청자에게 알림이 가고, 사유가 신청자 화면(S01-1)에 그대로 보여요. 신청자는
              고쳐서 다시 신청할 수 있어요.
            </p>
            <Textarea
              label="반려 사유 (필수)"
              helper="신청자 화면에 그대로 보여요"
              showCounter
              maxLength={REASON_MAX_LENGTH}
              value={reason}
              error={reasonError}
              onChange={(event) => {
                setReason(event.target.value)
                setReasonError(undefined)
              }}
            />
          </div>
        }
        confirmLabel="반려하기"
        cancelLabel="취소"
        confirming={reject.isPending}
        onConfirm={onReject}
      />
    </ConsolePage>
  )
}
