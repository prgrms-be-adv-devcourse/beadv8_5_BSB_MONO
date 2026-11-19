'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { forgetKitOptionIds, readKitOptionIds, SAMPLE_PARTICIPANT } from '@/features/booking'
import {
  chargeAmountFor,
  ChargeHeldAlert,
  MAX_CHARGE,
  requestCharge,
} from '@/features/deposit-charge'
import { useQueueAdmission } from '@/features/queue-admission'
import {
  parseKstDateTime,
  RACE_DETAIL,
  raceDetailQueryOptions,
  RacePoster,
  type RaceDetail,
} from '@/entities/race'
import { invalidateTicketQueries, orderQueryOptions } from '@/entities/ticket'
import { orderApi, orderModel, unwrapData } from '@/shared/api'
import { formatNumber, orderTrackItems, useNow, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  buttonClassName,
  Checkbox,
  EmptyState,
  Icon,
  Radio,
  RadioGroup,
  Skeleton,
  TimerBar,
  type IconName,
} from '@/shared/ui'

import { useDepositBalance } from '../api/use-deposit-balance'
import { splitPayment } from '../lib/deposit'
import { toPayFailure, type PayFailure } from '../lib/pay-error'
import { useHoldCountdown } from '../model/use-hold-countdown'

export type CheckoutState = 'default' | 'offline' | 'timeout' | 'soldout' | 'failed'

type ResultView = 'timeout' | 'soldout' | 'failed'

// 결제가 끝나지 못한 결과 화면 (M07-1 시간 초과 · M07-2 자리 마감 · M07-3 실패)
const RESULT: Record<
  ResultView,
  {
    title: string
    description: ReactNode
    action: string
    icon: IconName
    /** 대회를 모를 때(미리보기) 가는 곳. 시간 초과는 대회를 알면 그 대회 상세(M02)로 간다 */
    href: '/races' | '/checkout'
  }
> = {
  timeout: {
    title: '결제 시간이 지났어요',
    description: (
      <>
        입장 시간이 끝나 잡아 둔 자리가 풀렸어요.
        <br />
        충전한 예치금은 그대로 남아 있어요.
      </>
    ),
    action: '대회 다시 보기',
    icon: 'CircleAlert',
    href: '/races',
  },
  soldout: {
    title: '남은 자리가 없어요',
    description: (
      <>
        결제하는 동안 정원이 다 찼거나 접수가 끝났어요.
        <br />
        결제된 금액은 없어요.
      </>
    ),
    action: '다른 대회 보기',
    icon: 'TriangleAlert',
    href: '/races',
  },
  failed: {
    title: '결제하지 못했어요',
    description: (
      <>
        잠시 뒤 다시 시도해 주세요.
        <br />
        결제 시간 안이면 잡아 둔 자리는 그대로예요.
      </>
    ),
    action: '다시 결제하기',
    icon: 'CircleAlert',
    href: '/checkout',
  },
}

// 결제 확인 중(M07-4)을 「내 참가권에서 결과를 보라」(M07-4-1)로 바꾸는 때.
// 서버의 결제 재확인은 15초마다 돌고, 결제 중 주문은 기한 + 2분이 지나야 만료 정리 대상이 된다.
const PAYING_LATE_MS = 2 * 60 * 1000

// ?preview=offline 시안에서 보여 주는 예치금 잔액과 남은 시간 (5분 10초)
const OFFLINE_PREVIEW_BALANCE = 40_000
const OFFLINE_PREVIEW_SECONDS = 310

const won = (amount: number) => `${formatNumber(amount)}원`

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
      <main className="flex flex-1 flex-col px-4 pt-(--layout-content-start) pb-6">
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
            <Link href={href} replace className={buttonClassName({ size: 'l' })}>
              {result.action}
            </Link>
          )
        }
      />
    </>
  )
}

// 피그마 V3 · M07-4 결제 · 확인 중 / M07-4-1 확인이 늦어짐.
// 결제가 끝나지 않았으므로 다시 결제하는 버튼은 두지 않는다. 2분이 지나면 결과를 볼 곳(내 참가권)만 알려 준다.
function PayingResult() {
  const [late, setLate] = useState(false)
  useEffect(() => {
    const id = setTimeout(() => setLate(true), PAYING_LATE_MS)
    return () => clearTimeout(id)
  }, [])
  return (
    <>
      <AppHeader type="sub" title="결제" close backHref="/" />
      <main
        className="flex flex-1 flex-col px-4 pt-(--layout-content-start) pb-6"
        aria-live="polite"
      >
        <EmptyState
          icon="Clock"
          title={late ? '결제 확인이 늦어지고 있어요' : '결제를 확인하고 있어요'}
          description={
            late ? '결과는 내 참가권에서 볼 수 있어요.' : '이 화면을 나가도 결제는 계속 진행돼요.'
          }
        />
      </main>
      {late && (
        <BottomCta
          className="sticky bottom-0 z-10"
          primary={
            <Link href="/tickets" className={buttonClassName({ size: 'l' })}>
              내 참가권 보기
            </Link>
          }
        />
      )}
    </>
  )
}

