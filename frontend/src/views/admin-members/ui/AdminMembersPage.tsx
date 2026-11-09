'use client'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  Alert,
  Badge,
  Button,
  DataTable,
  KeyValue,
  Select,
  TextField,
  type BadgeTone,
} from '@/shared/ui'

type Order = {
  id: string
  product: string
  amount: string
  status: string
  tone: BadgeTone
  paidAt: string
}

// TODO(api): 회원 · 주문 조회 API로 바꾼다. 시안 예시 값.
const ORDERS: Order[] = [
  {
    id: 'R-20261004-0012',
    product: '쿠키RUN · 10K 참가권',
    amount: '₩45,000',
    status: '참가 확정',
    tone: 'brand',
    paidAt: '10/4',
  },
  {
    id: 'R-20260920-0388',
    product: '춘천 나이트런 · 10K',
    amount: '₩45,000',
    status: '결제 완료',
    tone: 'success',
    paidAt: '9/20',
  },
  {
    id: 'R-20260502-0071',
    product: '봄 한강 10K',
    amount: '₩40,000',
    status: '환불 완료',
    tone: 'success',
    paidAt: '5/2',
  },
]

const KEYS = [
  { value: 'nickname', label: '회원 닉네임' },
  { value: 'order', label: '주문번호' },
]

// 피그마 V2 · A04 회원 · 주문 조회
export function AdminMembersPage() {
  return (
    <ConsolePage title="회원 · 주문 조회" description="회원 닉네임이나 주문번호로 찾아요.">
      <Alert
        title="개인정보를 조회하면 기록이 남아요"
        description="누가 언제 어떤 정보를 봤는지 남겨요. 업무에 필요한 만큼만 조회해 주세요."
      />
      <ConsoleCard>
        <div className="flex items-end gap-4">
          <Select label="검색 기준" items={KEYS} defaultValue="nickname" className="w-80" />
          <TextField label="검색어" defaultValue="달리는곰" className="flex-1" />
          {/* TODO(api): 조회 */}
          <Button>조회</Button>
        </div>
      </ConsoleCard>
      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard title={`달리는곰의 주문 ${ORDERS.length}건`} className="min-w-130 flex-1">
          <DataTable
            label="회원 주문"
            rows={ORDERS}
            rowKey={(o) => o.id}
            columns={[
              { key: 'id', header: '주문번호', width: 'w-42.5', render: (o) => o.id },
              { key: 'product', header: '상품', render: (o) => o.product },
              {
                key: 'amount',
                header: '금액',
                align: 'right',
                width: 'w-24',
                render: (o) => o.amount,
              },
              {
                key: 'status',
                header: '상태',
                width: 'w-29',
                render: (o) => <Badge tone={o.tone}>{o.status}</Badge>,
              },
              {
                key: 'paidAt',
                header: '결제일',
                align: 'right',
                width: 'w-21',
                render: (o) => o.paidAt,
              },
            ]}
          />
        </ConsoleCard>
        <ConsoleCard title="회원 상세" className="w-100 shrink-0">
          <div className="flex items-center gap-3">
            <span className="flex size-10 items-center justify-center rounded-full bg-bg-brand-subtle text-body2-medium text-text-brand">
              달
            </span>
            <div className="flex flex-col gap-0.5">
              <p className="text-body1-medium">달리는곰</p>
              <p className="text-body2-regular text-text-secondary">가입 2025. 3. 2 · 일반 회원</p>
            </div>
          </div>
          <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
            <KeyValue label="예치금 잔액" value="₩45,000" />
            <KeyValue label="참가권" value="2장 (쿠키RUN, 춘천 나이트런)" />
            <KeyValue label="판매자" value="아니요" />
          </div>
          <KeyValue label="휴대폰" value="010-****-1234" />
          {/* TODO(api): 개인정보 전체 보기 (조회 기록 남김) */}
          <Button variant="secondary" className="w-full">
            개인정보 전체 보기
          </Button>
          <p className="text-caption-regular text-text-tertiary">
            전체 보기를 누르면 조회 기록이 남아요.
          </p>
        </ConsoleCard>
      </div>
    </ConsolePage>
  )
}
