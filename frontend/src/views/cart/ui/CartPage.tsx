'use client'

import { useMutation, useQueryClient } from '@tanstack/react-query'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useBookingFlow } from '@/features/booking'
import { isQueueNotAdmitted, QUEUE_NOT_ADMITTED_CODE } from '@/features/queue-admission'
import { ApiError, orderApi, unwrapData, useIdempotencyKey, type orderModel } from '@/shared/api'
import { formatWon } from '@/shared/lib'
import {
  Alert,
  Badge,
  BottomCta,
  Button,
  buttonClassName,
  EmptyState,
  KeyValue,
  Skeleton,
} from '@/shared/ui'

import { useCartLines, type CartLine } from '../model/use-cart-lines'
import { QueueRequiredDialog } from './QueueRequiredDialog'

type CheckoutResult = orderModel.CartCheckoutItemDto

/** 자리를 잡아 주문이 만들어진 결과 (새로 만듦 · 이미 만든 주문) */
const isOrdered = (r: CheckoutResult) => r.outcome === 'CREATED' || r.outcome === 'EXISTING'

// 피그마 V2 · M06 장바구니 (+ 비어 있음). 장바구니에는 한 종목(참가권 1장)만 담긴다.
// ?preview=empty 시안은 API를 부르지 않고 빈 화면을 보여 준다.
// ?preview=queue 시안은 대기열 입장 전 결제 창(M06-Q)을 연 채로 보여 준다.
export function CartPage({
  empty = false,
  queueDialogOpen = false,
}: {
  empty?: boolean
  /** 대기열 입장 전 결제 창(M06-Q)을 처음부터 열어 둔다 */
  queueDialogOpen?: boolean
}) {
  return (
    <>
      <AppHeader type="sub" title="장바구니" />
      {empty ? <EmptyCart /> : <CartContent initialQueueDialogOpen={queueDialogOpen} />}
    </>
  )
}

function EmptyCart() {
  return (
    <main className="flex flex-1 items-center justify-center px-4 py-6">
      <EmptyState
        title="장바구니가 비어 있어요"
        description="마음에 드는 대회를 찾아 참가권을 담아 보세요."
        action={
          <Link href="/races" className={buttonClassName({ variant: 'secondary' })}>
            대회 찾기
          </Link>
        }
      />
    </main>
  )
}

