'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { useBookingFlow, type BookingParticipant } from '@/features/booking'
import {
  admissionExpiredHref,
  isQueueNotAdmitted,
  useAdmissionTimer,
} from '@/features/queue-admission'
import {
  raceDetailQueryOptions,
  RaceTicketCard,
  type RaceDetail,
  type RaceOption,
} from '@/entities/race'
import { ApiError, memberApi, orderApi, unwrapData, useIdempotencyKey } from '@/shared/api'
import { cn, formatWon, trackEvent, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  Checkbox,
  Dialog,
  Divider,
  FOCUS_RING,
  KeyValue,
  PhoneField,
  Radio,
  RadioGroup,
  Section,
  Select,
  Skeleton,
  Stepper,
  TextField,
  TimerBar,
} from '@/shared/ui'

import {
  EMPTY_PARTICIPANT_FORM,
  PARTICIPANT_ERRORS,
  participantFormSchema,
  toParticipantFormValues,
  toParticipantRequest,
  type ParticipantFormValues,
} from '../model/participant-form'

const LINK = cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)

/** 신청서를 저장한 뒤 어디로 갈지 */
type NextStep = 'cart' | 'checkout'

/** 장바구니에 다른 종목이 담겨 있을 때 바꿀지 묻는 확인 창(M05-1)의 내용 */
type ReplaceCart = {
  /** 빼야 할 장바구니 항목 */
  cartItemIds: number[]
  /** 빼야 할 항목의 종목 ID. 빼면 서버가 그 결제 전 신청서를 취소하므로 메모리에서도 잊는다 */
  optionIds: string[]
  /** 담아 둔 대회 · 종목 이름. 대회를 알 수 없으면 undefined */
  label?: string
}

// 피그마 V2 · M05 참가 신청 · 참가자 정보 (+ M05-1 장바구니 바꾸기 확인)
// 종목은 page.tsx에서 정원이 남은 종목 중에 골라 넘긴다 (상품 API의 대회 상세 courses).
// 입력 칸은 이번에 입력해 둔 값(결제에서 돌아온 경우) → 지난 신청 값 → 회원 정보 순으로 채운다.
// 대기열에서 내 차례(M05-Q2)로 들어오면 남은 시간 띠가 결제(M07)까지 따라간다.
// 남은 시간은 대기열 화면에서 받은 입장 끝 시각을 이어 쓰고, 0이 되면 입장 시간 만료 화면(M07-1)으로 간다.
export function RaceApplyPage({
  race,
  option,
  previewRemainingSeconds,
}: {
  race: RaceDetail
  option: RaceOption
  /** 시안 미리보기(?preview=admitted). 있으면 API를 부르지 않고 이 남은 초로 띠를 붙인다 */
  previewRemainingSeconds?: number
}) {
  const defaults = useParticipantDefaults(option)
  const preview = previewRemainingSeconds !== undefined
  const timer = useAdmissionTimer(
    preview ? undefined : Number(race.id),
    preview ? undefined : Number(option.id),
    'follow',
  )
  const remaining = preview ? previewRemainingSeconds : timer.remaining
  useTrackOnce('apply_start', preview ? null : { raceId: race.id, courseId: option.id })
  return (
    <>
      <AppHeader type="sub" title="참가 신청" />
      {remaining !== null && (
        <TimerBar
          message="내 차례예요. 시간 안에 신청과 결제를 마쳐 주세요"
          remainingSeconds={remaining}
        />
      )}
      {defaults ? (
        <ParticipantForm race={race} option={option} defaults={defaults} />
      ) : (
        <main aria-busy className="flex flex-1 flex-col gap-4 bg-bg-surface px-4 py-5">
          <Skeleton shape="rect" className="h-24" />
          <Skeleton shape="rect" className="h-80" />
        </main>
      )}
    </>
  )
}

/**
 * 입력 칸의 처음 값. 불러오는 동안은 null.
 * 지난 신청이 없으면 회원 정보에서 휴대폰 번호를 채운다 (회원 정보에는 이름·생년월일·성별이 없다).
 * 불러오지 못하면 빈 칸으로 시작한다.
 */
