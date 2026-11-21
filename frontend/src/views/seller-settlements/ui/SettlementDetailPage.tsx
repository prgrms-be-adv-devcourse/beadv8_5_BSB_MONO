'use client'

import Link from 'next/link'
import type { ReactNode } from 'react'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import {
  formatDayWithWeekday,
  formatDeduction,
  PAYOUT_STAGE,
  payoutStatusLabel,
} from '@/entities/payout'
import { memberApi, payoutApi, unwrapData } from '@/shared/api'
import { bankName } from '@/shared/config'
import { formatNumber } from '@/shared/lib'
import { Alert, buttonClassName, KeyValue, Skeleton } from '@/shared/ui'

import { errorText, Named, PillLink, StatusText } from './parts'

// 시안 2070:69311 정산 상세 · 2070:70381 1차 정산 지급보류
// 금액은 정산 API 응답 그대로 쓴다. 빼는 금액에는 − 기호만 붙인다.
// shortcut: 건별 내역 구역은 정산 ID로 정산 항목(주문번호 · 종목 포함)을 주는 API가 없어 그리지 않는다
export function SettlementDetailPage({ payoutCode }: { payoutCode: string }) {
  const payouts = payoutApi.usePayouts({ code: payoutCode }, { query: { select: unwrapData } })
  const payout = payouts.data?.[0]

  return (
    <ConsolePage
      title="정산 상세"
      backLink={<PillLink href="/seller/settlements">정산</PillLink>}
      description={
        payout &&
        `${payout.raceName} · ${PAYOUT_STAGE[payout.payoutStage]} 정산 · ${payout.payoutCode}`
      }
    >
      {payouts.isError ? (
        <Alert
          tone="danger"
          title="정산 내역을 불러오지 못했어요"
          description={errorText(payouts.error)}
        />
      ) : !payouts.data ? (
        <Skeleton className="h-80 w-full" />
      ) : !payout ? (
        <Alert tone="danger" title="정산 내역을 찾을 수 없어요" />
      ) : (
        <div className="flex gap-6">
          <Named label="지급액 계산" className="flex min-w-0 flex-1">
            <ConsoleCard title="지급액 계산" className="min-w-0 flex-1 gap-1 p-6.25">
              <AmountRow label="판매액" value={`${formatNumber(payout.salesAmount)}원`} />
              <AmountRow label="환불액" value={formatDeduction(payout.refundAmount)} />
              {/* 수수료와 1차 정산금은 최종 정산에서만 뺀다 */}
              {payout.payoutStage === '최종_정산' ? (
                <>
                  <AmountRow label="수수료" value={formatDeduction(payout.feeAmount)} />
                  <AmountRow
                    label="1차 정산금"
                    value={formatDeduction(payout.advanceDeductionAmount)}
                  />
                </>
              ) : (
                // 1차 지급액은 판매액 − 환불액이 아니라 장마다 가격 × 40%다(백엔드 PayoutStage). 계산은 하지 않고 기준만 알린다
                <p className="text-caption-regular text-text-secondary">
                  1차 정산금은 참가권 가격의 40%예요. 수수료는 최종 정산에서 빼요.
                </p>
              )}
              <div className="mt-3 border-t border-border-default pt-1">
                <div className="flex h-12 items-end justify-between">
                  <span className="pb-1 text-body1-bold">지급액</span>
                  <span className="text-title2">{formatNumber(payout.payoutAmount)}원</span>
                </div>
              </div>
            </ConsoleCard>
          </Named>

          <PayCard status={payout.status} payDate={payout.paidAt ?? payout.scheduledPayDate} />
        </div>
      )}
    </ConsolePage>
  )
}

function AmountRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex h-10 items-center justify-between text-body1-regular">
      <span>{label}</span>
      <span>{value}</span>
    </div>
  )
}

function PayCard({ status, payDate }: { status: string; payDate: string }) {
  const account = memberApi.useFindMyPayoutAccount({ query: { select: unwrapData, retry: false } })
  const held = status === '지급보류'
  const noAccount = account.data?.registered === false

  let accountText: ReactNode = <Skeleton className="h-6 w-40" />
  if (account.isError) accountText = '-'
  else if (account.data) {
    accountText = account.data.registered
      ? `${bankName(account.data.bankCode ?? '')} ****-${account.data.accountNumberLast4 ?? ''}`
      : '없음'
  }

  return (
    <Named label="지급" className="flex w-100 shrink-0">
      <ConsoleCard title="지급" className="w-full p-6.25">
        <div className="flex flex-col">
          <KeyValue
            className="h-12"
            label="상태"
            value={<StatusText {...payoutStatusLabel(status)} />}
          />
          <KeyValue className="h-12" label="지급일" value={formatDayWithWeekday(payDate)} />
          <KeyValue className="h-12" label="받는 계좌" value={accountText} />
        </div>
        {/* 계좌가 없어 멈춘 지급만 이유를 쓴다. 다른 보류 사유는 이 응답으로 알 수 없다 */}
        {held && noAccount && (
          <div className="flex items-center justify-between gap-4">
            <span className="text-body1-medium whitespace-nowrap text-text-danger">
              정산 계좌가 없어 지급을 멈췄어요
            </span>
            <Link
              href="/seller/settings"
              className={buttonClassName({
                variant: 'ghost',
                size: 's',
                className: 'rounded-full',
              })}
            >
              계좌 등록하기
            </Link>
          </div>
        )}
      </ConsoleCard>
    </Named>
  )
}
