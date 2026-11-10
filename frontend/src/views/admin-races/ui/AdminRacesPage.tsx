'use client'

import { useState } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { cn } from '@/shared/lib'
import { Badge, DataTable, FOCUS_RING, KeyValue, type BadgeTone } from '@/shared/ui'

type Race = {
  id: string
  title: string
  seller: string
  status: string
  tone: BadgeTone
  sold: string
  date: string
}

// TODO(api): 관리자 대회 목록 · 상세 API로 바꾼다. 시안 예시 값.
const RACES: Race[] = [
  {
    id: 'trail',
    title: '2026 가을 트레일런',
    seller: '나이트런컴퍼니',
    status: '판매 중',
    tone: 'brand',
    sold: '420 / 800',
    date: '11/22',
  },
  {
    id: 'cookierun',
    title: '2026 용감한 쿠키RUN in 서울',
    seller: '데브시스터즈러닝',
    status: '판매 중',
    tone: 'brand',
    sold: '2,410 / 3,000',
    date: '11/1',
  },
  {
    id: 'chuncheon',
    title: '2026 춘천 나이트런',
    seller: '춘천러닝클럽',
    status: '판매 중',
    tone: 'brand',
    sold: '610 / 1,000',
    date: '10/25',
  },
  {
    id: 'busan',
    title: '2027 부산 바다 10K',
    seller: '데브시스터즈러닝',
    status: '작성 중',
    tone: 'neutral',
    sold: '0 / 2,000',
    date: '3/14',
  },
  {
    id: 'moon',
    title: '2026 한가위 달빛런',
    seller: '나이트런컴퍼니',
    status: '종료',
    tone: 'neutral',
    sold: '500 / 500',
    date: '10/4',
  },
  {
    id: 'hangang',
    title: '2026 봄 한강 10K',
    seller: '데브시스터즈러닝',
    status: '종료',
    tone: 'neutral',
    sold: '1,200 / 1,200',
    date: '5/17',
  },
]

// 피그마 V2 · A05 대회 관리 (관리자는 보기만 하고, 대회 정보는 판매자가 고친다)
export function AdminRacesPage() {
  const [selectedId, setSelectedId] = useState('cookierun')
  const race = RACES.find((r) => r.id === selectedId) ?? RACES[1]

  return (
    <ConsolePage
      title="대회 관리"
      description="등록된 대회를 조회해요. 대회 정보는 판매자가 등록하고 고쳐요."
    >
      <div className="flex flex-wrap items-start gap-6">
        <ConsoleCard
          title={`대회 ${RACES.length}개`}
          aside={
            <button
              type="button"
              className={cn('rounded-sm text-body2-medium text-text-brand', FOCUS_RING)}
            >
              대회명·판매자로 찾기
            </button>
          }
          className="min-w-130 flex-1"
        >
          <DataTable
            label="등록된 대회"
            rows={RACES}
            rowKey={(r) => r.id}
            selected={selectedId}
            onRowClick={(r) => setSelectedId(r.id)}
            columns={[
              { key: 'title', header: '대회', render: (r) => r.title },
              { key: 'seller', header: '판매자', width: 'w-35', render: (r) => r.seller },
              {
                key: 'status',
                header: '상태',
                width: 'w-24',
                render: (r) => <Badge tone={r.tone}>{r.status}</Badge>,
              },
              {
                key: 'sold',
                header: '판매 / 정원',
                align: 'right',
                width: 'w-30',
                render: (r) => r.sold,
              },
              {
                key: 'date',
                header: '대회일',
                align: 'right',
                width: 'w-18',
                render: (r) => r.date,
              },
            ]}
          />
        </ConsoleCard>

        <ConsoleCard title="대회 상세" className="w-95 shrink-0">
          <p className="text-body2-regular text-text-secondary">{race.title}</p>
          <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
            <KeyValue label="상태" value={race.status} />
            <KeyValue label="판매 / 정원" value={`${race.sold}명`} />
            <KeyValue label="대회일" value="11/1 (일) 08:00" />
            <KeyValue label="접수 기간" value="10/14 (수) 10:00 ~ 10/31 (토) 18:00" />
            <KeyValue label="환불 기한" value="10/31 (토) 23:59" />
            <KeyValue label="공개 범위" value="전체 공개" />
          </div>
          <p className="text-body2-regular text-text-secondary">판매자</p>
          <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
            <KeyValue label="판매자" value={race.seller} />
            <KeyValue label="구분" value="사업자 · 123-45-67890" />
            <KeyValue label="정산 계좌" value="국민 ****4567" />
          </div>
          <p className="text-body2-regular text-text-secondary">정산 일정</p>
          <div className="flex flex-col gap-3 rounded-md bg-bg-subtle p-4">
            <KeyValue label="정산" value="11/20 (금) · 대회 종료 뒤 한 번에" />
          </div>
        </ConsoleCard>
      </div>
    </ConsolePage>
  )
}
