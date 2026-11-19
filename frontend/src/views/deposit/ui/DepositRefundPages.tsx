'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { ApiError, cashApi, createIdempotencyKey, unwrapData } from '@/shared/api'
import { cashZod } from '@/shared/api-zod'
import {
  Alert,
  BottomCta,
  Button,
  buttonClassName,
  Icon,
  KeyValue,
  Select,
  Skeleton,
  TextField,
} from '@/shared/ui'

import { refundPlanQueryOptions, requestRefund } from '../api/refund-contract'
import {
  bankName,
  BANKS,
  clearLastWithdrawal,
  formatKstShort,
  maskAccount,
  parseLastWithdrawal,
  readLastWithdrawalRaw,
  saveLastWithdrawal,
  type BankCode,
} from '../lib/refund'

const wonText = (n: number) => `${n.toLocaleString('ko-KR')}원`
// 피그마 v4: 이름 칸 폭 160 · 88
const WIDE_KEY = '[&>span:first-child]:w-40'
const KEY_160 = '[&>span:first-child]:w-40'
const KEY_88 = '[&>span:first-child]:w-22'
const KEY_200 = '[&>span:first-child]:w-50'

// 환급이 끝나면 잔액 · 예치금 내역 · 환급 가능한 충전만 다시 불러온다
function refreshDeposit(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: cashApi.getGetBalanceQueryKey() }),
    queryClient.invalidateQueries({ queryKey: cashApi.getGetLedgersQueryKey() }),
    queryClient.invalidateQueries({ queryKey: cashApi.getGetRefundableChargesQueryKey() }),
  ])
}

// 시안에 입력 오류 문구가 없어, 다 채우기 전에는 버튼만 막는다 (서버 길이 제한은 생성된 zod 값을 쓴다)
const refundFormSchema = z.object({
  bankCode: z.enum(BANKS.map((b) => b.value) as [BankCode, ...BankCode[]]),
  accountNumber: z
    .string()
    .trim()
    .regex(/^[\d-]*\d[\d-]*$/)
    .max(cashZod.requestWithdrawalBodyAccountNumberMax),
  accountHolder: z.string().trim().min(1).max(cashZod.requestWithdrawalBodyAccountHolderMax),
})

type RefundFormValues = z.infer<typeof refundFormSchema>

/**
 * 같은 환급 신청인지 서버가 알아보는 키(Idempotency-Key 헤더)와 그 키로 처음 보낸 입력값.
 * 처음 누를 때 한 번 만들고, 성공하거나 서버가 4xx로 거절할 때까지 다시 눌러도 같은 키 · 같은 값을 보낸다.
 * 네트워크 오류·5xx는 서버에서 처리됐는지 모르므로 같은 키로 다시 보내야 두 번 빠져나가지 않는다.
 * 이때 서버는 처음 결과를 돌려주므로, 입력을 고쳐 다시 보내면 완료 화면에 실제로 보내지 않은 계좌가 나온다.
 * 그래서 처음 값을 키와 함께 두고 다시 보낼 때 그 값을 쓰며, 그동안(locked) 입력란을 잠근다.
 * 4xx는 저장되지 않은 요청이라 다음에는 새 키 · 지금 입력값을 쓴다.
 */
function usePendingWithdrawal() {
  const pending = useRef<{ key: string; values: RefundFormValues | null } | null>(null)
  const [locked, setLocked] = useState(false)
  return {
    locked,
    /** 이번에 보낼 키와 값. 결과를 모르는 요청이 남아 있으면 그때의 키 · 값 */
    take: (values: RefundFormValues | null) =>
      (pending.current ??= { key: createIdempotencyKey(), values }),
    /** 결과를 모르는 채로 끝남: 다음에도 같은 키 · 값을 보낸다 */
    lock: () => setLocked(true),
    /** 성공하거나 서버가 확실히 거절함: 다음에는 새 키 · 지금 입력값 */
    reset: () => {
      pending.current = null
      setLocked(false)
    },
  }
}

