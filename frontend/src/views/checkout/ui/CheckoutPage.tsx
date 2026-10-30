'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState, type ReactNode } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { SAMPLE_PARTICIPANT } from '@/features/booking'
import { chargeAmountFor, requestCharge } from '@/features/deposit-charge'
import { useQueueAdmission } from '@/features/queue-admission'
import { parseKstDateTime, RACE_DETAIL, raceDetailQueryOptions } from '@/entities/race'
import { invalidateTicketQueries } from '@/entities/ticket'
import { orderApi, unwrapData, type orderModel } from '@/shared/api'
import { formatNumber, orderTrackItems, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  buttonClassName,
  Checkbox,
  EmptyState,
  KeyValue,
  Radio,
  RadioGroup,
  Skeleton,
  Stepper,
  TimerBar,
  type IconName,
} from '@/shared/ui'

import { useDepositBalance } from '../api/deposit-balance'
import { splitPayment } from '../lib/deposit'
import { toPayFailure, type PayFailure } from '../lib/pay-error'
import { useHoldCountdown } from '../model/use-hold-countdown'

export type CheckoutState = 'default' | 'offline' | 'timeout' | 'soldout' | 'failed'

type ResultView = 'timeout' | 'soldout' | 'failed'

// 결제가 끝나지 못한 결과 화면 (M07-1 시간 초과 · M07-2 자리 마감 · M07-3 실패)
const RESULT: Record<
  ResultView,
  {
    icon: IconName
    title: string
    description: ReactNode
    action: string
    /** 대회를 모를 때(미리보기) 가는 곳. 시간 초과는 대회를 알면 그 대회 상세(M02)로 간다 */
    href: '/races' | '/checkout'
  }
> = {
  timeout: {
    icon: 'CircleAlert',
    title: '결제 시간이 지났어요',
    description: (
      <>
        입장 시간이 끝나 잡아 둔 자리가 풀렸어요.
        <br />
        충전한 예치금은 그대로 남아 있어요.
      </>
    ),
    action: '대회 다시 보기',
    href: '/races',
  },
  soldout: {
    icon: 'TriangleAlert',
    title: '남은 자리가 없어요',
    description: (
      <>
        결제하는 동안 정원이 다 찼거나 접수가 끝났어요.
        <br />
        결제된 금액은 없어요.
      </>
    ),
    action: '다른 대회 보기',
    href: '/races',
  },
  failed: {
    icon: 'CircleAlert',
    title: '결제하지 못했어요',
    description: (
      <>
        잠시 뒤 다시 시도해 주세요.
        <br />
        결제 시간 안이면 잡아 둔 자리는 그대로예요.
      </>
    ),
    action: '다시 결제하기',
    href: '/checkout',
  },
}

// ?preview=offline 시안에서 보여 주는 예치금 잔액과 남은 시간 (5분 10초)
const OFFLINE_PREVIEW_BALANCE = 40_000
const OFFLINE_PREVIEW_SECONDS = 310

const won = (amount: number) => `₩${formatNumber(amount)}`