function CartContent({ initialQueueDialogOpen }: { initialQueueDialogOpen: boolean }) {
  const router = useRouter()
  // 대기열 입장 전 결제 창(M06-Q). 결제가 대기열 입장 전(ORDER_QUEUE_NOT_ADMITTED)이라 막히면 연다.
  const [queueDialogOpen, setQueueDialogOpen] = useState(initialQueueDialogOpen)
  const queryClient = useQueryClient()
  const { lines, isError } = useCartLines()
  // 장바구니 결제에서 자리를 잡지 못한 결과 (매진 · 실패). 담은 참가권 줄에 보여 준다.
  const [failure, setFailure] = useState<CheckoutResult | null>(null)
  // 같은 참가 신청으로 다시 누르면(두 번 클릭 · 실패 뒤 재시도) 같은 Idempotency-Key를 보낸다.
  const checkoutKey = useIdempotencyKey()
  const forgetApplications = useBookingFlow((s) => s.forgetApplications)

  const remove = useMutation({
    mutationFn: async (line: CartLine) => unwrapData(await orderApi.removeItem(line.cartItemId)),
    onSuccess: (cart, line) => {
      // 빼면 서버가 그 종목의 결제 전 신청서를 취소한다. 다시 신청할 때 취소된 신청서를 고치지 않게 잊는다
      forgetApplications([String(line.raceCourseId)])
      queryClient.setQueryData(orderApi.getFindCartQueryKey(), {
        status: 200,
        message: '',
        data: cart,
      })
      setFailure(null)
    },
  })

  const checkout = useMutation({
    // 장바구니에는 한 종목만 담기므로 참가 신청 1건만 보낸다 (2건 이상이면 400).
    mutationFn: async ({ participationId, key }: { participationId: number; key: string }) =>
      unwrapData(
        await orderApi.checkout({ participationIds: [participationId] }, { idempotencyKey: key }),
      ),
    onSuccess: (data) => {
      // 결과도 1건이다. 자리를 잡았으면 결제 화면으로, 매진 · 실패면 그 줄에 이유를 보여 준다.
      const result = data.results?.[0]
      if (result && isOrdered(result) && result.order?.id !== undefined) {
        router.push(`/checkout?orderId=${result.order.id}`)
        return
      }
      // 대기열 입장 전이면 항목 실패(FAILED + code)로 온다. 줄의 실패 문구 대신 대기열 창을 띄운다.
      if (result?.code === QUEUE_NOT_ADMITTED_CODE) {
        setFailure(null)
        setQueueDialogOpen(true)
        return
      }
      setFailure(result ?? null)
    },
    onError: (error) => {
      if (isQueueNotAdmitted(error)) setQueueDialogOpen(true)
    },
  })

  if (isError) {
    return (
      <main className="flex flex-1 flex-col px-4 py-6">
        <Alert
          tone="danger"
          title="장바구니를 불러오지 못했어요"
          description="잠시 후 다시 시도해 주세요."
        />
      </main>
    )
  }
  if (!lines) {
    return (
      <main aria-busy className="flex flex-1 flex-col gap-6 px-4 py-6">
        <Skeleton shape="rect" className="h-16" />
        <Skeleton shape="rect" className="h-28" />
      </main>
    )
  }
  if (lines.length === 0) return <EmptyCart />

  // 결제할 참가권. 한 종목만 담기므로 결제할 수 있는 첫 줄 하나다.
  const payable = lines.find((l) => l.application && l.option)
  const total = payable?.option?.price ?? 0

  const startCheckout = () => {
    const participationId = payable?.application?.participationId
    if (participationId === undefined) return
    checkout.mutate({ participationId, key: checkoutKey(participationId) })
  }

  // 장바구니에는 대회 1개만 담기므로 대회 · 종목을 아는 첫 줄의 대기열로 간다
  const queued = lines.find((l) => l.race && l.option)
  const queueHref =
    queued?.race && queued.option
      ? (`/races/${queued.race.id}/queue?option=${encodeURIComponent(queued.option.id)}` as const)
      : ('/races' as const)

  // 대기열 입장 전(403)은 서버 문구 대신 대기열 창(M06-Q)으로 알린다.
  const checkoutFailed = checkout.isError && !isQueueNotAdmitted(checkout.error)
  const checkoutError =
    checkoutFailed && checkout.error instanceof ApiError ? checkout.error.serverMessage : undefined

  return (
    <>
      <main className="flex flex-1 flex-col gap-6 px-4 py-6">
        <Alert
          tone="warning"
          title="담기만 해서는 자리가 잡히지 않아요"
          description="결제할 때 가격 · 정원 · 기한을 다시 확인해요."
        />
        <ul className="flex flex-col gap-3">
          {lines.map((line) => (
            <CartLineCard
              key={line.cartItemId}
              line={line}
              result={
                failure && failure.participationId === line.application?.participationId
                  ? failure
                  : undefined
              }
              removing={remove.isPending && remove.variables?.cartItemId === line.cartItemId}
              onRemove={() => remove.mutate(line)}
            />
          ))}
        </ul>
        <div className="flex flex-col gap-3 rounded-lg bg-bg-subtle p-5">
          <KeyValue label={`참가권 ${payable ? 1 : 0}개`} value={formatWon(total)} />
          <div className="flex items-center justify-between gap-2">
            <span className="text-body2-bold text-text-secondary">합계</span>
            <span className="text-body1-bold">{formatWon(total)}</span>
          </div>
        </div>
        {checkoutFailed && (
          <Alert
            tone="danger"
            title={checkoutError ?? '결제를 시작하지 못했어요'}
            description={checkoutError ? undefined : '잠시 후 다시 시도해 주세요.'}
          />
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        price={{ label: '총 결제 금액', amount: formatWon(total) }}
        primary={
          <Button size="l" disabled={!payable || checkout.isPending} onClick={startCheckout}>
            결제하기
          </Button>
        }
      />
      <QueueRequiredDialog
        open={queueDialogOpen}
        onOpenChange={setQueueDialogOpen}
        onQueue={() => router.push(queueHref)}
      />
    </>
  )
}

function CartLineCard({
  line,
  result,
  removing,
  onRemove,
}: {
  line: CartLine
  result?: CheckoutResult
  removing: boolean
  onRemove: () => void
}) {
  const { race, option, application } = line
  const failed = result && !isOrdered(result)
  return (
    <li className="flex gap-4 rounded-lg bg-bg-surface px-5 py-4 shadow-sm inset-ring inset-ring-border-default">
      {race?.images[0] ? (
        <Image
          src={race.images[0]}
          alt=""
          width={64}
          height={64}
          className="size-16 shrink-0 rounded-md object-cover"
        />
      ) : (
        <div aria-hidden className="size-16 shrink-0 rounded-md bg-bg-subtle" />
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div className="flex items-center justify-between gap-2">
          <p className="min-w-0 flex-1 text-body2-bold">
            {race && option ? `${race.title} · ${option.name}` : '참가권'}
          </p>
          {failed ? (
            <Badge tone="danger">{result.outcome === 'SOLD_OUT' ? '매진' : '실패'}</Badge>
          ) : (
            <Badge tone="brand">참가권</Badge>
          )}
        </div>
        {application ? (
          // 참가자 이름이 들어 있어 Clarity 화면 녹화에서 가린다
          <p className="text-body2-regular text-text-secondary" data-clarity-mask="true">
            {application.participantName ? `참가자 ${application.participantName} · ` : ''}수량 1
            (고정)
            {race ? ` · 접수 마감 ${race.dDay}` : ''}
          </p>
        ) : (
          // 장바구니에 붙은 참가 신청이 없으면(신청서 없이 담음) 결제에 보낼 참가 신청 ID를 알 수 없다
          <p className="text-body2-regular text-text-secondary">
            신청서를 찾을 수 없어 결제할 수 없어요. 빼고 다시 담아 주세요.
          </p>
        )}
        {failed && <p className="text-body2-regular text-text-danger">{result.message}</p>}
        <div className="flex items-center justify-between gap-2">
          <Button variant="text" size="s" disabled={removing} onClick={onRemove}>
            삭제
          </Button>
          {option && <p className="text-body1-bold">{formatWon(option.price)}</p>}
        </div>
      </div>
    </li>
  )
}