// 피그마 v4 · M22 예치금 환급받기. 신청하면 바로 환급이 끝난다(백엔드 PRO-84, 1차는 모의 처리).
export function DepositRefundPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const plan = useQuery({
    ...refundPlanQueryOptions(),
    enabled: useAuthStatus() === 'authenticated',
  })
  const [detailOpen, setDetailOpen] = useState(false)
  const pending = usePendingWithdrawal()
  const [notice, setNotice] = useState<{
    title: string
    description?: string
    info?: boolean
  } | null>(null)
  // 서버가 계좌로 보낼 몫이 있다고 알려 오면, 다시 불러온 충전 내역과 상관없이 계좌를 받는다
  const [needAccount, setNeedAccount] = useState(false)
  // 두 번 누르기: 버튼이 꺼지는 렌더보다 두 번째 클릭이 먼저 올 수 있어 요청 중인지를 바로 적어 둔다
  const inFlight = useRef(false)
  const {
    control,
    register,
    handleSubmit,
    formState: { isValid },
  } = useForm<RefundFormValues>({
    resolver: zodResolver(refundFormSchema),
    mode: 'onChange',
    defaultValues: { bankCode: '004', accountNumber: '', accountHolder: '' },
  })

  const withdraw = useMutation({
    mutationFn: async ({ key, values }: { key: string; values: RefundFormValues | null }) => {
      const account = values && {
        bankCode: values.bankCode,
        accountNumber: values.accountNumber.replace(/\D/g, ''),
        accountHolder: values.accountHolder.trim(),
      }
      const result = unwrapData(await requestRefund(account, key))
      return { result, values }
    },
    onSuccess: ({ result, values }) => {
      pending.reset()
      // 잔액이 바뀐 것을 예치금 화면들이 다시 불러오게 한다
      void refreshDeposit(queryClient)
      // 카드로 돌려줄 수 없는 몫이 계좌 정보가 없어 예치금에 되돌아왔다. 계좌를 받아 다시 신청하게 한다
      if (result.accountRequired) {
        setNeedAccount(true)
        const card =
          result.cardAmount > 0 ? `카드 결제취소 ${wonText(result.cardAmount)}은 요청했어요. ` : ''
        // 확인 중인 몫이 있으면 서버가 이 환급을 처리 중으로 두어, 바로 다시 신청하면 409가 난다
        const retry =
          result.processingAmount > 0
            ? `확인 중인 ${wonText(result.processingAmount)}이 정해진 뒤 다시 신청할 수 있어요.`
            : '계좌 정보를 넣고 다시 신청해 주세요.'
        setNotice({ title: `${card}남은 금액은 계좌로 보내야 해요. ${retry}` })
        return
      }
      saveLastWithdrawal({
        amount: result.withdrawnAmount,
        balance: result.balance,
        cardAmount: result.cardAmount,
        accountAmount: result.accountAmount,
        switchedToAccountAmount: result.switchedToAccountAmount,
        processingAmount: result.processingAmount,
        revertedAmount: result.revertedAmount,
        ...(values && {
          bankName: bankName(values.bankCode),
          maskedAccount: maskAccount(values.accountNumber.trim()),
          accountHolder: values.accountHolder.trim(),
        }),
        at: Date.now(),
      })
      router.replace('/me/deposit/refund/done')
    },
    onError: (error) => {
      // 서버가 확실히 거절한 요청(잔액 없음 400, 다른 회원 키 409 등)이면 다음에는 새 키를 쓴다
      if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
        pending.reset()
        // 계좌 필요(400)면 바뀐 충전 내역을 다시 불러온다
        void refreshDeposit(queryClient)
      } else {
        pending.lock()
      }
      if (error instanceof ApiError && error.code === 'WITHDRAWAL_ACCOUNT_REQUIRED') {
        setNeedAccount(true)
        setNotice({ title: '계좌로 돌려드릴 금액이 있어요. 계좌를 입력하고 다시 신청해 주세요.' })
      } else if (error instanceof ApiError && error.code === 'WITHDRAWAL_IN_PROGRESS') {
        setNotice({
          title: '처리 중인 환급이 끝나야 다시 신청할 수 있어요',
          description: '끝나면 알림으로 알려 드려요.',
          info: true,
        })
      } else {
        // 시안에 환급 실패 문구가 없어 결제 실패(M07-3)의 안내 문구를 쓴다
        setNotice({ title: '잠시 뒤 다시 시도해 주세요.' })
      }
    },
  })

  const submit = (values: RefundFormValues | null) => {
    if (inFlight.current) return
    inFlight.current = true
    setNotice(null)
    withdraw.mutate(pending.take(values), {
      onSettled: () => {
        inFlight.current = false
      },
    })
  }

  const data = plan.data
  const accountRequired = (data?.accountRequired ?? false) || needAccount
  // 결과를 모르는 요청이 남아 있으면(locked) 다시 불러온 금액이 0원이어도 같은 키로 다시 보낼 수 있게 둔다
  // accountRequired는 계좌 몫이 있거나 서버가 계좌를 요구했을 때만 참이므로, 거짓이면 계좌 검사를 건너뛰어도 된다
  const disabled =
    withdraw.isPending ||
    (!pending.locked && (!data || data.balance <= 0 || (accountRequired && !isValid)))

  return (
    <>
      <AppHeader type="sub" title="예치금 환급받기" />
      <form
        className="flex flex-1 flex-col"
        onSubmit={(event) => {
          // 계좌로 보낼 몫이 없으면 숨긴 계좌 입력을 검사하지 않고 계좌 없이 보낸다
          // 결과를 모르는 요청이 남아 있으면 그때의 값을 다시 보내므로 입력을 다시 검사하지 않는다
          if (accountRequired && !pending.locked) return void handleSubmit(submit)(event)
          event.preventDefault()
          submit(null)
        }}
      >
        <main className="flex flex-1 flex-col gap-4 px-4 py-6">
          {notice && (
            <Alert
              tone={notice.info ? 'info' : 'danger'}
              title={notice.title}
              description={notice.description}
            />
          )}
          <section className="flex flex-col gap-1">
            <p className="text-body1-regular text-text-secondary">환급받을 금액</p>
            {plan.isPending ? (
              <Skeleton shape="rect" className="h-11 w-36" />
            ) : (
              <p className="font-display text-number-m">{data ? wonText(data.balance) : '-'}</p>
            )}
            {plan.isError && (
              <p className="text-body2-regular text-text-secondary">
                환급받을 금액을 불러오지 못했어요.
              </p>
            )}
          </section>
          {data && !needAccount && data.balance > 0 && (
            <section className="flex flex-col gap-2 rounded-md border border-border-default bg-bg-surface p-4">
              {data.cardRefundAmount > 0 && (
                <KeyValue
                  label="카드로 돌아가는 금액"
                  value={wonText(data.cardRefundAmount)}
                  className={WIDE_KEY}
                />
              )}
              {data.accountRefundAmount > 0 && (
                <KeyValue
                  label="계좌로 받는 금액"
                  value={wonText(data.accountRefundAmount)}
                  className={WIDE_KEY}
                />
              )}
              {data.cardRefundAmount > 0 && (
                <KeyValue
                  label="카드 취소 신청 기한"
                  value={`${data.earliestCardCancelDate}까지`}
                  className={WIDE_KEY}
                />
              )}
            </section>
          )}
          {data && data.charges.length > 0 && (
            <section className="flex flex-col">
              <button
                type="button"
                aria-expanded={detailOpen}
                aria-controls="refund-charges"
                onClick={() => setDetailOpen((open) => !open)}
                className="flex items-center justify-between py-4 text-body1-bold"
              >
                충전별 내역 상세보기
                <Icon name={detailOpen ? 'ChevronUp' : 'ChevronDown'} className="size-5" />
              </button>
              {/* 버튼이 가리키는 목록은 늘 두고, 접혀 있을 때는 줄을 그리지 않는다 */}
              <ul id="refund-charges" hidden={!detailOpen} className="mt-3 flex flex-col">
                {detailOpen &&
                  data.charges.map((charge) => (
                    <li
                      key={charge.chargeId}
                      className="flex items-center justify-between gap-2 border-b border-border-default py-3 last:border-b-0"
                    >
                      <div className="flex min-w-0 flex-1 flex-col gap-1">
                        <p className="text-body2-medium">
                          {`${charge.approvedAt.slice(0, 10)} 충전 · ${wonText(charge.chargedAmount)}`}
                        </p>
                        <p className="text-caption-regular text-text-tertiary">
                          {charge.refundTo === 'CARD'
                            ? `결제한 카드로 · 카드 취소 ${charge.cardCancelDate}까지`
                            : `계좌로 · 카드 취소 기한(${charge.cardCancelDate})이 지났어요`}
                        </p>
                      </div>
                      <p className="text-body2-bold tabular-nums">
                        {wonText(charge.remainingAmount)}
                      </p>
                    </li>
                  ))}
              </ul>
            </section>
          )}
          {accountRequired && (
            <section className="flex flex-col gap-4 pt-4">
              <h2 className="text-body1-bold">환급 계좌</h2>
              <Controller
                control={control}
                name="bankCode"
                render={({ field }) => (
                  <Select
                    label="은행"
                    disabled={pending.locked}
                    items={[...BANKS]}
                    value={field.value}
                    onValueChange={(value) => value && field.onChange(value)}
                  />
                )}
              />
              <TextField
                label="계좌번호"
                inputMode="numeric"
                // 숫자와 -만 받는다. 은행마다 -를 넣는 자리가 달라 -는 막지 않는다
                filter={(v) => v.replace(/[^\d-]/g, '')}
                filterMessage="숫자와 -만 입력할 수 있어요"
                readOnly={pending.locked}
                {...register('accountNumber')}
              />
              <TextField
                label="예금주"
                helper="본인 명의 계좌만 쓸 수 있어요"
                readOnly={pending.locked}
                {...register('accountHolder')}
              />
            </section>
          )}
        </main>
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Button type="submit" size="l" disabled={disabled}>
              {data ? `${wonText(data.balance)} 환급 신청` : '환급 신청'}
            </Button>
          }
        />
      </form>
    </>
  )
}

