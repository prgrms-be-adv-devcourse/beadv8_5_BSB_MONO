'use client'

import { Dialog } from '@base-ui/react/dialog'
import { zodResolver } from '@hookform/resolvers/zod'
import { useState, type ReactNode } from 'react'
import { Controller, useForm } from 'react-hook-form'

import {
  kitErrorMessage,
  useChangeKitTracking,
  useCompleteKitDeliveries,
  useShipKits,
  type KitStatus,
} from '@/entities/seller-kit'
import type { orderModel } from '@/shared/api'
import { cn, copyText } from '@/shared/lib'
import { Alert, Button, FOCUS_RING, Icon, Select, TextField, useToast } from '@/shared/ui'

import {
  detailFormSchema,
  formatMonthDay,
  formatTrackingNumber,
  type DetailFormValues,
} from '../lib/kit-detail'
import { CARRIERS, TAB_LABEL } from '../model/ship-form'

type Kit = orderModel.SellerKitDto

export type KitOrderDetailProps = {
  /** 연 주문. 없으면 창이 닫힌다 */
  kit?: Kit
  raceTitle: string
  kitOption: string
  onClose: () => void
  /** 배송 중 · 배송 완료로 바꾼 뒤 (목록에서 이 주문이 빠지므로 창을 닫는다) */
  onDone: () => void
}

