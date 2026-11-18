'use client'

import { useQuery } from '@tanstack/react-query'

import { Badge, buttonClassName } from '@/shared/ui'

import { myRecordCertsQueryOptions } from '../api/record-cert-api'
import { latestSubmission, REASON_LABEL, SUBMISSION_STATUS } from '../lib/cert-form'

/**
 * 참가권 상세의 "기록증 제출" 구역. 결제 완료이고 기록증 규칙이 있는 종목일 때만 화면이 그린다.
 * 최근 제출이 있으면 상태 · 사유를 보여 주고, 반려 · 승인 불가 · 취소됨이면 다시 낼 수 있다.
 */
export function RecordCertEntry({ orderItemId, href }: { orderItemId: number; href: string }) {
  const list = useQuery(myRecordCertsQueryOptions())
  if (!list.data) return null
  const { latest, canSubmit } = latestSubmission(list.data, orderItemId)
  const status = latest && SUBMISSION_STATUS[latest.status]
  const reasons = [
    ...(latest?.reasons.map((r) => REASON_LABEL[r]) ?? []),
    latest?.denyReason,
  ].filter(Boolean)

  return (
    <section className="flex flex-col gap-3 border-t-8 border-bg-subtle px-4 py-6">
      <div className="flex items-center justify-between">
        <h2 className="text-[18px] leading-6.5 font-bold tracking-[-0.015em]">기록증</h2>
        {status && <Badge tone={status.tone}>{status.label}</Badge>}
      </div>
      <p className="text-body2-regular text-text-secondary">
        {latest
          ? reasons.join(' · ') || '제출한 기록증을 확인하고 있어요.'
          : '완주 기록증을 내면 참가 자격을 확인해 드려요.'}
      </p>
      {canSubmit && (
        // 읽기 화면은 WASM 스레드를 쓰려고 응답 헤더(교차 출처 격리)가 다르다. 문서를 새로 받아야 헤더가 적용돼 Link 대신 a를 쓴다
        <a
          href={href}
          className={buttonClassName({ variant: latest ? 'secondary' : 'primary', size: 'l' })}
        >
          {latest ? '다시 제출하기' : '기록증 제출'}
        </a>
      )}
    </section>
  )
}
