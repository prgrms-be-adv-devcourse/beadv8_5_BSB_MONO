'use client'

import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  useAdminReports,
  useHandleReport,
  type AdminReport,
  type ReportReason,
} from '@/entities/board'
import { ApiError } from '@/shared/api'
import {
  Badge,
  Button,
  DataTable,
  EmptyState,
  KeyValue,
  Skeleton,
  StatTile,
  useToast,
  type BadgeTone,
} from '@/shared/ui'

const REASON: Record<ReportReason, string> = {
  AD: '광고',
  ABUSE: '욕설 · 비방',
  OBSCENE: '음란',
  OTHER: '기타',
}
const STATUS: Record<AdminReport['status'], { label: string; tone: BadgeTone }> = {
  HIDDEN: { label: '숨김', tone: 'warning' },
  RESTORED: { label: '되살림', tone: 'neutral' },
  DELETED: { label: '지움', tone: 'danger' },
}

const rowKey = (r: AdminReport) => `${r.targetType}-${r.targetId}`
const kind = (r: AdminReport) => (r.targetType === 'POST' ? '글' : '댓글')
const reasonText = (r: AdminReport) =>
  r.reasons.map((x) => `${REASON[x.reason]} ${x.count}`).join(' · ')
const topReason = (r: AdminReport) =>
  REASON[[...r.reasons].sort((a, b) => b.count - a.count)[0]?.reason ?? 'OTHER']

// 피그마 A12 커뮤니티 신고 관리 · A12-1 처리 후
export function AdminCommunityReportsPage() {
  const reports = useAdminReports()
  const handle = useHandleReport()
  const toast = useToast()
  const [selectedKey, setSelectedKey] = useState<string>()

  const items = reports.data?.items ?? []
  const selected =
    items.find((r) => rowKey(r) === selectedKey) ?? items.find((r) => r.status === 'HIDDEN')

  const act = (action: 'restore' | 'delete') => {
    if (!selected) return
    handle.mutate(
      { targetType: selected.targetType, targetId: selected.targetId, action },
      {
        onError: (e) =>
          toast({
            message:
              e instanceof ApiError && e.serverMessage ? e.serverMessage : '처리하지 못했어요',
            tone: 'error',
          }),
      },
    )
  }

  return (
    <ConsolePage title="커뮤니티 신고" description="숨겨진 글과 댓글을 보고 되살리거나 지워요.">
      {reports.isPending ? (
        <div role="status" aria-label="불러오는 중" className="flex flex-col gap-4">
          <Skeleton shape="rect" className="h-24 rounded-lg" />
          <Skeleton shape="rect" className="h-48 rounded-lg" />
        </div>
      ) : reports.isError ? (
        <EmptyState
          role="alert"
          icon="CircleAlert"
          title="신고 목록을 불러오지 못했어요"
          action={
            <Button
              variant="secondary"
              loading={reports.isFetching}
              onClick={() => void reports.refetch()}
            >
              다시 시도하기
            </Button>
          }
        />
      ) : (
        <>
          <div className="flex gap-4">
            <StatTile label="숨겨진 글 · 댓글" value={reports.data.hiddenCount} unit="건" />
            <StatTile label="확인을 기다리는 신고" value={reports.data.pendingReportCount} />
            <StatTile label="오늘 처리" value={reports.data.handledTodayCount} unit="건" />
          </div>

          <ConsoleCard title={`숨겨진 글 · 댓글 ${items.length}건`}>
            {items.length === 0 ? (
              <EmptyState title="숨겨진 글 · 댓글이 없어요" />
            ) : (
              <DataTable
                label="숨겨진 글 · 댓글"
                rows={items}
                rowKey={rowKey}
                selected={selected && rowKey(selected)}
                onRowClick={(r) => setSelectedKey(rowKey(r))}
                columns={[
                  { key: 'kind', header: '종류', width: 'w-42.5', render: kind },
                  {
                    key: 'writer',
                    header: '글쓴이',
                    width: 'w-35',
                    render: (r) => r.writerNickname,
                  },
                  { key: 'content', header: '내용', width: 'w-93', render: (r) => r.content },
                  {
                    key: 'count',
                    header: '신고 수',
                    align: 'right',
                    width: 'w-29',
                    render: (r) => r.reportCount,
                  },
                  { key: 'reason', header: '많이 고른 사유', render: topReason },
                  {
                    key: 'status',
                    header: '상태',
                    width: 'w-25',
                    render: (r) => (
                      <Badge tone={STATUS[r.status].tone}>{STATUS[r.status].label}</Badge>
                    ),
                  },
                ]}
              />
            )}
          </ConsoleCard>

          {selected && (
            <ConsoleCard title="처리">
              <div className="flex flex-col gap-3 rounded-md p-4 inset-ring inset-ring-border-default">
                <p className="text-body2-medium">
                  {kind(selected)} · {selected.writerNickname}
                  {selected.postTitle && ` · 「${selected.postTitle}」에 단 댓글`}
                </p>
                <KeyValue label="내용" value={selected.content} />
                <KeyValue label="신고 사유" value={reasonText(selected)} />
                <Button
                  variant="secondary"
                  className="w-full"
                  disabled={selected.status !== 'HIDDEN' || handle.isPending}
                  onClick={() => act('restore')}
                >
                  되살리기
                </Button>
                <Button
                  variant="danger"
                  className="w-full"
                  disabled={selected.status !== 'HIDDEN' || handle.isPending}
                  onClick={() => act('delete')}
                >
                  지우기
                </Button>
                <p className="text-caption-regular text-text-tertiary">
                  되살리면 다시 모두에게 보여요. 지우면 쓴 사람에게도 보이지 않아요.
                </p>
              </div>
            </ConsoleCard>
          )}
        </>
      )}
    </ConsolePage>
  )
}