function useParticipantDefaults(option: RaceOption): ParticipantFormValues | null {
  const saved = useBookingFlow((s) => s.participant)
  const latest = orderApi.useFindLatestParticipant({
    query: { enabled: !saved, select: (response) => response.data ?? null },
  })
  const needMember = !saved && latest.isSuccess && latest.data === null
  const me = memberApi.useFindMe({ query: { enabled: needMember, select: unwrapData } })

  if (saved) return toParticipantFormValues(saved, option)
  if (latest.isError) return EMPTY_PARTICIPANT_FORM
  if (!latest.isSuccess) return null
  if (latest.data) return toParticipantFormValues(latest.data, option)
  if (me.isError) return EMPTY_PARTICIPANT_FORM
  if (!me.isSuccess) return null
  return { ...EMPTY_PARTICIPANT_FORM, phone: me.data.phone ?? '' }
}

function ParticipantForm({
  race,
  option,
  defaults,
}: {
  race: RaceDetail
  option: RaceOption
  defaults: ParticipantFormValues
}) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const refundDeadline = race.schedule.find((row) => row.label === '전액 환불 기한')?.value
  const sizes = (option.shirtSizes ?? []).map((v) => ({ value: v, label: v }))
  const applications = useBookingFlow((s) => s.applications)
  const submitApplication = useBookingFlow((s) => s.submitApplication)
  const forgetApplications = useBookingFlow((s) => s.forgetApplications)
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<ParticipantFormValues>({
    resolver: zodResolver(participantFormSchema(option)),
    defaultValues: defaults,
  })
  // 필수 동의는 사용자가 직접 체크해야 버튼이 켜진다.
  const [agreed, setAgreed] = useState(false)
  const [replace, setReplace] = useState<ReplaceCart | null>(null)
  // 같은 참가 신청으로 바로 결제를 다시 누르면(두 번 클릭 · 실패 뒤 재시도) 같은 Idempotency-Key를 보낸다.
  const orderKey = useIdempotencyKey()

  // 참가 신청을 저장하고(이번에 이미 저장한 종목이면 참가자 정보만 고친다) 다음 단계로 넘어간다.
  const save = useMutation({
    mutationFn: async ({ values, next }: { values: ParticipantFormValues; next: NextStep }) => {
      const participant = toParticipantRequest(values, option)
      const participation = await saveParticipation(participant)
      submitApplication(
        {
          raceId: race.id,
          optionId: option.id,
          participationId: participation.id ?? 0,
          participantName: participant.name,
        },
        participant,
      )
      if (next === 'checkout') {
        return { orderId: await createOrder(participation.id ?? 0), replace: null }
      }
      // 장바구니에는 한 종목만 담을 수 있다. 다른 종목(같은 대회의 다른 종목 포함)이 담겨 있으면
      // 담기 전에 바꿀지 묻는다. 같은 종목이면 묻지 않는다(참가자 정보는 위에서 이미 고쳤다).
      const pending = await replaceIfOtherCourse(false)
      if (pending) return { orderId: null, replace: pending }
      try {
        await addToCart()
      } catch (error) {
        // 미리 확인한 뒤 다른 종목이 담긴 경우(다른 탭 · 동시 요청)도 같은 확인 창으로 묻는다.
        if (error instanceof ApiError && error.code === 'ORDER_CART_ONE_COURSE_ONLY') {
          const conflict = await replaceIfOtherCourse(true)
          if (conflict) return { orderId: null, replace: conflict }
        }
        throw error
      }
      return { orderId: null, replace: null }
    },
    onSuccess: ({ orderId, replace: pending }) => {
      if (orderId !== null) router.push(`/checkout?orderId=${orderId}`)
      else if (pending) setReplace(pending)
      else router.push('/cart')
    },
    // 바로 결제가 대기열 입장 전(입장 시간이 끝남)이라 막히면 입장 시간 만료 화면(M07-1)으로 간다.
    onError: (error) => {
      if (isQueueNotAdmitted(error)) router.replace(admissionExpiredHref(Number(race.id)))
    },
  })

  /** 바로 결제: 참가 신청으로 주문을 만들어 자리를 잡는다. */
  async function createOrder(participationId: number) {
    const order = unwrapData(
      await orderApi.create({ participationId }, { idempotencyKey: orderKey(participationId) }),
    )
    return order.id ?? 0
  }

  // 확인 창에서 바꾸기를 누르면 담아 둔 다른 종목을 빼고 이 종목을 담는다.
  const replaceCart = useMutation({
    mutationFn: async ({ cartItemIds, optionIds }: ReplaceCart) => {
      for (const id of cartItemIds) await orderApi.removeItem(id)
      forgetApplications(optionIds)
      await addToCart()
    },
    onSuccess: () => router.push('/cart'),
    onSettled: () => setReplace(null),
  })

  async function addToCart() {
    await orderApi.addItem({ raceCourseId: Number(option.id) })
    trackEvent('add_to_cart', {
      item: { raceId: race.id, courseId: option.id, price: option.price, name: race.title },
    })
    await queryClient.invalidateQueries({ queryKey: orderApi.getFindCartQueryKey() })
  }

  /**
   * 장바구니에 이 종목과 다른 항목이 있으면 확인 창 내용을, 없으면 null을 돌려준다.
   * fresh면 캐시를 쓰지 않고 장바구니를 새로 불러온다(서버가 409를 준 뒤).
   */
  async function replaceIfOtherCourse(fresh: boolean): Promise<ReplaceCart | null> {
    const options = orderApi.getFindCartQueryOptions()
    const cart = unwrapData(
      await queryClient.fetchQuery(fresh ? { ...options, staleTime: 0 } : options),
    )
    const others = (cart.items ?? []).filter((item) => String(item.raceCourseId) !== option.id)
    if (others.length === 0) return null
    const label = await cartLabel(others[0])
    return {
      cartItemIds: others.map((i) => i.id ?? 0),
      optionIds: others.map((i) => String(i.raceCourseId)),
      label,
    }
  }

  /**
   * 담아 둔 항목의 대회 · 종목 이름. 같은 대회의 종목이면 이 대회에서 찾고,
   * 다른 대회는 장바구니 항목에 붙은 참가 신청(없으면 이 기기에서 저장한 참가 신청)의 대회 ID로 찾는다.
   */
  async function cartLabel(item: { raceCourseId?: number; raceId?: number | null }) {
    const courseId = String(item.raceCourseId)
    const sameRace = race.options.find((o) => o.id === courseId)
    if (sameRace) return `${race.title} · ${sameRace.name}`
    const raceId = item.raceId != null ? String(item.raceId) : applications[courseId]?.raceId
    if (!raceId) return undefined
    try {
      const other = await queryClient.fetchQuery(raceDetailQueryOptions(raceId))
      const course = other?.options.find((o) => o.id === courseId)
      return other && course ? `${other.title} · ${course.name}` : undefined
    } catch {
      return undefined
    }
  }

  async function saveParticipation(participant: BookingParticipant) {
    const existing = applications[option.id]
    if (existing) {
      return unwrapData(await orderApi.changeParticipant(existing.participationId, participant))
    }
    return unwrapData(
      await orderApi.apply({
        raceCourseId: Number(option.id),
        participant,
        thirdPartyConsent: true,
      }),
    )
  }

  const submit = (next: NextStep) => handleSubmit((values) => save.mutate({ values, next }))
  // 409(이미 신청함 · 접수 기간 아님) · 400(나이 제한 밖 · 키트 배송 주소 없음)은 서버 문구를 그대로 보여 준다.
  const failure = (isQueueNotAdmitted(save.error) ? null : save.error) ?? replaceCart.error
  const saveError = failure
    ? ((failure instanceof ApiError ? failure.serverMessage : undefined) ??
      PARTICIPANT_ERRORS.unavailable)
    : null
  const busy = save.isPending || replaceCart.isPending

  return (
    <>
      <main className="flex flex-1 flex-col gap-2 bg-bg-surface">
        <div className="bg-bg-surface px-4 py-5">
          <Stepper steps={['종목 선택', '참가자 정보', '결제']} current={1} />
        </div>

        <Section title="선택한 대회">
          <RaceTicketCard
            image={race.images[0]}
            title={race.title}
            meta={`${race.startsAt} · ${race.place}`}
            ticket={`${option.name} · ${formatWon(option.price)}`}
            action={
              <Link href={`/races/${race.id}`} className={LINK}>
                변경
              </Link>
            }
          />
        </Section>

        <Section
          title="참가자 정보"
          aside={
            <Link href="/me/edit" className={LINK}>
              회원 정보에서 바꾸기 ›
            </Link>
          }
        >
          <TextField label="이름" error={errors.name?.message} {...register('name')} />
          <TextField
            label="생년월일"
            type="date"
            error={errors.birth?.message}
            {...register('birth')}
          />
          <PhoneField label="휴대폰 번호" error={errors.phone?.message} {...register('phone')} />
          <Divider />
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-label-m">성별</legend>
            <Controller
              control={control}
              name="gender"
              render={({ field }) => (
                <RadioGroup
                  value={field.value}
                  onValueChange={(gender) => field.onChange(gender)}
                  className="flex-row gap-6"
                >
                  <Radio value="MALE" label="남성" />
                  <Radio value="FEMALE" label="여성" />
                </RadioGroup>
              )}
            />
            {errors.gender?.message && (
              <p role="alert" className="text-caption text-text-danger">
                {errors.gender.message}
              </p>
            )}
          </fieldset>
          {sizes.length > 0 && (
            <Controller
              control={control}
              name="tshirtSize"
              render={({ field }) => (
                <Select
                  label="기념 티셔츠 사이즈"
                  items={sizes}
                  value={field.value || null}
                  onValueChange={(size) => field.onChange(size ?? '')}
                  error={errors.tshirtSize?.message}
                />
              )}
            />
          )}
          <TextField
            label="비상 연락처 이름"
            error={errors.emergencyContactName?.message}
            {...register('emergencyContactName')}
          />
          <PhoneField
            label="비상 연락처 전화번호"
            // 비상 연락처라 브라우저가 본인 번호를 제안하지 않게 한다
            autoComplete="off"
            helper="연락받을 분께 미리 알려 주세요"
            error={errors.emergencyContactPhone?.message}
            {...register('emergencyContactPhone')}
          />
          {option.kitIncluded && (
            <>
              <TextField
                label="키트 배송 우편번호"
                inputMode="numeric"
                error={errors.zipcode?.message}
                {...register('zipcode')}
              />
              <TextField
                label="키트 배송 주소"
                helper={`환불 기한(${refundDeadline})이 지난 뒤 이 주소로 키트를 보내요.`}
                error={errors.address?.message}
                {...register('address')}
              />
              <TextField
                label="상세 주소"
                error={errors.addressDetail?.message}
                {...register('addressDetail')}
              />
            </>
          )}
        </Section>

        <Section>
          <div className="flex flex-col gap-4 rounded-lg bg-bg-surface p-5 shadow-sm inset-ring inset-ring-border-default">
            <Checkbox
              checked={agreed}
              onCheckedChange={setAgreed}
              label={<span className="font-bold">[필수] 개인정보 제3자 제공 동의</span>}
            />
            <div className="flex flex-col gap-2">
              <KeyValue label="받는 곳" value={`${race.seller.company} (판매자)`} />
              <KeyValue label="목적" value="대회 운영 · 보험 가입" />
              <KeyValue
                label="항목"
                value="이름 · 생년월일 · 성별 · 연락처 2개 · 사이즈 · 배송 주소"
              />
              <KeyValue label="보유 기간" value="대회 종료 후 1년" />
              <p className="text-caption text-text-tertiary">동의하지 않으면 신청할 수 없어요.</p>
            </div>
          </div>
          <p className="text-caption text-text-tertiary">
            입력한 정보는 다음 신청 때 자동으로 채워져요.
          </p>
          <p className="text-caption text-text-tertiary">
            참가권은 다른 사람에게 양도할 수 없어요. 꼭 본인 정보로 신청해 주세요.
          </p>
          {saveError && <Alert tone="danger" title={saveError} />}
        </Section>
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        secondary={
          <Button variant="secondary" size="l" disabled={!agreed || busy} onClick={submit('cart')}>
            장바구니 담기
          </Button>
        }
        primary={
          <Button size="l" disabled={!agreed || busy} onClick={submit('checkout')}>
            바로 결제
          </Button>
        }
      />
      <Dialog
        open={replace !== null}
        onOpenChange={(open) => !open && setReplace(null)}
        title="장바구니를 바꿀까요?"
        body={
          <>
            장바구니에는 종목 1개만 담을 수 있어요.
            <br />
            {replace?.label ? `담아 둔 「${replace.label}」를 빼고` : '담아 둔 대회를 빼고'}
            <br />
            {`「${race.title} · ${option.name}」를 담아요.`}
          </>
        }
        confirmLabel="바꾸기"
        cancelLabel="그대로 두기"
        onConfirm={() => replace && replaceCart.mutate(replace)}
      />
    </>
  )
}