// 피그마 V2 · M07 결제 · 부족분 충전 (+ 연결 끊김, M07-1~3 결과)
// 결제 화면은 주문(/checkout?orderId=)으로 그린다. 남은 시간은 대기열 입장 끝 시각(= 결제 기한)까지 센다.
// 대기열 화면에서 받은 입장 상태를 이어 쓰고, 입장 상태를 모르면 주문의 결제 기한(paymentDueAt)까지 센다.
// ?preview=offline|timeout|soldout|failed 시안은 API를 부르지 않고 시안 예시 값을 보여 준다.
// ?result=timeout&raceId= 는 입장 시간이 끝나 신청 · 대기열 화면에서 넘어온 시간 초과 화면(M07-1)이다.
//
// 예치금이 모자라면(PRO-82): 결제 API가 409 ORDER_INSUFFICIENT_BALANCE + 부족 금액을 돌려주면 그만큼(최소 1만 원)
// 토스 결제창으로 바로 충전한다. 충전 화면(M21)을 거치지 않는다 — 충전까지 결제 기한(10분) 안에 끝나야 해서다.
// 충전되면 결제창이 &charged=1을 붙여 이 화면으로 돌려보내고, 같은 주문으로 결제 API를 다시 부른다(서버가 잔액을 다시 본다).
// 충전이 안 되면 &charge=failed로 돌아와 결제 실패(M07-3)를 보여 준다. 기한은 늘리지 않는다(PRO-168).
// 남은 시간은 따로 들고 다니지 않는다. 돌아오면 주문의 결제 기한과 대기열 입장 끝 시각으로 다시 센다.
export function CheckoutPage({
  state = 'default',
  orderId,
  raceId,
  charged = false,
  chargeFailed = false,
}: {
  state?: CheckoutState
  /** state가 default일 때 결제할 주문 */
  orderId?: number
  /** state가 timeout일 때 "대회 다시 보기"가 갈 대회 */
  raceId?: number
  /** 결제 중 충전하고 돌아옴: 들어오자마자 같은 주문으로 다시 결제한다 */
  charged?: boolean
  /** 결제 중 충전이 안 돼 돌아옴: 결제 실패(M07-3)를 보여 준다 */
  chargeFailed?: boolean
}) {
  if (state === 'timeout' || state === 'soldout' || state === 'failed') {
    return <CheckoutResult view={state} raceId={state === 'timeout' ? raceId : undefined} />
  }
  if (state === 'offline') return <OfflinePreview />
  // 주문 없이 들어오는 경우는 page.tsx에서 notFound()로 막는다
  if (orderId === undefined) return <CheckoutResult view="failed" />
  return <OrderCheckout orderId={orderId} charged={charged} chargeFailed={chargeFailed} />
}

function CheckoutResult({
  view,
  raceId,
  onRetry,
}: {
  view: ResultView
  /** 시간 초과 화면의 "대회 다시 보기"가 갈 대회. 모르면 대회 목록으로 간다 */
  raceId?: number
  onRetry?: () => void
}) {
  const result = RESULT[view]
  const href =
    view === 'timeout' && raceId !== undefined ? (`/races/${raceId}` as const) : result.href
  return (
    <>
      <AppHeader type="sub" title="결제" close backHref="/" />
      <main className="flex flex-1 items-center justify-center px-4 py-6">
        <EmptyState icon={result.icon} title={result.title} description={result.description} />
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          onRetry ? (
            <Button size="l" onClick={onRetry}>
              {result.action}
            </Button>
          ) : (
            <Link href={href} className={buttonClassName({ size: 'l' })}>
              {result.action}
            </Link>
          )
        }
      />
    </>
  )
}

/** 연결 끊김 시안 (M07 오프라인). 결제를 누른 뒤라 동의는 체크된 채로 둔다. */
function OfflinePreview() {
  const [expiresAt] = useState(() => Date.now() + OFFLINE_PREVIEW_SECONDS * 1000)
  const remaining = useHoldCountdown(expiresAt)
  const [agreed, setAgreed] = useState(true)
  const race = RACE_DETAIL
  const option = race.options[0]
  if (remaining === 0) return <CheckoutResult view="timeout" />
  return (
    <CheckoutForm
      offline
      remaining={remaining}
      summary={`${race.title} · ${option.name} · ${SAMPLE_PARTICIPANT.name}`}
      amount={option.price}
      balance={OFFLINE_PREVIEW_BALANCE}
      refund={race.refund}
      agreed={agreed}
      onAgreedChange={setAgreed}
      action={
        <Button size="l" disabled={!agreed}>
          다시 시도하기
        </Button>
      }
    />
  )
}