/** 연결 끊김 시안 (M07 오프라인). 결제를 누른 뒤라 동의는 체크된 채로 둔다. */
function OfflinePreview() {
  const now = useNow()
  return now ? (
    <OfflinePreviewBody expiresAt={now.getTime() + OFFLINE_PREVIEW_SECONDS * 1000} />
  ) : null
}

function OfflinePreviewBody({ expiresAt }: { expiresAt: number }) {
  const remaining = useHoldCountdown(expiresAt)
  const [agreed, setAgreed] = useState(true)
  const race = RACE_DETAIL
  const option = race.options[0]
  if (remaining === 0) return <CheckoutResult view="timeout" />
  const participant = SAMPLE_PARTICIPANT
  return (
    <CheckoutForm
      offline
      remaining={remaining}
      race={{
        title: race.title,
        meta: [race.startsAt, race.place, option.name].join(' · '),
        image: race.images[0],
      }}
      participant={participantLine(participant)}
      emergency={emergencyLabel(
        participant.emergencyContactName,
        participant.emergencyContactPhone,
      )}
      amount={option.price}
      balance={OFFLINE_PREVIEW_BALANCE}
      refundDeadline={refundDeadlineOf(race)}
      agreed={agreed}
      onAgreedChange={setAgreed}
      action={
        <Button size="l" disabled={!agreed}>
          다시 결제하기
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
  // 결제 확인 중(PAYING)이면 결과가 나올 때까지 주문을 다시 조회한다 (참가권 상세 orderQueryOptions와 같은 캐시)
  const order = useQuery(orderQueryOptions(orderId))
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
  // 결과를 모르는 충전이 있어 결제창을 띄우지 않았음
  const [chargeHeld, setChargeHeld] = useState(false)

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
        if (result !== 'redirecting') setCharging(false)
        setChargeHeld(result === 'held')
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
    // 참가 신청에서 고른 키트 옵션. 옵션이 있는 대회는 이름마다 하나씩 없으면 서버가 400으로 거절한다
    mutationFn: async () => {
      const kitOptionIds = readKitOptionIds(orderId)
      return unwrapData(
        await orderApi.pay(orderId, kitOptionIds.length > 0 ? { kitOptionIds } : undefined),
      )
    },
    // 이 화면이 받아 둔 결제 전 주문 · 잔액이 캐시에 1분 동안 남는다. 지우지 않으면 결제 직후 참가권 상세가
    // 결제 전 주문을 읽어 '참가권을 찾을 수 없어요'가 나오고, 내 참가권의 예치금도 옛 값으로 보인다.
    // 결제한 주문만 다시 받은 뒤에 넘어간다 (받기 전에 상세를 열면 옛 주문을 잠깐 읽는다).
    // 잔액 · 참가 신청 등은 무효로만 표시한다. 다 기다리면 그중 하나가 실패해 재시도가 끝난 몇 초 뒤에야
    // 넘어가는데, 그때 사용자가 다른 화면에 가 있어도 완료 화면으로 다시 보낸다.
    // 다시 받은 주문이 PAID라 아래 effect도 완료 화면으로 보내므로, 같은 replace로 맞춰 기록이 두 번 남지 않게 한다.
    // 202(결제 확인 중)면 주문을 다시 받아 결제 확인 중 화면(M07-4)을 보여 주고, 결과는 주문 재조회가 맞춘다.
    // 거절되면 결제 대기로 돌아와 다시 결제하므로 고른 키트 옵션은 지우지 않는다.
    onSuccess: async (result) => {
      const queryKey = orderApi.getFindById1QueryKey(orderId)
      if (result.status === orderModel.OrderDtoStatus.PAYING) {
        // 진행 중이던 옛 조회 결과(결제 대기)를 다시 쓰지 않도록 받은 주문을 캐시에 바로 넣는다
        await queryClient.cancelQueries({ queryKey })
        queryClient.setQueryData<orderModel.RsDataOrderDto>(queryKey, (old) =>
          old ? { ...old, data: result } : old,
        )
        return
      }
      forgetKitOptionIds(orderId)
      void invalidateTicketQueries(queryClient)
      await queryClient.refetchQueries({ queryKey }, { cancelRefetch: false })
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
  // 결제 확인 중이던 주문이 결제 대기로 돌아오면 서버가 결제를 거절한 것이다 → 결제 실패(M07-3)
  const [prevStatus, setPrevStatus] = useState(status)
  if (status !== prevStatus) {
    setPrevStatus(status)
    if (prevStatus === 'PAYING' && status === 'CREATED') setFailure({ kind: 'failed' })
  }
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
  // 이미 결제한 주문이면 완료 화면으로 보낸다 (뒤로 가기 · 새로고침 · 결제 확인 중 재조회로 완료됨).
  // 결제 확인 중을 거쳐 완료되면 결제 요청의 onSuccess가 지우지 못한 키트 옵션 · 잔액 · 참가권 캐시를 여기서 지운다.
  // 무효화가 다시 그리기를 부르므로 한 번만 한다
  const finished = useRef(false)
  useEffect(() => {
    if (status !== 'PAID' || finished.current) return
    finished.current = true
    forgetKitOptionIds(orderId)
    void invalidateTicketQueries(queryClient)
    router.replace(completeHref)
  }, [status, orderId, queryClient, completeHref, router])

  // 처음 불러오기 실패만 실패 화면이다. 결제 확인 중 재조회가 실패하면 받아 둔 주문으로 계속 기다린다
  if (order.isError && !order.data)
    return <CheckoutResult view="failed" onRetry={() => void order.refetch()} />
  if (!order.data || status === 'PAID') return <CheckoutSkeleton />
  // 결제 중이면 결제 기한이 지나도 결과를 기다린다 (서버는 기한 + 2분까지 결제를 끝낸다)
  if (status === 'PAYING') return <PayingResult />
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
      race={raceSummary(order.data, race.data ?? undefined)}
      participant={participantLine(
        participation.data?.participant,
        order.data.shippingAddress?.recipientName,
      )}
      shipTo={shipToLabel(order.data.shippingAddress)}
      changeHref={
        raceId !== undefined && order.data.items?.[0]?.raceCourseId !== undefined
          ? (`/races/${raceId}/apply?option=${order.data.items[0].raceCourseId}` as const)
          : undefined
      }
      emergency={emergencyLabel(
        participation.data?.participant?.emergencyContactName,
        participation.data?.participant?.emergencyContactPhone,
      )}
      amount={amount}
      balance={balance}
      refundDeadline={race.data ? refundDeadlineOf(race.data) : undefined}
      kitOption={kitOptionLabel(participation.data?.participant?.tshirtSize)}
      agreed={agreed || charged}
      onAgreedChange={setAgreed}
      notice={chargeHeld && <ChargeHeldAlert />}
      action={
        <Button
          size="l"
          disabled={!ready}
          aria-describedby={
            payment && payment.balanceAfter < 0 ? CHARGE_LIMIT_NOTICE_ID : undefined
          }
          onClick={() => pay.mutate({ auto: false })}
        >
          {payment && payment.charge > 0
            ? `${won(payment.charge)} 충전하고 결제하기`
            : `${won(amount)} 결제하기`}
        </Button>
      }
    />
  )
}

/** 1회 충전 한도 안내. 충전 버튼이 이 안내로 설명된다 (화면 낭독기가 버튼과 함께 읽는다) */
const CHARGE_LIMIT_NOTICE_ID = 'checkout-charge-limit-notice'

/** 대회 요약. 대회 상세를 아직 못 받았으면 주문 상품 이름만 쓴다. */
function raceSummary(
  order: orderModel.OrderDto,
  race: RaceDetail | undefined,
): { title: string; meta: string; image?: string } {
  const item = order.items?.[0]
  if (!race) return { title: item?.itemName ?? '', meta: '' }
  const option = race.options.find((o) => o.id === String(item?.raceCourseId))
  const meta = [race.startsAt, race.place, option?.name].filter(Boolean).join(' · ')
  return { title: race.title, meta, image: race.images[0] }
}

// 매퍼는 기한이 없으면 '-'를 넣는다. 날짜 문장에는 쓰지 않는다.
const refundDeadlineOf = (race: RaceDetail) => {
  const value = race.schedule.find((row) => row.label === '전액 환불 기한')?.value
  return value === '-' ? undefined : value
}

const GENDER_LABEL: Record<string, string> = { MALE: '남성', FEMALE: '여성' }

/** 이름 · 생년월일 · 성별. 모르는 값은 빼고 잇는다. */
function participantLine(
  participant?: { name?: string; birth?: string; gender?: string } | null,
  fallbackName?: string,
) {
  const line = [
    participant?.name ?? fallbackName,
    participant?.birth?.replaceAll('-', '.'),
    participant?.gender ? GENDER_LABEL[participant.gender] : undefined,
  ]
    .filter(Boolean)
    .join(' · ')
  return line || undefined
}

const emergencyLabel = (name?: string, phone?: string) =>
  name && phone ? `비상 연락처 ${name} (${phone})` : undefined

const kitOptionLabel = (tshirtSize?: string | null) =>
  tshirtSize ? `티셔츠 ${tshirtSize}` : undefined

const shipToLabel = (address?: orderModel.ShippingAddressDto | null) =>
  address?.address ? [address.address, address.addressDetail].filter(Boolean).join(' ') : undefined

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

/** 결제 화면 구역: 제목 + 내용. 화면 낭독기가 구역 이름을 읽도록 제목에 이어 둔다. */
function Block({
  title,
  aside,
  children,
}: {
  title: string
  /** 제목 오른쪽 (변경 버튼) */
  aside?: ReactNode
  children: ReactNode
}) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h2 id={id} className="text-title3">
          {title}
        </h2>
        {aside}
      </div>
      {children}
    </section>
  )
}

