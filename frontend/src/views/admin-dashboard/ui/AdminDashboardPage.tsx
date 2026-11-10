import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { cn } from '@/shared/lib'
import {
  Badge,
  buttonClassName,
  DataTable,
  FOCUS_RING,
  StatTile,
  type BadgeTone,
} from '@/shared/ui'

type Payout = { seller: string; id: string; amount: string; status: string; tone: BadgeTone }
type Refund = { at: string; member: string; amount: string; status: string; tone: BadgeTone }

// TODO(api): 관리자 대시보드 API로 바꾼다. 지금은 시안 예시 값.
const PAYOUTS: Payout[] = [
  {
    seller: '데브시스터즈러닝',
    id: 'ST-20261120-0A3F',
    amount: '₩1,920,000',
    status: '지급 예정',
    tone: 'neutral',
  },
  {
    seller: '나이트런컴퍼니',
    id: 'ST-20261120-2D91',
    amount: '₩2,616,000',
    status: '지급 예정',
    tone: 'neutral',
  },
  {
    seller: '춘천러닝클럽',
    id: 'ST-20261120-1C07',
    amount: '₩860,000',
    status: '지급보류',
    tone: 'warning',
  },
]

const REFUNDS: Refund[] = [
  { at: '10/3', member: '한강러너', amount: '₩40,000', status: '요청', tone: 'neutral' },
  { at: '10/4', member: '새벽토끼', amount: '₩15,000', status: '요청', tone: 'neutral' },
  { at: '9/30', member: '달리는곰', amount: '₩120,000', status: '환급 완료', tone: 'success' },
]

const LINK = cn('rounded-sm text-body2-medium text-text-brand', FOCUS_RING)

// 피그마 V2 · A03 관리자 홈
export function AdminDashboardPage() {
  return (
    <ConsolePage
      title="관리자 홈"
      description="처리 대기 중인 일을 먼저 확인해요."
      actions={
        <Link href="/admin/sellers" className={buttonClassName()}>
          승인 대기 보기
        </Link>
      }
    >
      <div className="flex gap-4">
        <StatTile label="판매자 승인 대기" value="3" unit="건" />
        <StatTile label="예치금 환급 요청" value="2" unit="건" />
        <Link href="/admin/holds" className={cn('flex flex-1 rounded-lg', FOCUS_RING)}>
          <StatTile label="지급보류 중" value="2" unit="건" />
        </Link>
      </div>

      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard
          title="오늘 정산 지급 예정 · 11/20"
          aside={
            <Link href="/admin/settlements" className={LINK}>
              정산 · 환급 처리
            </Link>
          }
          className="min-w-130 flex-1"
        >
          <StatTile
            tone="accent"
            label="지급 예정액 (판매자 2명)"
            value="₩4,536,000"
            className="flex-none"
          />
          <DataTable
            label="오늘 지급 예정"
            rows={PAYOUTS}
            rowKey={(r) => r.id}
            columns={[
              { key: 'seller', header: '판매자', render: (r) => r.seller },
              { key: 'id', header: '정산 ID', width: 'w-39', render: (r) => r.id },
              {
                key: 'amount',
                header: '지급액',
                align: 'right',
                width: 'w-27.5',
                render: (r) => r.amount,
              },
              {
                key: 'status',
                header: '상태',
                width: 'w-21',
                render: (r) => <Badge tone={r.tone}>{r.status}</Badge>,
              },
            ]}
          />
          <p className="text-body2-regular text-text-tertiary">
            보류된 금액은 지급 완료로 바꿀 수 없어요.
          </p>
        </ConsoleCard>

        <ConsoleCard
          title="예치금 환급 요청"
          aside={
            <Link href="/admin/settlements?tab=refunds" className={LINK}>
              정산 · 환급 처리
            </Link>
          }
          className="min-w-130 flex-1"
        >
          <DataTable
            label="예치금 환급 요청"
            rows={REFUNDS}
            rowKey={(r) => `${r.at}-${r.member}`}
            columns={[
              { key: 'at', header: '요청일', align: 'right', width: 'w-22.5', render: (r) => r.at },
              { key: 'member', header: '회원', width: 'w-42.5', render: (r) => r.member },
              { key: 'amount', header: '금액', align: 'right', render: (r) => r.amount },
              {
                key: 'status',
                header: '상태',
                width: 'w-27',
                render: (r) => <Badge tone={r.tone}>{r.status}</Badge>,
              },
            ]}
          />
        </ConsoleCard>
      </div>
    </ConsolePage>
  )
}