function OrderCheckout({
  orderId,
  charged,
  chargeFailed,
}: {
  orderId: number
  charged: boolean
  chargeFailed: boolean
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const completeHref = `/checkout/complete?orderId=${orderId}` as const
  const order = orderApi.useFindById1(orderId, { query: { select: unwrapData } })
  const raceId = order.data?.raceId
  const race = useQuery({
    ...raceDetailQueryOptions(String(raceId)),
    enabled: raceId !== undefined,
  })
  const deposit = useDepositBalance()
  // 참가자 이름은 참가 신청 API로 불러온다 (예매 흐름 스토어는 새로고침하면 비어서 쓰지 않는다)
  const participationId = order.data?.items?.[0]?.participationId
  const participation = orderApi.useFindById(participationId ?? 0, {
    query: { enabled: participationId !== undefined, select: unwrapData },
  })
  // 필수 동의는 결제 화면에 들어올 때마다 사용자가 직접 체크한다.
  const [agreed, setAgreed] = useState(false)
  const [failure, setFailure] = useState<PayFailure | null>(
    chargeFailed ? { kind: 'failed' } : null,
  )
  // 토스 결제창을 띄우는 중(페이지를 떠나기 전까지)
  const [charging, setCharging] = useState(false)

  // 남은 시간은 대기열 입장 끝 시각과 주문의 결제 기한 중 이른 쪽까지 센다.
  // 결제 API는 입장을 다시 확인하지 않고 결제 기한만 보므로, 기한이 지난 주문을 새로고침해 입장 확인이 새 시간을 잡아도 기한을 넘기지 않는다.
  // 입장 상태를 묻는 중이거나 입장 중이 아니면(모름) 주문의 결제 기한만 쓴다.
  const admission = useQueueAdmission(raceId, order.data?.items?.[0]?.raceCourseId, 'follow')
  const dueAt = order.data?.paymentDueAt
  const dueDeadline = dueAt ? parseKstDateTime(dueAt).getTime() : null
  const admissionDeadline = admission.isFetching ? null : admission.deadline
  const remaining = useHoldCountdown(
    admissionDeadline !== null && dueDeadline !== null
      ? Math.min(admissionDeadline, dueDeadline)
      : (admissionDeadline ?? dueDeadline),
  )

  const charge = (shortage: number) => {
    setCharging(true)
    requestCharge({ amount: chargeAmountFor(shortage), returnOrderId: orderId })
      .then((result) => {
        if (result === 'canceled') setCharging(false)
      })
      .catch(() => {
        setCharging(false)
        setFailure({ kind: 'failed' })
      })
  }

  // 잔액이 모자라 보여도 결제 API부터 부른다. 서버 잔액이 기준이고, 모자란 금액도 서버가 알려 준다.
  // auto: 충전하고 돌아와 저절로 다시 결제한 경우. 그때 또 모자라면(다른 탭에서 결제해 잔액이 줄었을 때 등)
  // 결제창을 저절로 다시 띄우지 않는다. 사용자가 두 번 충전하는 것처럼 보이고, 클릭 없이 띄운 결제창은 브라우저가 막을 수도 있다.
  // 잔액을 다시 불러 화면의 계산 · "충전하고 결제" 버튼으로 모자란 만큼을 보여 주고, 사용자가 눌러 충전한다.
  const pay = useMutation<orderModel.OrderDto, Error, { auto: boolean }>({
    mutationFn: async () => unwrapData(await orderApi.pay(orderId)),
    // 이 화면이 받아 둔 결제 전 주문 · 잔액이 캐시에 1분 동안 남는다. 지우지 않으면 결제 직후 참가권 상세가
    // 결제 전 주문을 읽어 '참가권을 찾을 수 없어요'가 나오고, 내 참가권의 예치금도 옛 값으로 보인다.
    // 결제한 주문만 다시 받은 뒤에 넘어간다 (받기 전에 상세를 열면 옛 주문을 잠깐 읽는다).
    // 잔액 · 참가 신청 등은 무효로만 표시한다. 다 기다리면 그중 하나가 실패해 재시도가 끝난 몇 초 뒤에야
    // 넘어가는데, 그때 사용자가 다른 화면에 가 있어도 완료 화면으로 다시 보낸다.
    // 다시 받은 주문이 PAID라 아래 effect도 완료 화면으로 보내므로, 같은 replace로 맞춰 기록이 두 번 남지 않게 한다.
    onSuccess: async () => {
      void invalidateTicketQueries(queryClient)
      await queryClient.refetchQueries(
        { queryKey: orderApi.getFindById1QueryKey(orderId) },
        { cancelRefetch: false },
      )
      router.replace(completeHref)
    },
    onError: (error, { auto }) => {
      const next = toPayFailure(error)
      if (next.kind === 'insufficient') {
        void deposit.refetch()
        if (!auto) charge(next.shortage)
        return
      }
      setFailure(next)
    },
  })

  const status = order.data?.status
  // 결제할 수 있는 주문이 보일 때 한 번. 모자란 금액은 잔액을 받은 뒤 화면 계산과 같은 값으로 보낸다
  const payable = status === 'CREATED' ? order.data : undefined
  // 충전을 다녀와 다시 열린 화면(charged)은 같은 주문의 결제 시작을 한 번 더 세지 않는다
  useTrackOnce(
    'begin_checkout',
    payable && !charged
      ? { items: orderTrackItems(payable), value: payable.totalAmount ?? 0 }
      : null,
  )
  const shortage =
    payable && deposit.balance !== undefined
      ? Math.max(0, (payable.totalAmount ?? 0) - deposit.balance)
      : 0
  useTrackOnce('deposit_short', shortage > 0 ? { shortage } : null)
  // 충전하고 돌아왔으면 같은 주문으로 한 번 다시 결제한다. 기한이 지났으면 부르지 않고 시간 초과 화면을 보여 준다.
  const resumed = useRef(false)
  const canResume = charged && status === 'CREATED' && remaining > 0 && failure === null
  useEffect(() => {
    if (!canResume || resumed.current) return
    resumed.current = true
    pay.mutate({ auto: true })
  }, [canResume, pay])
  // 이미 결제한 주문이면 완료 화면으로 보낸다 (뒤로 가기 · 새로고침).
  useEffect(() => {
    if (status === 'PAID') router.replace(completeHref)
  }, [status, completeHref, router])

  if (order.isError) return <CheckoutResult view="failed" onRetry={() => void order.refetch()} />
  if (!order.data || status === 'PAID') return <CheckoutSkeleton />
  if (status === 'EXPIRED' || remaining === 0 || failure?.kind === 'timeout') {
    return <CheckoutResult view="timeout" raceId={raceId} />
  }
  if (failure?.kind === 'soldout') return <CheckoutResult view="soldout" />
  if (failure?.kind === 'failed' || status !== 'CREATED') {
    return (
      <CheckoutResult
        view="failed"
        onRetry={() => {
          setFailure(null)
          void order.refetch()
        }}
      />
    )
  }

  const amount = order.data.totalAmount ?? 0
  const balance = deposit.balance
  const payment = balance === undefined ? undefined : splitPayment(amount, balance)
  const busy = pay.isPending || charging
  // 충전하고 돌아와 다시 결제하는 동안에는 동의를 다시 받지 않는다 (떠나기 전에 체크했다)
  const ready = (agreed || charged) && !busy

  return (
    <CheckoutForm
      remaining={remaining}
      summary={orderSummary(
        order.data,
        race.data ?? undefined,
        participation.data?.participant?.name,
      )}
      amount={amount}
      balance={balance}
      refund={race.data?.refund}
      agreed={agreed || charged}
      onAgreedChange={setAgreed}
      action={
        <Button size="l" disabled={!ready} onClick={() => pay.mutate({ auto: false })}>
          {payment && payment.charge > 0
            ? `${won(payment.charge)} 충전하고 결제`
            : `${won(amount)} 결제`}
        </Button>
      }
    />
  )
}

/** 대회 · 종목 · 참가자. 대회 상세를 아직 못 받았으면 주문 상품 이름을 쓴다. */
function orderSummary(
  order: orderModel.OrderDto,
  race: { title: string; options: { id: string; name: string }[] } | undefined,
  participantName: string | undefined,
) {
  const item = order.items?.[0]
  const option = race?.options.find((o) => o.id === String(item?.raceCourseId))
  const name = race && option ? `${race.title} · ${option.name}` : (item?.itemName ?? '')
  const participant = participantName ?? order.shippingAddress?.recipientName
  return participant ? `${name} · ${participant}` : name
}

function CheckoutSkeleton() {
  return (
    <>
      <AppHeader type="sub" title="결제" />
      <main aria-busy className="flex flex-1 flex-col gap-4 px-4 py-6">
        <Skeleton shape="rect" className="h-96" />
      </main>
    </>
  )
}

function CheckoutForm({
  offline = false,
  remaining,
  summary,
  amount,
  balance,
  refund,
  agreed,
  onAgreedChange,
  action,
}: {
  offline?: boolean
  remaining: number
  summary: string
  amount: number
  /** 예치금 잔액. 불러오지 못하면 undefined */
  balance?: number
  refund?: { label: string; value: string }[]
  agreed: boolean
  onAgreedChange: (agreed: boolean) => void
  action: ReactNode
}) {
  const payment = balance === undefined ? undefined : splitPayment(amount, balance)
  return (
    <>
      <AppHeader type="sub" title="결제" />
      {/* 대기열 내 차례(M05-Q2)에서 이어지는 남은 시간 띠 */}
      <TimerBar
        message={
          offline
            ? '연결이 끊긴 동안에도 시간은 흘러요'
            : '자리를 잡아 뒀어요. 시간 안에 결제해 주세요'
        }
        remainingSeconds={remaining}
      />
      <main className="flex flex-1 flex-col px-4 py-6">
        <div className="flex flex-col gap-7 rounded-lg bg-bg-surface p-5 shadow-sm inset-ring inset-ring-border-default">
          <Stepper steps={['종목 선택', '참가자 정보', '결제']} current={2} />

          {offline && (
            <Alert
              tone="danger"
              title="연결이 끊겼어요"
              description="결제는 한 번만 처리돼요. 다시 연결되면 결제가 됐는지 확인해 드려요."
            />
          )}

          <div className="flex flex-col gap-1">
            <p className="text-body-s text-text-secondary">{summary}</p>
            <p className="text-[32px] leading-11 font-bold tracking-[-0.015em]">{won(amount)}</p>
          </div>

          <div className="flex flex-col gap-3 border-t border-border-default pt-4">
            <KeyValue label="예치금에서" value={payment ? won(payment.fromDeposit) : '-'} />
            <KeyValue
              label="충전 (최소 1만 원)"
              value={payment ? `+${won(payment.charge)}` : '-'}
            />
            <KeyValue
              label="결제 뒤 남는 예치금"
              value={payment ? won(payment.balanceAfter) : '-'}
            />
            <p className="text-caption text-text-tertiary">
              1만 원보다 적게 모자라면 1만 원을 충전해요.
              <br />
              결제가 안 돼도 충전한 돈은 예치금으로 남아요.
            </p>
          </div>

          <fieldset className="flex flex-col gap-3">
            <legend className="mb-3 text-label-m">충전 수단</legend>
            <RadioGroup defaultValue="toss">
              <Radio value="toss" label="카드 · 간편결제 (토스페이먼츠)" />
            </RadioGroup>
          </fieldset>

          <div className="flex flex-col gap-3">
            {refund && (
              <div className="flex flex-col gap-2 rounded-md px-4 pt-3 pb-4 inset-ring inset-ring-border-default">
                <p className="text-body-s text-text-secondary">환불 규정</p>
                {refund.map((row) => (
                  <KeyValue key={row.label} label={row.label} value={row.value} />
                ))}
              </div>
            )}
            <Checkbox
              checked={agreed}
              onCheckedChange={onAgreedChange}
              label="[필수] 환불 기한을 확인했어요"
            />
          </div>
        </div>
      </main>
      <BottomCta className="sticky bottom-0 z-10" primary={action} />
    </>
  )
}
