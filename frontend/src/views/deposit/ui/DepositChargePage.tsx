'use client'

import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { ChargeHeldAlert, MIN_CHARGE, requestCharge } from '@/features/deposit-charge'
import { useMyBalance } from '@/entities/wallet'
import { cashApi } from '@/shared/api'
import { Alert, BottomCta, Button, KeyValue } from '@/shared/ui'

const STEPS = [10_000, 50_000, 100_000]
const won = (n: number) => n.toLocaleString('ko-KR')

// 피그마 V2 · M21 예치금 충전 (예치금 M17에서 들어오는 충전)
// 결제 중 모자란 충전은 결제 화면(M07)이 바로 토스 결제창을 띄우므로 이 화면을 거치지 않는다.
// 그래서 시안 위쪽의 "결제에 ₩N이 부족해요" 안내는 두지 않는다.
export function DepositChargePage() {
  const { balance } = useMyBalance()
  const queryClient = useQueryClient()
  const [amount, setAmount] = useState(MIN_CHARGE)
  const [charging, setCharging] = useState(false)
  const [failed, setFailed] = useState(false)
  const [held, setHeld] = useState(false)

  const charge = () => {
    setCharging(true)
    setFailed(false)
    requestCharge({ amount })
      .then((result) => {
        if (result !== 'redirecting') setCharging(false)
        setHeld(result === 'held')
        // 앞선 충전을 서버가 이미 맞췄을 수 있어 잔액을 다시 불러 보여 준다
        if (result === 'held') {
          void queryClient.invalidateQueries({ queryKey: cashApi.getGetBalanceQueryKey() })
        }
      })
      .catch(() => {
        setCharging(false)
        setFailed(true)
      })
  }

  return (
    <>
      <AppHeader type="sub" title="예치금 충전" />
      <main className="flex flex-1 flex-col gap-4 px-4 py-6">
        {/* 결제창을 띄우지 못했을 때. 시안에 문구가 없어 결제 실패(M07-3) 문구를 쓴다 */}
        {failed && (
          <Alert
            tone="danger"
            title="결제하지 못했어요"
            description="잠시 뒤 다시 시도해 주세요."
          />
        )}
        {held && <ChargeHeldAlert />}
        <section className="flex flex-col gap-4">
          <label htmlFor="charge-amount" className="text-body2-medium">
            충전할 금액
          </label>
          <div className="flex items-baseline justify-end gap-1 border-b border-border-strong px-1 py-3">
            <input
              id="charge-amount"
              inputMode="numeric"
              value={won(amount)}
              onChange={(e) => setAmount(Number(e.target.value.replace(/\D/g, '')) || 0)}
              className="w-full bg-transparent text-right font-display text-number-s outline-none"
            />
            <span className="text-body2-medium">원</span>
          </div>
          {amount < MIN_CHARGE && (
            <p className="text-caption-regular text-text-tertiary">1만 원부터 충전할 수 있어요.</p>
          )}
          <div className="flex gap-2">
            {STEPS.map((step) => (
              <Button
                key={step}
                variant="secondary"
                className="flex-1"
                onClick={() => setAmount((a) => a + step)}
              >
                +{step / 10_000}만
              </Button>
            ))}
          </div>
          <div className="flex flex-col gap-2 border-t border-border-default pt-4">
            <KeyValue
              label="지금 예치금"
              value={balance === undefined ? '-' : `${won(balance)}원`}
            />
            <div className="flex items-center justify-between">
              <span className="text-body2-regular text-text-tertiary">충전 후 예치금</span>
              <span className="text-body2-bold">
                {balance === undefined ? '-' : `${won(balance + amount)}원`}
              </span>
            </div>
          </div>
        </section>
        <section>
          <KeyValue label="충전 방법" value="토스페이먼츠 (카드 · 계좌이체)" />
        </section>
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          <Button size="l" disabled={amount < MIN_CHARGE || charging} onClick={charge}>
            {won(amount)}원 충전하기
          </Button>
        }
      />
    </>
  )
}