// 시안 V4 · 판매자 · 키트 배송 · 주문 상세(2070:67422 외): 오른쪽에 붙는 폭 480 창
export function KitOrderDetail({
  kit,
  raceTitle,
  kitOption,
  onClose,
  onDone,
}: KitOrderDetailProps) {
  return (
    <Dialog.Root open={Boolean(kit)} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        {/* 토스트(z-50)가 창 위에 보이도록 한 단계 아래(z-40)에 둔다 */}
        <Dialog.Backdrop className="fixed inset-0 z-40 bg-bg-overlay/40" />
        <Dialog.Popup className="fixed inset-y-0 right-0 z-40 flex w-120 max-w-full flex-col bg-bg-surface shadow-lg outline-none">
          {kit && (
            <DetailBody
              key={kit.orderItemId}
              kit={kit}
              raceTitle={raceTitle}
              kitOption={kitOption}
              onDone={onDone}
            />
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function DetailBody({
  kit,
  raceTitle,
  kitOption,
  onDone,
}: Required<Pick<KitOrderDetailProps, 'kit' | 'raceTitle' | 'kitOption' | 'onDone'>>) {
  const id = kit.orderItemId ?? 0
  const status: KitStatus = kit.kitDeliveryStatus ?? 'PREPARING'
  const canceled = kit.orderItemStatus === 'CANCELED'
  const [editing, setEditing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const showToast = useToast()
  const ship = useShipKits()
  const deliver = useCompleteKitDeliveries()
  const change = useChangeKitTracking()
  // 송장 입력 대기 · 송장 고치기에서만 입력 칸을 연다.
  // 취소는 머리 표시에만 쓴다. 서버는 보낸 뒤 취소된 주문도 송장 고치기 · 배송 완료를 받는다
  const formMode = status === 'PREPARING' || editing
  const form = useForm<DetailFormValues>({
    resolver: zodResolver(detailFormSchema),
    defaultValues: { carrier: '', trackingNumber: '' },
  })
  const address = kit.shippingAddress
  // 서버에서 받은 택배사가 고르기 목록에 없어도 고친 화면에서 그대로 보이게 한다
  const carriers =
    kit.kitCarrier && !CARRIERS.some((c) => c.value === kit.kitCarrier)
      ? [...CARRIERS, { value: kit.kitCarrier, label: kit.kitCarrier }]
      : CARRIERS

  const submit = form.handleSubmit(async ({ carrier, trackingNumber }) => {
    // 버튼은 loading으로 막히지만 입력칸에서 Enter로 보내는 길은 여기서 막는다
    if (ship.isPending || change.isPending) return
    setError(null)
    if (editing) {
      try {
        await change.mutateAsync({ orderItemId: id, carrier, trackingNumber })
        setEditing(false)
      } catch (e) {
        setError(kitErrorMessage(e))
      }
      return
    }
    const outcome = await ship.mutateAsync([{ orderItemId: id, carrier, trackingNumber }])
    if (outcome.failures.length > 0) setError(kitErrorMessage(outcome.failures[0].error))
    else onDone()
  })

  async function completeDelivery() {
    setError(null)
    const outcome = await deliver.mutateAsync([{ orderItemId: id }])
    if (outcome.failures.length > 0) setError(kitErrorMessage(outcome.failures[0].error))
    else onDone()
  }

  async function copyTracking() {
    const copied = await copyText(kit.kitTrackingNumber ?? '')
    showToast(
      copied
        ? { message: '운송장 번호를 복사했어요' }
        : { message: '운송장 번호를 복사하지 못했어요', tone: 'error' },
    )
  }

  function startEditing() {
    setError(null)
    setEditing(true)
    form.reset({ carrier: kit.kitCarrier ?? '', trackingNumber: kit.kitTrackingNumber ?? '' })
  }

  function cancelEditing() {
    setError(null)
    setEditing(false)
  }

  const footer = editing ? (
    <>
      <Button variant="secondary" size="l" className="flex-1" onClick={cancelEditing}>
        취소
      </Button>
      <Button size="l" className="flex-1" loading={change.isPending} onClick={submit}>
        저장하기
      </Button>
    </>
  ) : status === 'PREPARING' ? (
    <Button size="l" className="flex-1" loading={ship.isPending} onClick={submit}>
      배송 중으로 바꾸기
    </Button>
  ) : status === 'SHIPPING' ? (
    <Button size="l" className="flex-1" loading={deliver.isPending} onClick={completeDelivery}>
      배송 완료로 바꾸기
    </Button>
  ) : null

  return (
    <>
      <div className="flex items-start gap-3 px-6 pt-6 pb-4">
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          {canceled ? (
            <p className="text-body1-medium text-text-danger">취소</p>
          ) : (
            <p className="text-caption-medium text-text-secondary">{TAB_LABEL[status]}</p>
          )}
          <Dialog.Title className="text-title3 text-text-primary">{kit.orderNo}</Dialog.Title>
        </div>
        <Dialog.Close aria-label="닫기" className={cn('rounded-sm text-text-primary', FOCUS_RING)}>
          <Icon name="X" className="size-6" />
        </Dialog.Close>
      </div>

      <div className="flex min-h-0 flex-1 flex-col gap-8 overflow-y-auto px-6 pt-2 pb-6">
        {error && <Alert tone="danger" title="바꾸지 못했어요" description={error} />}
        <InfoGroup title="주문">
          <InfoRow label="대회">{raceTitle}</InfoRow>
          <InfoRow label="종목">{kit.courseName}</InfoRow>
          <InfoRow label="키트 옵션">{kitOption}</InfoRow>
        </InfoGroup>
        {/* 받는 사람 정보는 Clarity 화면 녹화에서 가린다 */}
        <InfoGroup title="받는 사람" mask>
          <InfoRow label="이름">{address?.recipientName}</InfoRow>
          <InfoRow label="연락처">{address?.recipientPhone}</InfoRow>
          <InfoRow label="주소">
            {[address?.address, address?.addressDetail].filter(Boolean).join(', ')}
          </InfoRow>
        </InfoGroup>

        {formMode ? (
          <section className={cn('flex flex-col', editing ? 'gap-1' : 'gap-4')}>
            <h3 className="text-body1-bold text-text-primary">배송</h3>
            <form
              className="flex flex-col gap-4"
              noValidate
              onSubmit={(event) => {
                event.preventDefault()
                void submit()
              }}
            >
              <Controller
                control={form.control}
                name="carrier"
                render={({ field, fieldState }) => (
                  <Select
                    label="택배사"
                    placeholder="고르기"
                    items={carriers}
                    value={field.value || null}
                    onValueChange={(value) => field.onChange(value ?? '')}
                    error={fieldState.error?.message}
                  />
                )}
              />
              <TextField
                label="송장 번호"
                placeholder="숫자만 적어 주세요"
                inputMode="numeric"
                error={form.formState.errors.trackingNumber?.message}
                {...form.register('trackingNumber')}
              />
            </form>
          </section>
        ) : (
          kit.kitShippedAt && (
            <div className="flex flex-col gap-1">
              <InfoGroup title="배송">
                <InfoRow label="택배사">{kit.kitCarrier}</InfoRow>
                <InfoRow label="송장 번호">
                  <button
                    type="button"
                    aria-label={`송장 번호 ${formatTrackingNumber(kit.kitTrackingNumber)} 복사`}
                    onClick={copyTracking}
                    className={cn('cursor-pointer rounded-sm hover:underline', FOCUS_RING)}
                  >
                    {formatTrackingNumber(kit.kitTrackingNumber)}
                  </button>
                </InfoRow>
                <InfoRow label="발송일">{formatMonthDay(kit.kitShippedAt)}</InfoRow>
                {status === 'DELIVERED' && (
                  <InfoRow label="받은 날">{formatMonthDay(kit.kitDeliveredAt)}</InfoRow>
                )}
              </InfoGroup>
              {status === 'SHIPPING' && (
                <Button variant="secondary" size="s" className="self-start" onClick={startEditing}>
                  송장 수정하기
                </Button>
              )}
            </div>
          )
        )}
      </div>

      {footer && (
        <div className="flex gap-2 border-t border-border-default px-6 pt-4 pb-6">{footer}</div>
      )}
    </>
  )
}

function InfoGroup({
  title,
  mask,
  children,
}: {
  title: string
  mask?: boolean
  children: ReactNode
}) {
  return (
    <section className="flex flex-col gap-1" data-clarity-mask={mask ? 'true' : undefined}>
      <h3 className="text-body1-bold text-text-primary">{title}</h3>
      <dl className="flex flex-col">{children}</dl>
    </section>
  )
}

function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline gap-4 py-3 text-body1-regular">
      <dt className="w-30 shrink-0 text-text-secondary">{label}</dt>
      <dd className="min-w-0 flex-1 text-text-primary">{children}</dd>
    </div>
  )
}