/** 이 화면에 있는 동안 저장 값은 바뀌지 않아 구독할 것이 없다 */
const noSubscribe = () => () => {}

// 피그마 v4 · M22-1 예치금 환급받기 · 환급 완료 (10/16 즉시 완료로 시안 문구 수정)
export function DepositRefundDonePage() {
  const router = useRouter()
  // sessionStorage는 브라우저에만 있다. 서버 렌더에서는 undefined(로딩)로 두고, 브라우저에서 읽는다
  const raw = useSyncExternalStore(noSubscribe, readLastWithdrawalRaw, () => undefined)
  const withdrawal = useMemo(
    () => (raw === undefined ? undefined : parseLastWithdrawal(raw)),
    [raw],
  )
  useEffect(() => {
    if (withdrawal === null) router.replace('/me/deposit')
  }, [withdrawal, router])
  // 화면을 떠나면 저장 값을 지운다. 개발 모드 Strict Mode는 정리 직후 다시 붙이므로,
  // 바로 지우지 않고 다음 차례로 미뤘다가 다시 붙으면 취소한다
  const clearTimer = useRef<ReturnType<typeof setTimeout>>(undefined)
  useEffect(() => {
    clearTimeout(clearTimer.current)
    return () => {
      clearTimer.current = setTimeout(clearLastWithdrawal)
    }
  }, [])

  if (!withdrawal) {
    return (
      <>
        <AppHeader type="sub" title="환급 완료" close backHref="/me/deposit" />
        <main aria-busy className="flex flex-1 flex-col gap-4 px-4 py-6">
          <Skeleton shape="rect" className="h-64" />
        </main>
      </>
    )
  }

  // 카드 결제취소 결과나 확인 중인 몫이 남았거나, 예치금으로 되돌아온 몫이 있으면 끝난 것으로 보지 않는다
  const done =
    withdrawal.cardAmount === 0 &&
    withdrawal.processingAmount === 0 &&
    withdrawal.revertedAmount === 0
  const allReverted =
    withdrawal.revertedAmount > 0 && withdrawal.revertedAmount === withdrawal.amount

  const noAccount = !withdrawal.maskedAccount
  // 되돌아온 몫이 있는데 계좌를 넣지 않았으면 계좌를 넣고 다시 신청하게 한다 (피그마 2070:39011)
  const retryWithAccount = withdrawal.revertedAmount > 0 && noAccount
  // 라벨이 긴 줄(카드 결제취소 금액 · 처리 중인 금액)이 있으면 이름 칸을 넓힌다
  const keyWidth =
    withdrawal.revertedAmount > 0
      ? KEY_200
      : withdrawal.cardAmount > 0 || withdrawal.processingAmount > 0
        ? KEY_160
        : KEY_88

  return (
    <>
      <AppHeader
        type="sub"
        title={done ? '환급 완료' : '환급 신청 완료'}
        close
        backHref="/me/deposit"
      />
      <main className="flex flex-1 flex-col justify-center-safe gap-8 px-4 pt-6 pb-8">
        <h2 className="text-title2">
          {done
            ? withdrawal.cardAmount > 0
              ? `예치금 ${wonText(withdrawal.amount)}을 환급받았어요`
              : '예치금을 환급받았어요'
            : allReverted
              ? '예치금으로 되돌아왔어요'
              : `예치금 ${wonText(withdrawal.amount)} 환급을 신청했어요`}
        </h2>
        <section className="flex flex-col gap-4">
          <h3 className="text-body1-bold">{done ? '환급받은 내역' : '신청한 내역'}</h3>
          <div className="flex flex-col gap-3 rounded-md border border-border-default bg-bg-surface p-4">
            {withdrawal.cardAmount > 0 && (
              <KeyValue
                label="카드 결제취소 금액"
                value={wonText(withdrawal.cardAmount)}
                className={keyWidth}
              />
            )}
            {withdrawal.accountAmount > 0 && (
              <KeyValue
                label={withdrawal.cardAmount > 0 ? '계좌로 보낸 금액' : '환급 금액'}
                value={wonText(withdrawal.accountAmount)}
                className={keyWidth}
              />
            )}
            {withdrawal.processingAmount > 0 && (
              <KeyValue
                label="처리 중인 금액"
                value={wonText(withdrawal.processingAmount)}
                className={keyWidth}
              />
            )}
            {withdrawal.revertedAmount > 0 && (
              <KeyValue
                label="예치금으로 되돌린 금액"
                value={wonText(withdrawal.revertedAmount)}
                className={keyWidth}
              />
            )}
            {/* 계좌 · 예금주 이름은 Clarity 화면 녹화에서 가린다 */}
            {withdrawal.accountAmount > 0 && withdrawal.maskedAccount && (
              <div className="contents" data-clarity-mask="true">
                <KeyValue
                  label="받을 계좌"
                  value={`${withdrawal.bankName} ${withdrawal.maskedAccount}`}
                  className={keyWidth}
                />
                <KeyValue label="예금주" value={withdrawal.accountHolder} className={keyWidth} />
              </div>
            )}
            <KeyValue
              label="지금 예치금"
              value={wonText(withdrawal.balance)}
              className={keyWidth}
            />
            <KeyValue
              label={done ? '환급 일시' : '신청 일시'}
              value={formatKstShort(withdrawal.at)}
              className={keyWidth}
            />
          </div>
        </section>
        {(withdrawal.cardAmount > 0 ||
          withdrawal.switchedToAccountAmount > 0 ||
          withdrawal.revertedAmount > 0 ||
          withdrawal.processingAmount > 0) && (
          <section className="flex flex-col gap-4">
            <h3 className="text-body1-bold">알아 두세요</h3>
            <div className="flex flex-col gap-1 rounded-md border border-border-default bg-bg-surface p-4 text-body1-regular">
              {withdrawal.revertedAmount > 0 && (
                <>
                  <p className="text-text-warning">
                    일부 금액은 결제사 사정으로 카드 취소가 되지 않아 예치금으로 돌려드렸어요.
                  </p>
                  <p className="text-text-warning">
                    {noAccount
                      ? '계좌 정보를 넣고 다시 신청해 주세요.'
                      : '잠시 후 다시 신청해 주세요.'}
                  </p>
                </>
              )}
              {withdrawal.processingAmount > 0 && (
                <p className="text-text-warning">
                  {wonText(withdrawal.processingAmount)}은 아직 처리 중이에요. 끝나면 알림으로 알려
                  드려요.
                </p>
              )}
              {withdrawal.switchedToAccountAmount > 0 && (
                <p>
                  {wonText(withdrawal.switchedToAccountAmount)}은 카드 결제취소가 되지 않아 입력하신
                  계좌로 보냈어요.
                </p>
              )}
              {withdrawal.cardAmount > 0 && (
                <p>카드 결제취소는 카드사에 따라 며칠 걸릴 수 있어요.</p>
              )}
            </div>
          </section>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          retryWithAccount ? (
            <Link
              href="/me/deposit"
              replace
              className={buttonClassName({ variant: 'secondary', size: 'l' })}
            >
              예치금 보기
            </Link>
          ) : (
            <Link href="/" replace className={buttonClassName({ variant: 'ghost', size: 'l' })}>
              홈으로
            </Link>
          )
        }
        primary={
          retryWithAccount ? (
            <Link href="/me/deposit/refund" replace className={buttonClassName({ size: 'l' })}>
              계좌 넣고 다시 신청
            </Link>
          ) : (
            <Link href="/me/deposit" replace className={buttonClassName({ size: 'l' })}>
              예치금 보기
            </Link>
          )
        }
      />
    </>
  )
}
