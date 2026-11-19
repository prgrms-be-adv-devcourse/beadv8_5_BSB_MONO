'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { ConsoleCard, ConsoleMessage, ConsolePage } from '@/widgets/console-page'
import { useAuthStatus } from '@/features/auth'
import { memberApi, unwrapData, type memberModel } from '@/shared/api'
import { cn, useSearchParamsState } from '@/shared/lib'
import { Alert, Button, Chip, DataTable, Dialog, Textarea } from '@/shared/ui'

import {
  loadErrorMessage,
  reviewErrorMessage,
  sellerDisplayName,
  sellerKindLabel,
  shouldRefetchAfter,
  toShortDate,
} from '../model/admin-sellers-api'
import { rejectFormSchema } from '../model/reject-form'

// 상태는 칩 대신 뜻 색 글자로 보여 준다 (완료 초록, 실패 빨강, 나머지 보조 글자)
const STATUS_TEXT: Record<string, string> = {
  success: 'text-text-success',
  danger: 'text-text-danger',
  warning: 'text-text-warning',
}
const statusText = (tone: string) =>
  cn('text-body2-medium', STATUS_TEXT[tone] ?? 'text-text-secondary')

type Tab = 'pending' | 'approved' | 'rejected'

const STATUS_BY_TAB = {
  pending: 'PENDING',
  approved: 'APPROVED',
  rejected: 'REJECTED',
} as const satisfies Record<Tab, memberModel.FindAllStatus>

// 서버가 반려 사유를 500자까지 받는다.
const REASON_MAX_LENGTH = 500
type RejectValues = z.infer<typeof rejectFormSchema>

// 피그마 V2 · A01 판매자 승인 (+ A01-1 반려 사유 입력)
// 신청 목록(GET /api/v1/member/admin/sellers?status=)을 탭별로 불러오고, 승인·반려(POST .../{id}/approval · rejection)를 보낸다.
// 서버가 주는 신청에 이메일·정산 계좌·약관 동의가 없어 시안의 그 칸들은 뺐다.
export function AdminSellersPage({ rejectOpen = false }: { rejectOpen?: boolean }) {
  const queryClient = useQueryClient()
  const authenticated = useAuthStatus() === 'authenticated'
  // 신청 상태 탭은 URL(?tab=)에 담는다
  const [url, setUrl] = useSearchParamsState(
    { tab: 'pending' },
    { tab: Object.keys(STATUS_BY_TAB) },
  )
  const tab = url.tab as Tab
  const [selectedId, setSelectedId] = useState<number>()
  const [dialogOpen, setDialogOpen] = useState(rejectOpen)
  const rejectForm = useForm<RejectValues>({
    resolver: zodResolver(rejectFormSchema),
    defaultValues: { reason: '' },
  })
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
    rejectForm.reset()
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
  const onReject = rejectForm.handleSubmit(({ reason }) => {
    if (!app) return
    setReviewError(undefined)
    reject.mutate(
      { id: app.id, data: { reason } },
      { onSuccess: onReviewed, onError: onReviewFailed },
    )
  })

  return (
    <ConsolePage title="판매자 관리">
      <div role="group" aria-label="신청 상태" className="flex items-center gap-2">
        {(
          [
            { value: 'pending', label: '승인 대기', count: pending.data?.length },
            { value: 'approved', label: '승인' },
            { value: 'rejected', label: '반려' },
          ] as { value: Tab; label: string; count?: number }[]
        ).map((t) => (
          <Chip
            key={t.value}
            selected={tab === t.value}
            onClick={() => {
              setUrl({ tab: t.value })
              setSelectedId(undefined)
              resetReviewInput()
            }}
          >
            {t.label}
            {t.count !== undefined && <span className="tabular-nums">{t.count}</span>}
          </Chip>
        ))}
      </div>
      {list.isError && (
        <ConsoleMessage kind="error" onRetry={() => void list.refetch()} retrying={list.isFetching}>
          {loadErrorMessage(list.error)}
        </ConsoleMessage>
      )}
      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-130 flex-1 flex-col gap-4">
          <DataTable
            label="판매자 신청"
            framed
            rowArrow
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
        </div>

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
                <span className={statusText(app.status === 'APPROVED' ? 'success' : 'danger')}>
                  {app.status === 'APPROVED' ? '승인' : '반려'}
                </span>
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
            <Controller
              control={rejectForm.control}
              name="reason"
              render={({ field, fieldState }) => (
                <Textarea
                  label="반려 사유 (필수)"
                  helper="신청자 화면에 그대로 보여요"
                  showCounter
                  maxLength={REASON_MAX_LENGTH}
                  error={fieldState.error?.message}
                  {...field}
                />
              )}
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
