'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { AddressSearch, type AddressSearchResult } from '@/features/address-search'
import { saveKitOptionIds, useBookingFlow, type BookingParticipant } from '@/features/booking'
import {
  admissionExpiredHref,
  isQueueNotAdmitted,
  useAdmissionTimer,
} from '@/features/queue-admission'
import { RaceTicketCard, type RaceDetail, type RaceOption } from '@/entities/race'
import {
  ApiError,
  memberApi,
  orderApi,
  productApi,
  unwrapData,
  useIdempotencyKey,
  type productModel,
} from '@/shared/api'
import { cn, formatWon, useTrackOnce } from '@/shared/lib'
import {
  Alert,
  BottomCta,
  Button,
  Checkbox,
  Chip,
  EmptyState,
  FOCUS_RING,
  HeaderTimer,
  KeyValue,
  PhoneField,
  Radio,
  RadioGroup,
  Section,
  Select,
  Skeleton,
  TextField,
} from '@/shared/ui'

import {
  EMPTY_PARTICIPANT_FORM,
  formatBirth,
  PARTICIPANT_ERRORS,
  participantFormSchema,
  toParticipantFormValues,
  toParticipantRequest,
  type ParticipantFormValues,
} from '../model/participant-form'

// 피그마 v3: 밑줄 글자 링크 (변경 · 회원 정보에서 바꾸기)
const LINK = cn('shrink-0 rounded-sm text-body2-medium text-text-secondary underline', FOCUS_RING)

type KitGroup = productModel.KitOptionGroupDto