const BOX = 'flex flex-col rounded-md border border-border-default bg-bg-surface'

function Row({ label, value, strong }: { label: string; value: ReactNode; strong?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2 text-body1-regular">
      <span className="shrink-0 text-text-secondary">{label}</span>
      <span className={strong ? 'text-body1-bold' : 'text-body1-medium'}>{value}</span>
    </div>
  )
}

function CheckoutForm({
  offline = false,
  remaining,
  race,
  participant,
  shipTo,
  emergency,
  kitOption,
  amount,
  balance,
  refundDeadline,
  agreed,
  onAgreedChange,
  notice,
  action,
  changeHref,
}: {
  offline?: boolean
  remaining: number
  race: { title: string; meta: string; image?: string }
  /** 이름 · 생년월일 · 성별. 모르면 줄을 그리지 않는다 */
  participant?: string
  shipTo?: string
  emergency?: string
  kitOption?: string
  amount: number
  /** 예치금 잔액. 불러오지 못하면 undefined */
  balance?: number
  /** 전액 환불 기한 (대회 상세 일정). 모르면 환불 줄을 그리지 않는다 */
  refundDeadline?: string
  agreed: boolean
  onAgreedChange: (agreed: boolean) => void
  /** 결제창 위에 보여 줄 안내 */
  notice?: ReactNode
  action: ReactNode
  /** 참가자 정보를 고치러 가는 참가 신청 화면. 모르면 변경 버튼을 그리지 않는다 */
  changeHref?: `/races/${number}/apply?option=${number}`
}) {
  const payment = balance === undefined ? undefined : splitPayment(amount, balance)
  // 1회 충전 한도보다 많이 모자라 한 번 충전으로는 결제할 수 없다
  const overLimit = payment !== undefined && payment.balanceAfter < 0
  return (
    <>
      <AppHeader type="sub" title="결제" />
      <TimerBar
        message="자리를 잡아 뒀어요. 시간 안에 결제해 주세요"
        remainingSeconds={remaining}
      />
      <main className="flex flex-1 flex-col gap-8 px-4 pt-4 pb-6">
        {offline && (
          <Alert
            tone="info"
            title="연결이 끊겼어요"
            description="결제는 한 번만 처리돼요. 다시 연결되면 결제가 됐는지 확인해 드려요."
          />
        )}
        {notice}

        {(participant || shipTo || emergency) && (
          <Block
            title="참가자 · 배송"
            aside={
              changeHref && (
                <Link
                  href={changeHref}
                  className={buttonClassName({ variant: 'ghost', size: 's' })}
                >
                  변경
                </Link>
              )
            }
          >
            {/* 참가자 이름 · 받는 곳 주소가 들어 있어 Clarity 화면 녹화에서 가린다 */}
            <div className={`${BOX} gap-1 p-4`} data-clarity-mask="true">
              {participant && <p className="text-body1-medium">{participant}</p>}
              {shipTo && <p className="text-body1-regular">{shipTo}</p>}
              {emergency && <p className="text-body1-regular text-text-secondary">{emergency}</p>}
            </div>
          </Block>
        )}

        <Block title="신청 대회">
          <div className={`${BOX} gap-3 p-4`}>
            <div className="flex items-center gap-3">
              {race.image ? (
                <Image
                  src={race.image}
                  alt=""
                  width={72}
                  height={54}
                  className="h-13.5 w-18 shrink-0 rounded-sm object-cover"
                />
              ) : (
                <RacePoster
                  seed={race.title}
                  title=""
                  label=""
                  className="h-13.5 w-18 shrink-0 rounded-sm"
                />
              )}
              <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                <p className="text-body1-bold">{race.title}</p>
                <p className="text-caption-regular text-text-secondary">{race.meta}</p>
              </div>
            </div>
            {kitOption && (
              <p className="border-t border-border-default pt-3 text-body1-regular">
                키트 {kitOption}
              </p>
            )}
            <div className="border-t border-border-default pt-2">
              <div className="flex items-center justify-between gap-4 py-2">
                <span className="text-body1-regular">참가비</span>
                <span className="text-body1-bold">{won(amount)}</span>
              </div>
            </div>
          </div>
        </Block>

        <Block title="예치금">
          <div className={`${BOX} px-4 py-2`}>
            <Row label="보유 예치금" value={payment ? won(balance ?? 0) : '-'} />
            <Row
              label="이번에 쓸 예치금"
              strong
              value={
                payment
                  ? `${won(payment.fromDeposit)}${payment.fromDeposit > 0 && payment.fromDeposit === balance ? ' (전액)' : ''}`
                  : '-'
              }
            />
          </div>
        </Block>

        {payment && payment.charge > 0 && (
          <Block title="충전 수단">
            <div className={`${BOX} p-4`}>
              <RadioGroup defaultValue="toss">
                <Radio value="toss" label="카드 · 간편결제 (토스페이먼츠)" />
              </RadioGroup>
            </div>
          </Block>
        )}

        <Block title="결제 금액">
          <div className={`${BOX} px-4 py-3`}>
            <Row label="참가비" value={won(amount)} />
            <Row label="예치금 사용" value={payment ? `-${won(payment.fromDeposit)}` : '-'} />
            <Row label="남은 금액" value={payment ? won(amount - payment.fromDeposit) : '-'} />
            {payment && payment.charge > 0 && (
              <>
                <div className="mt-1 flex items-center justify-between gap-4 border-t border-border-default pt-3 pb-1">
                  <span className="text-body1-bold">카드로 충전할 금액</span>
                  <span className="text-title2">{won(payment.charge)}</span>
                </div>
                {overLimit ? (
                  <p id={CHARGE_LIMIT_NOTICE_ID} className="text-right text-caption-regular">
                    한 번에 {MAX_CHARGE / 10_000}만 원까지 충전할 수 있어요. 충전한 뒤 남은 금액은
                    다시 충전해 주세요.
                  </p>
                ) : (
                  <p className="text-right text-caption-regular text-text-secondary">
                    {payment.balanceAfter > 0
                      ? `최소 1만 원부터 충전해요. 남는 ${won(payment.balanceAfter)}은 예치금으로 남아요.`
                      : '최소 1만 원부터 충전해요.'}
                  </p>
                )}
              </>
            )}
          </div>
        </Block>

        <Block title="결제 동의">
          <Checkbox
            checked={agreed}
            onCheckedChange={onAgreedChange}
            label={<span className="font-bold">주문 내용 확인 및 결제 동의</span>}
          />
          <ul className="flex flex-col gap-2 pl-8 text-body1-regular text-text-secondary">
            {refundDeadline && (
              <li className="flex gap-2">
                <Icon name="Check" className="mt-0.5 size-4 shrink-0" aria-hidden />
                (필수) {refundDeadline} 뒤에는 취소해도 환불되지 않아요
              </li>
            )}
            <li className="flex gap-2">
              <Icon name="Check" className="mt-0.5 size-4 shrink-0" aria-hidden />
              (필수) 결제대행 서비스 이용약관 동의
            </li>
          </ul>
          <p className="border-t border-border-default pt-2 text-body2-regular text-text-secondary">
            크루런은 통신판매중개자이며 거래 당사자가 아니에요. 대회 운영과 환불 책임은 판매자에게
            있어요.
          </p>
        </Block>
      </main>
      <BottomCta className="sticky bottom-0 z-10" primary={action} />
    </>
  )
}