// 피그마 V3 · M05 참가 신청 · 참가자 정보 → M05-2 키트 옵션 → 결제(M07). 하단 버튼은 「다음」 하나다.
// 키트 옵션이 없는 대회는 「다음」으로 바로 결제로 간다. 장바구니 담기는 이 화면에 없다.
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
  const { defaults, memberFailed, retryMember } = useParticipantDefaults(option)
  const preview = previewRemainingSeconds !== undefined
  // 키트 옵션이 있으면 참가자 정보 다음에 키트 옵션 단계를 둔다.
  // 못 불러오면 옵션이 없는 것으로 보지 않는다. 옵션이 있는 대회는 결제가 매번 거절되므로 진행을 막고 다시 시도하게 한다.
  const kit = productApi.useFindKitOptions(Number(race.id), {
    query: { select: unwrapData },
  })
  const timer = useAdmissionTimer(
    preview ? undefined : Number(race.id),
    preview ? undefined : Number(option.id),
    'follow',
  )
  const remaining = preview ? previewRemainingSeconds : timer.remaining
  // 0 참가자 정보, 1 키트 옵션. 헤더 뒤로 가기가 키트 옵션 단계에서 참가자 정보 단계로 돌아간다. 키트 조회 오류 화면이면 단계를 바꾸지 않고 이전 페이지로 간다.
  const [step, setStep] = useState(0)
  // 결제를 저장하는 중에는 헤더 뒤로 가기로 단계를 바꿀 수 없다
  const [saving, setSaving] = useState(false)
  useTrackOnce('apply_start', preview ? null : { raceId: race.id, courseId: option.id })
  return (
    <>
      <AppHeader
        type="sub"
        title="참가 신청"
        onBack={step === 1 && !kit.isError ? () => !saving && setStep(0) : undefined}
        action={remaining !== null && <HeaderTimer remainingSeconds={remaining} />}
      />
      {kit.isError ? (
        <main className="flex flex-1 items-center justify-center bg-bg-surface px-4 py-6">
          <EmptyState
            icon="CircleAlert"
            title="키트 옵션을 불러오지 못했어요"
            description="잠시 뒤 다시 시도해 주세요."
            action={
              <Button onClick={() => void kit.refetch()} disabled={kit.isFetching}>
                다시 시도하기
              </Button>
            }
          />
        </main>
      ) : defaults && !kit.isPending ? (
        <ParticipantForm
          // 회원 정보를 다시 받으면 입력 칸의 처음 값도 새로 채우려고 폼을 다시 만든다
          key={memberFailed ? 'member-failed' : 'member-ready'}
          race={race}
          option={option}
          defaults={defaults}
          memberFailed={memberFailed}
          retryMember={retryMember}
          kitGroups={kit.data?.kitOptions ?? []}
          step={step}
          setStep={setStep}
          setSaving={setSaving}
        />
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
 * 이름 · 생년월일 · 휴대폰 번호는 회원 정보 값을 쓰고 참가자가 바꿀 수 없다(바꾸려면 회원 정보에서).
 * 나머지는 이번에 입력해 둔 값 → 지난 신청 값 순으로 채운다. 불러오지 못하면 빈 칸으로 시작한다.
 */
function useParticipantDefaults(option: RaceOption) {
  const saved = useBookingFlow((s) => s.participant)
  const latest = orderApi.useFindLatestParticipant({
    query: { enabled: !saved, select: (response) => response.data ?? null },
  })
  const me = memberApi.useFindMe({ query: { select: unwrapData } })

  const loading = (!saved && latest.isPending) || me.isPending
  const base = saved
    ? toParticipantFormValues(saved, option)
    : latest.isSuccess && latest.data
      ? toParticipantFormValues(latest.data, option)
      : EMPTY_PARTICIPANT_FORM
  const defaults: ParticipantFormValues | null = loading
    ? null
    : me.isSuccess
      ? {
          ...base,
          name: me.data.name ?? base.name,
          birth: me.data.birthDate ?? base.birth,
          phone: me.data.phone ?? base.phone,
        }
      : base
  return {
    defaults,
    // 회원 정보를 못 불러오면 이름 · 생년월일 · 휴대폰 번호를 채울 수 없어 신청을 막는다
    memberFailed: me.isError,
    retryMember: () => void me.refetch(),
  }
}

function ParticipantForm({
  race,
  option,
  defaults,
  memberFailed,
  retryMember,
  kitGroups,
  step,
  setStep,
  setSaving,
}: {
  race: RaceDetail
  option: RaceOption
  defaults: ParticipantFormValues
  memberFailed: boolean
  retryMember: () => void
  kitGroups: KitGroup[]
  step: number
  setStep: (step: number) => void
  setSaving: (saving: boolean) => void
}) {
  const router = useRouter()
  const refundDeadline = race.schedule.find((row) => row.label === '전액 환불 기한')?.value
  const sizeNames = option.shirtSizes ?? []
  const sizes = sizeNames.map((v) => ({ value: v, label: v }))
  // 키트 옵션이 있으면 티셔츠 사이즈도 키트 옵션 단계에서 고른다
  const kitStep = kitGroups.length > 0
  const tshirtGroup = kitStep
    ? kitGroups.find((g) => g.values.every((v) => sizeNames.includes(v.value.toUpperCase())))
    : undefined
  const applications = useBookingFlow((s) => s.applications)
  const submitApplication = useBookingFlow((s) => s.submitApplication)
  const {
    register,
    control,
    handleSubmit,
    setValue,
    setFocus,
    formState: { errors },
  } = useForm<ParticipantFormValues>({
    resolver: zodResolver(participantFormSchema(kitStep ? { ...option, shirtSizes: [] } : option)),
    defaultValues: defaults,
  })
  const address = useWatch({ control, name: 'address' })
  // 주소 검색을 불러오지 못했을 때만 우편번호 · 주소를 직접 입력하게 연다
  const [manualAddress, setManualAddress] = useState(false)
  const pickAddress = ({ zipcode, address }: AddressSearchResult) => {
    setValue('zipcode', zipcode, { shouldValidate: true, shouldDirty: true })
    setValue('address', address, { shouldValidate: true, shouldDirty: true })
    // 예전 주소의 동 · 호수가 새 주소에 붙어 잘못 배송되지 않게 비운다
    setValue('addressDetail', '', { shouldDirty: true })
    setFocus('addressDetail')
  }
  // 필수 동의는 사용자가 직접 체크해야 버튼이 켜진다.
  const [agreed, setAgreed] = useState(false)
  // 키트 옵션 단계로 넘어갈 때 입력값을 들고 간다.
  const [confirmed, setConfirmed] = useState<ParticipantFormValues>(defaults)
  // 키트 옵션 이름 → 고른 값의 kitOptionId. 처음엔 아무것도 고르지 않는다.
  const [picked, setPicked] = useState<Record<string, number>>({})
  // 같은 참가 신청으로 결제를 다시 누르면(두 번 클릭 · 실패 뒤 재시도) 같은 Idempotency-Key를 보낸다.
  const orderKey = useIdempotencyKey()

  // 참가 신청을 저장하고(이번에 이미 저장한 종목이면 참가자 정보만 고친다) 주문을 만들어 결제로 간다.
  const save = useMutation({
    mutationFn: async (values: ParticipantFormValues) => {
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
      const orderId = await createOrder(participation.id ?? 0)
      // 결제가 옵션 이름마다 하나씩 보낼 수 있도록 고른 옵션 전체를 주문에 붙여 남긴다
      saveKitOptionIds(orderId, kitStep ? kitGroups.flatMap((g) => picked[g.name] ?? []) : [])
      return orderId
    },
    onMutate: () => setSaving(true),
    onSettled: () => setSaving(false),
    onSuccess: (orderId) => router.replace(`/checkout?orderId=${orderId}`),
    // 결제가 대기열 입장 전(입장 시간이 끝남)이라 막히면 입장 시간 만료 화면(M07-1)으로 간다.
    onError: (error) => {
      if (isQueueNotAdmitted(error)) router.replace(admissionExpiredHref(Number(race.id)))
    },
  })

  /** 참가 신청으로 주문을 만들어 자리를 잡는다. */
  async function createOrder(participationId: number) {
    const order = unwrapData(
      await orderApi.create({ participationId }, { idempotencyKey: orderKey(participationId) }),
    )
    return order.id ?? 0
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

  // 키트 옵션이 없으면 바로 저장하고 결제로, 있으면 키트 옵션 단계로 넘어간다.
  const next = handleSubmit((values) => {
    if (kitStep) {
      setConfirmed(values)
      setStep(1)
    } else {
      save.mutate(values)
    }
  })
  const allPicked = kitGroups.every((g) => picked[g.name] !== undefined)
  const pay = () => {
    // 참가 신청에는 티셔츠 사이즈만 보낸다. 고른 옵션 전체는 결제에서 kitOptionIds로 보낸다.
    const size = tshirtGroup?.values.find((v) => v.kitOptionId === picked[tshirtGroup.name])
    save.mutate({
      ...confirmed,
      tshirtSize: size ? size.value.toUpperCase() : confirmed.tshirtSize,
    })
  }
  // 409(이미 신청함 · 접수 기간 아님) · 400(나이 제한 밖 · 키트 배송 주소 없음)은 서버 문구를 그대로 보여 준다.
  const failure = isQueueNotAdmitted(save.error) ? null : save.error
  const saveError = failure
    ? ((failure instanceof ApiError ? failure.serverMessage : undefined) ??
      PARTICIPANT_ERRORS.unavailable)
    : null
  const busy = save.isPending

  return (
    <>
      <main className="flex flex-1 flex-col bg-bg-surface">
        <h2 className="px-4 pt-4 pb-2 text-title3">{step === 1 ? '키트 옵션' : '참가자 정보'}</h2>
        <div className="px-4 pt-1 pb-6">
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
        </div>

        {step === 1 ? (
          <Section className="gap-6 pt-2">
            {kitGroups.map((group) => (
              <fieldset key={group.name} className="flex flex-col gap-3">
                <legend className="mb-3 text-body1-bold">{group.name}</legend>
                <div className="flex flex-wrap gap-2">
                  {group.values.map((v) => (
                    <Chip
                      key={v.kitOptionId}
                      selected={picked[group.name] === v.kitOptionId}
                      onClick={() => setPicked((p) => ({ ...p, [group.name]: v.kitOptionId }))}
                    >
                      {v.value}
                    </Chip>
                  ))}
                </div>
              </fieldset>
            ))}
            {saveError && <Alert tone="danger" title={saveError} />}
          </Section>
        ) : (
          <>
            <Section
              title="회원 정보"
              aside={
                <Link href="/me/edit" className={LINK}>
                  회원 정보에서 바꾸기
                </Link>
              }
              className="gap-3"
            >
              {memberFailed ? (
                <div className="flex items-center justify-between gap-3">
                  <p className="text-body2-regular text-text-danger">
                    회원 정보를 불러오지 못했어요
                  </p>
                  <Button size="s" variant="secondary" onClick={retryMember}>
                    다시 시도하기
                  </Button>
                </div>
              ) : (
                <>
                  <KeyValue label="이름" value={defaults.name || '-'} />
                  <KeyValue label="생년월일" value={formatBirth(defaults.birth)} />
                </>
              )}
            </Section>

            <Section className="gap-6 pt-2">
              <fieldset className="flex flex-col gap-2">
                <legend className="mb-2 text-body2-medium">성별</legend>
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
                  <p role="alert" className="text-caption-regular text-text-danger">
                    {errors.gender.message}
                  </p>
                )}
              </fieldset>
              {/* 키트 옵션을 못 불러오거나 없는데 티셔츠가 있는 종목은 사이즈를 여기서 받는다 */}
              {!kitStep && sizes.length > 0 && (
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
                    label="받는 사람"
                    placeholder="받는 사람 이름"
                    error={errors.recipientName?.message}
                    {...register('recipientName')}
                  />
                  <PhoneField
                    label="받는 사람 연락처"
                    error={errors.recipientPhone?.message}
                    {...register('recipientPhone')}
                  />
                  <div className="flex flex-col gap-2">
                    {!manualAddress && <span className="text-body2-medium">키트 배송 주소</span>}
                    {manualAddress && (
                      <TextField
                        label="키트 배송 우편번호"
                        inputMode="numeric"
                        error={errors.zipcode?.message}
                        {...register('zipcode')}
                      />
                    )}
                    {manualAddress ? (
                      <TextField
                        label="키트 배송 주소"
                        error={errors.address?.message}
                        {...register('address')}
                      />
                    ) : null}
                    {/* 우편번호는 주소 검색 결과로만 저장하고 화면에는 쓰지 않는다 */}
                    <AddressSearch
                      onSelect={pickAddress}
                      onUnavailable={() => setManualAddress(true)}
                      summary={address || undefined}
                      label={address ? '다시 검색' : '주소 검색'}
                      variant={address ? 'text' : 'secondary'}
                    />
                    {!manualAddress && errors.address?.message && (
                      <p role="alert" className="text-caption-regular text-text-danger">
                        {errors.address.message}
                      </p>
                    )}
                    {address && (
                      <TextField
                        label="상세 주소"
                        error={errors.addressDetail?.message}
                        {...register('addressDetail')}
                      />
                    )}
                    <p className="text-caption-regular text-text-secondary">
                      환불 기한({refundDeadline})이 지난 뒤 이 주소로 키트를 보내요.
                    </p>
                  </div>
                </>
              )}
            </Section>

            <section className="flex flex-col gap-4 px-4 py-5">
              <div className="flex flex-col">
                <h3 className="text-body1-bold">개인정보 제3자 제공</h3>
                {[
                  ['받는 곳', `${race.seller.company} (판매자)`],
                  ['목적', '대회 운영 · 보험 가입'],
                  ['항목', '이름 · 생년월일 · 성별 · 연락처 2개 · 키트 옵션 · 배송 주소'],
                  ['보유 기간', '대회 종료 후 1년'],
                ].map(([label, value], i) => (
                  <div
                    key={label}
                    className={cn(
                      'flex gap-3 py-3 text-body2-regular',
                      i === 0 ? 'pt-4' : 'border-t border-border-default',
                    )}
                  >
                    <span className="w-18 shrink-0 text-text-secondary">{label}</span>
                    <span className="min-w-0 flex-1">{value}</span>
                  </div>
                ))}
                <p className="pt-2 text-body2-regular text-text-secondary">
                  동의하지 않으면 신청할 수 없어요.
                </p>
              </div>
              <Checkbox
                checked={agreed}
                onCheckedChange={setAgreed}
                label={<span className="font-bold">[필수] 개인정보 제3자 제공 동의</span>}
              />
              {saveError && <Alert tone="danger" title={saveError} />}
            </section>
          </>
        )}
      </main>
      <BottomCta
        className="sticky bottom-0 z-10"
        primary={
          step === 1 ? (
            <Button size="l" disabled={!allPicked || busy} onClick={pay}>
              결제하기
            </Button>
          ) : (
            <Button size="l" disabled={!agreed || busy || memberFailed} onClick={next}>
              다음
            </Button>
          )
        }
      />
    </>
  )
}
