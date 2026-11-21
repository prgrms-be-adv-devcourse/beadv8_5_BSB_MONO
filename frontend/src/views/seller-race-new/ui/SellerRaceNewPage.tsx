'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { isAfter, isBefore, parseISO, startOfToday } from 'date-fns'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch, type Resolver } from 'react-hook-form'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { AddressSearch } from '@/features/address-search'
import { parseRaceDateInput } from '@/entities/race'
import { ApiError } from '@/shared/api'
import { DateTimeField } from '@/shared/date-picker'
import { formatNumber, type SellerRaceFormEntry } from '@/shared/lib'
import {
  Alert,
  Button,
  Chip,
  DataTable,
  digitsOnly,
  ImageCropDialog,
  ImageListDialog,
  ImageUpload,
  RequiredMark,
  Select,
  Textarea,
  TextField,
  useImageList,
  type ImageUploadValue,
  type UploadImage,
} from '@/shared/ui'

import {
  ADULT_MIN_AGE,
  createRaceFormSchema,
  earliestSaleOpenDate,
  MAX_INTRO_IMAGES,
  NO_LIMIT_MIN_AGE,
  parseDateTimeInput,
  RACE_FORM_DEFAULTS,
  RACE_FORM_ERRORS,
  type RaceFormValues,
} from '../model/race-form'
import { errorSections, useRaceFormTracking } from '../model/use-race-form-tracking'
import { KitOptionsNotSavedError, useRegisterRace } from '../model/use-register-race'
import { CourseAddDialog } from './CourseAddDialog'

const FORM_ID = 'seller-race-new'

// 오늘은 쓸 때마다 계산한다. 화면을 연 채 자정을 넘겨도 어제를 오늘로 쓰지 않는다
// 지난 날은 대회 날짜·환불 기한으로 고를 수 없다
const isPast = (date: Date) => isBefore(date, startOfToday())
// 판매 시작(크루 예매 시작 · 일반 판매 시작)은 오늘로부터 이틀 뒤부터
const isBeforeSaleOpen = (date: Date) => isBefore(date, earliestSaleOpenDate(startOfToday()))
const raceFormResolver: Resolver<RaceFormValues> = (...args) =>
  zodResolver(createRaceFormSchema({ today: startOfToday() }))(...args)

const AGE_ITEMS = [
  { value: String(NO_LIMIT_MIN_AGE) as '14', label: `만 ${NO_LIMIT_MIN_AGE}세 이상` },
  { value: String(ADULT_MIN_AGE) as '19', label: `만 ${ADULT_MIN_AGE}세 이상` },
]

/** 'yyyy-MM-dd' 글자의 날짜 (날짜를 아직 안 골랐으면 undefined) */
function dayOf(text: string): Date | undefined {
  const day = parseRaceDateInput(text)
  return day ? parseISO(day) : undefined
}

const finite = (value: number) => (Number.isFinite(value) ? value : 0)

// 피그마 V2 · S02 대회 등록 (+ S02-1~3 이미지 올리기 · 자르기 창, S02-4 등록 완료)
// 사진은 고를 때는 브라우저 안 주소로 미리 보여 주고, 등록하기를 누를 때 파일 서비스로 올린다.
export function SellerRaceNewPage({ entry = 'direct' }: { entry?: SellerRaceFormEntry }) {
  const router = useRouter()
  const registerRace = useRegisterRace()
  const tracking = useRaceFormTracking(entry)
  const {
    control,
    register,
    handleSubmit,
    setValue,
    trigger,
    clearErrors,
    formState: { errors, isSubmitted },
  } = useForm<RaceFormValues>({
    resolver: raceFormResolver,
    mode: 'onTouched',
    defaultValues: RACE_FORM_DEFAULTS,
  })
  // 종목 표의 줄 키는 useFieldArray의 id다. 이름이 같은 종목도 따로 지울 수 있다
  const courseFields = useFieldArray({ control, name: 'courses' })
  const kitFields = useFieldArray({ control, name: 'kitOptions' })
  const [courseOpen, setCourseOpen] = useState(false)
  const [searching, setSearching] = useState(false)
  const location = useWatch({ control, name: 'location' })
  const crewOpen = useWatch({ control, name: 'crewOpen' })
  const courses = useWatch({ control, name: 'courses' })
  // 시·도 · 시·군·구는 주소 검색 결과로만 채운다. 검색을 못 불러오면 둘 다, 시·군·구가 없는 곳(세종)이면 시·군·구만 직접 쓴다
  const [manualRegion, setManualRegion] = useState({ sido: false, sigungu: false })
  const schedule = useWatch({
    control,
    name: [
      'raceDate',
      'raceTime',
      'crewSaleStartAt',
      'crewSaleEndAt',
      'individualOpenAt',
      'applicationCloseAt',
      'refundDeadline',
    ],
  })
  const raceDay = dayOf(schedule[0])
  // 순서 오류는 서로 다른 칸을 비교해서 생긴다. 대회 날짜를 나중에 바꿔도 오류가 다시 계산되도록,
  // 일정 칸 중 하나가 바뀌면 다 쓴 칸을 모두 다시 검사한다(등록하기를 누르기 전에도 칸 아래에 보인다)
  const scheduleKey = schedule.join('|')
  useEffect(() => {
    const [, , crewStart, crewEnd, open, close, refund] = scheduleKey.split('|')
    const dateTimes: [keyof RaceFormValues, string][] = [
      ['individualOpenAt', open],
      ['applicationCloseAt', close],
    ]
    if (crewOpen) dateTimes.push(['crewSaleStartAt', crewStart], ['crewSaleEndAt', crewEnd])
    const ready = dateTimes.filter(([, text]) => parseDateTimeInput(text) !== null).map(([n]) => n)
    if (parseRaceDateInput(refund) !== null) ready.push('refundDeadline')
    if (ready.length > 0) void trigger(ready)
  }, [scheduleKey, crewOpen, trigger])

  function openCrew() {
    setValue('crewOpen', true, { shouldDirty: true })
  }
  // 닫으면 크루 예매 날짜와 크루 정원을 비운다. 날짜 칸은 닫으면 사라져 다시 열면 빈 칸으로 시작하므로 값도 같이 비운다
  function closeCrew() {
    setValue('crewOpen', false, { shouldDirty: true })
    setValue('crewSaleStartAt', '')
    setValue('crewSaleEndAt', '')
    clearErrors(['crewSaleStartAt', 'crewSaleEndAt'])
    courseFields.fields.forEach((_, i) => setValue(`courses.${i}.crewQuota`, 0))
  }
  const [cover, setCover] = useState<ImageUploadValue[]>([])
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [coverError, setCoverError] = useState<string>()
  const [cropOpen, setCropOpen] = useState(false)
  const [dropped, setDropped] = useState<File | null>(null)
  const [intro, setIntro] = useState<ImageUploadValue[]>([])
  const [listOpen, setListOpen] = useState(false)
  // 소개 이미지 미리보기 주소 → 원본 파일. 등록할 때 원본을 올린다.
  // 목록 창의 미리보기 주소는 창에서 빼면 바로 해제돼, 저장한 목록이 쓸 주소는 이 화면이 따로 만들고 해제한다
  const [introFiles, setIntroFiles] = useState<Record<string, File>>({})
  const keepLocal: UploadImage = async (file, onProgress) => {
    const url = URL.createObjectURL(file)
    setIntroFiles((prev) => ({ ...prev, [url]: file }))
    onProgress(1)
    return url
  }
  /** 저장한 소개 이미지(kept)가 쓰지 않는 주소를 해제한다 */
  function releaseIntro(kept: ImageUploadValue[]) {
    const used = new Set(kept.map((v) => v.url))
    Object.keys(introFiles).forEach((url) => !used.has(url) && URL.revokeObjectURL(url))
    setIntroFiles((prev) =>
      Object.fromEntries(
        Object.entries(prev).filter(([url]) => used.has(url) || !(url in introFiles)),
      ),
    )
  }
  // 화면을 떠날 때 남은 주소를 해제하려고 마지막 값을 들고 있는다
  const latestIntroFiles = useRef(introFiles)
  useEffect(() => {
    latestIntroFiles.current = introFiles
  }, [introFiles])
  useEffect(() => {
    const latest = latestIntroFiles
    return () => Object.keys(latest.current).forEach((url) => URL.revokeObjectURL(url))
  }, [])
  // 대표 이미지는 바꾸거나 지우거나 화면을 떠나면 이전 주소를 해제한다
  const coverUrl = cover[0]?.url
  useEffect(() => {
    if (coverUrl) return () => URL.revokeObjectURL(coverUrl)
  }, [coverUrl])
  const list = useImageList({ upload: keepLocal, rules: { minWidth: 1080 } })
  const introError =
    intro.length > MAX_INTRO_IMAGES
      ? `소개 이미지는 ${MAX_INTRO_IMAGES}장까지 올릴 수 있어요.`
      : undefined

  // 종목 표 안에서 바로 쓰는 정원 칸 (숫자만, 비우면 숫자를 쓰라는 안내)
  const quotaColumn = (key: 'crewQuota' | 'individualQuota', header: string) => ({
    key,
    header,
    align: 'right' as const,
    render: (r: { index: number; course: { name: string } }) => (
      <TextField
        compact
        aria-label={`${r.course.name} ${header}`}
        inputMode="numeric"
        filter={digitsOnly}
        error={errors.courses?.[r.index]?.[key]?.message}
        {...register(`courses.${r.index}.${key}`, { valueAsNumber: true })}
      />
    ),
  })

  // 등록에 성공해 화면을 옮기는 동안에도 잠가 두 번 등록하지 않는다
  const locked = registerRace.isPending || registerRace.isSuccess
  const submit = handleSubmit(
    (values) => {
      if (locked) return
      if (!coverFile || introError) {
        if (!coverFile) setCoverError(RACE_FORM_ERRORS.coverRequired)
        tracking.formError(errorSections({}, true))
        return
      }
      const introList = intro.flatMap((v) => {
        const file = introFiles[v.url]
        return file ? [file] : []
      })
      const registered = tracking.beforeRegister()
      registerRace.mutate(
        { values, images: { cover: coverFile, intro: introList } },
        {
          onSuccess: (saved) => {
            registered(saved.raceId, values.courses.length)
            router.replace(`/seller/races?registered=${saved.raceId}`)
          },
        },
      )
    },
    (invalid) => {
      if (!coverFile) setCoverError(RACE_FORM_ERRORS.coverRequired)
      tracking.formError(errorSections(invalid, !coverFile || introError !== undefined))
    },
  )

  function openList(files: File[] = []) {
    // 창을 열 때는 저장된 목록에서 시작한다
    list.setItems(
      intro.map((v) => ({
        id: v.id,
        name: v.name,
        previewUrl: v.url,
        url: v.url,
        status: 'done',
        progress: 1,
      })),
    )
    setListOpen(true)
    if (files.length) void list.add(files)
  }

  return (
    <ConsolePage
      title="대회 등록"
      actions={
        <>
          {/* 꺼진 버튼은 마우스 이벤트를 받지 않아, 감싼 span의 :hover로 말풍선을 띄운다 */}
          <span className="group relative inline-flex">
            <Button variant="secondary" disabled aria-describedby="race-preview-reason">
              미리보기
            </Button>
            <span
              id="race-preview-reason"
              role="tooltip"
              className="pointer-events-none absolute top-full right-0 z-10 mt-2 hidden w-max rounded-md bg-bg-inverse px-3 py-2 text-caption-regular text-text-inverse shadow-md group-hover:block"
            >
              미리보기는 아직 준비 중이에요
            </span>
          </span>
          <Button type="submit" form={FORM_ID} loading={locked}>
            등록하기
          </Button>
        </>
      }
    >
      <form id={FORM_ID} noValidate onSubmit={submit} className="contents">
        {registerRace.isError && (
          <Alert
            tone="danger"
            title="대회를 등록하지 못했어요"
            description={registerErrorMessage(registerRace.error)}
          />
        )}
        <div className="max-lg:grid-cols-1 grid grid-cols-2 gap-6">
          <ConsoleCard title="기본 정보">
            <div className="flex flex-col gap-4">
              <TextField
                label={
                  <>
                    대회 이름
                    <RequiredMark />
                  </>
                }
                placeholder="2026 용감한 쿠키RUN in 서울"
                error={errors.title?.message}
                {...register('title')}
              />
              <div className="flex gap-4">
                <Controller
                  control={control}
                  name="raceDate"
                  render={({ field }) => (
                    <DateTimeField
                      mode="single"
                      withTime={false}
                      label="대회 날짜"
                      required
                      error={errors.raceDate?.message}
                      isDateDisabled={isPast}
                      startMonth={startOfToday()}
                      onValueChange={field.onChange}
                    />
                  )}
                />
                <TextField
                  label={
                    <>
                      출발 시각
                      <RequiredMark />
                    </>
                  }
                  placeholder="08:00"
                  inputMode="numeric"
                  maxLength={5}
                  filter={(value) => value.replace(/[^\d:]/g, '')}
                  filterMessage="숫자와 : 만 입력할 수 있어요"
                  error={errors.raceTime?.message}
                  className="flex-1"
                  {...register('raceTime')}
                />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              {manualRegion.sido ? (
                <TextField
                  label={
                    <>
                      장소
                      <RequiredMark />
                    </>
                  }
                  placeholder="서울숲 일대"
                  error={errors.location?.message}
                  {...register('location')}
                />
              ) : (
                <>
                  <span className="text-body2-medium">
                    장소
                    <RequiredMark />
                  </span>
                  {location && (
                    <div className="flex items-center justify-between gap-4">
                      <p className="text-body2-regular">{location}</p>
                      <Button variant="secondary" size="s" onClick={() => setSearching(true)}>
                        다시 검색
                      </Button>
                    </div>
                  )}
                </>
              )}
              {(!location || searching || manualRegion.sido) && (
                <AddressSearch
                  defaultOpen={searching}
                  onSelect={({ address, sido, sigungu }) => {
                    setValue('location', address, { shouldValidate: true, shouldDirty: true })
                    setValue('regionSido', sido, { shouldValidate: true, shouldDirty: true })
                    setValue('regionSigungu', sigungu, {
                      shouldValidate: isSubmitted,
                      shouldDirty: true,
                    })
                    setManualRegion((manual) => ({ ...manual, sigungu: manual.sido || !sigungu }))
                    setSearching(false)
                  }}
                  onUnavailable={() => setManualRegion({ sido: true, sigungu: true })}
                />
              )}
              {!manualRegion.sido && errors.location?.message && (
                <p className="text-caption-regular text-text-danger">{errors.location.message}</p>
              )}
            </div>
            {(manualRegion.sido || manualRegion.sigungu) && (
              <div className="flex gap-4">
                {manualRegion.sido && (
                  <TextField
                    label="시·도"
                    placeholder="서울특별시"
                    error={errors.regionSido?.message}
                    className="flex-1"
                    {...register('regionSido')}
                  />
                )}
                <TextField
                  label="시·군·구"
                  placeholder="성동구"
                  error={errors.regionSigungu?.message}
                  className="flex-1"
                  {...register('regionSigungu')}
                />
              </div>
            )}
            {(location || manualRegion.sido) && (
              <TextField
                label="상세 주소"
                placeholder="건물명, 출입구 등"
                error={errors.locationDetail?.message}
                {...register('locationDetail')}
              />
            )}
            <Textarea
              label="대회 소개"
              maxLength={2000}
              error={errors.description?.message}
              {...register('description')}
            />
          </ConsoleCard>

          <ConsoleCard title="판매 일정">
            <div className="flex flex-col gap-4">
              <div role="group" aria-labelledby="crew-open-label" className="flex flex-col gap-2">
                <span id="crew-open-label" className="text-body2-medium">
                  크루 선예매
                </span>
                <div className="flex gap-2">
                  <Chip selected={crewOpen} onClick={openCrew}>
                    열기
                  </Chip>
                  <Chip selected={!crewOpen} onClick={closeCrew}>
                    열지 않기
                  </Chip>
                </div>
              </div>
              {/* 시각은 시안처럼 칸 옆에 직접 쓴다. 서버에는 그 시각 그대로 보낸다(23:59는 그날 끝) */}
              {crewOpen && (
                <div className="flex gap-4">
                  <Controller
                    control={control}
                    name="crewSaleStartAt"
                    render={({ field }) => (
                      <DateTimeField
                        mode="single"
                        label="크루 예매 시작"
                        required
                        error={errors.crewSaleStartAt?.message}
                        isDateDisabled={isBeforeSaleOpen}
                        startMonth={startOfToday()}
                        onValueChange={field.onChange}
                      />
                    )}
                  />
                  <Controller
                    control={control}
                    name="crewSaleEndAt"
                    render={({ field }) => (
                      <DateTimeField
                        mode="single"
                        label="크루 예매 마감"
                        required
                        helper="마감 뒤 남은 크루 자리는 일반 판매로 넘어가요"
                        error={errors.crewSaleEndAt?.message}
                        isDateDisabled={isBeforeSaleOpen}
                        startMonth={startOfToday()}
                        onValueChange={field.onChange}
                      />
                    )}
                  />
                </div>
              )}
              <div className="flex gap-4">
                <Controller
                  control={control}
                  name="individualOpenAt"
                  render={({ field }) => (
                    <DateTimeField
                      mode="single"
                      label="일반 판매 시작"
                      required
                      helper={crewOpen ? '크루 예매 마감 뒤로 고를 수 있어요' : undefined}
                      error={errors.individualOpenAt?.message}
                      isDateDisabled={(date) =>
                        isBeforeSaleOpen(date) || (raceDay !== undefined && isAfter(date, raceDay))
                      }
                      startMonth={startOfToday()}
                      onValueChange={field.onChange}
                    />
                  )}
                />
                <Controller
                  control={control}
                  name="applicationCloseAt"
                  render={({ field }) => (
                    <DateTimeField
                      mode="single"
                      label="접수 마감"
                      required
                      helper="대회 시작 전까지 고를 수 있어요"
                      error={errors.applicationCloseAt?.message}
                      isDateDisabled={(date) =>
                        isBeforeSaleOpen(date) || (raceDay !== undefined && isAfter(date, raceDay))
                      }
                      startMonth={startOfToday()}
                      onValueChange={field.onChange}
                    />
                  )}
                />
              </div>
            </div>
          </ConsoleCard>
        </div>

        <div className="max-lg:grid-cols-1 grid grid-cols-2 gap-6">
          <ConsoleCard title="이미지">
            <div className="flex flex-col gap-4">
              <ImageUpload
                label={
                  <>
                    대표 이미지
                    <RequiredMark />
                  </>
                }
                value={cover}
                error={coverError}
                onOpen={() => {
                  setDropped(null)
                  setCropOpen(true)
                }}
                onFilesDrop={(files) => {
                  setDropped(files[0] ?? null)
                  setCropOpen(true)
                }}
                onRemove={() => {
                  setCover([])
                  setCoverFile(null)
                }}
                className="flex-1"
              />
              <ImageUpload
                type="multiple"
                label="소개 이미지"
                value={intro}
                error={introError}
                onOpen={() => openList()}
                onFilesDrop={openList}
                onRemove={(id) => setIntro((prev) => prev.filter((v) => v.id !== id))}
                className="flex-1"
              />
            </div>
          </ConsoleCard>

          <div className="flex flex-col gap-6">
            <ConsoleCard title="취소 · 환불">
              <Controller
                control={control}
                name="refundDeadline"
                render={({ field }) => (
                  // 백엔드는 날짜만 받고 그 날 23:59:59까지로 본다
                  <DateTimeField
                    mode="single"
                    withTime={false}
                    label="환불 기한"
                    required
                    helper="이 날 23:59까지 취소하면 전액 환불돼요"
                    error={errors.refundDeadline?.message}
                    isDateDisabled={(date) =>
                      isPast(date) || (raceDay !== undefined && isAfter(date, raceDay))
                    }
                    startMonth={startOfToday()}
                    onValueChange={field.onChange}
                  />
                )}
              />
            </ConsoleCard>

            <ConsoleCard
              className="flex-1"
              title="키트"
              aside={
                <Button
                  variant="secondary"
                  size="s"
                  onClick={() => kitFields.append({ name: '', value: '', stock: '' })}
                >
                  옵션 추가
                </Button>
              }
            >
              <DataTable
                label="키트 옵션"
                rows={kitFields.fields.map((f, index) => ({ id: f.id, index }))}
                rowKey={(r) => r.id}
                columns={[
                  {
                    key: 'name',
                    header: '옵션 이름',
                    render: ({ index }) => (
                      <TextField
                        aria-label={`옵션 이름 ${index + 1}`}
                        placeholder="티셔츠"
                        error={errors.kitOptions?.[index]?.name?.message}
                        {...register(`kitOptions.${index}.name`)}
                      />
                    ),
                  },
                  {
                    key: 'value',
                    header: '옵션 값',
                    render: ({ index }) => (
                      <TextField
                        aria-label={`옵션 값 ${index + 1}`}
                        placeholder="M"
                        error={errors.kitOptions?.[index]?.value?.message}
                        {...register(`kitOptions.${index}.value`)}
                      />
                    ),
                  },
                  {
                    key: 'stock',
                    header: '준비 수량',
                    render: ({ index }) => (
                      <TextField
                        aria-label={`준비 수량 ${index + 1}`}
                        inputMode="numeric"
                        filter={digitsOnly}
                        placeholder="맞춤 제작"
                        error={errors.kitOptions?.[index]?.stock?.message}
                        {...register(`kitOptions.${index}.stock`)}
                      />
                    ),
                  },
                  {
                    key: 'remove',
                    header: '',
                    align: 'right',
                    render: ({ index }) => (
                      <Button
                        variant="secondary"
                        size="s"
                        className="text-text-danger"
                        aria-label={`키트 옵션 ${index + 1} 삭제`}
                        onClick={() => kitFields.remove(index)}
                      >
                        삭제
                      </Button>
                    ),
                  },
                ]}
              />
            </ConsoleCard>
          </div>
        </div>

        <ConsoleCard
          title="종목 · 참가권"
          aside={
            <Button variant="secondary" size="s" onClick={() => setCourseOpen(true)}>
              종목 추가
            </Button>
          }
        >
          <DataTable
            label="종목 · 참가권"
            rows={courseFields.fields.map((f, index) => ({
              id: f.id,
              index,
              course: courses[index] ?? f,
            }))}
            rowKey={(r) => r.id}
            columns={[
              { key: 'course', header: '종목', render: (r) => r.course.name },
              {
                key: 'distance',
                header: '거리',
                align: 'right',
                render: (r) => `${r.course.distanceKm}km`,
              },
              {
                key: 'price',
                header: '참가비',
                align: 'right',
                render: (r) => `${formatNumber(r.course.price)}원`,
              },
              // 크루 선예매를 열 때만 크루 정원을 받는다. 정원은 표 안에서 바로 입력한다
              ...(crewOpen ? [quotaColumn('crewQuota', '크루 정원')] : []),
              quotaColumn('individualQuota', '일반 정원'),
              {
                key: 'total',
                header: '총 정원',
                align: 'right',
                render: (r) => {
                  const crew = crewOpen ? finite(r.course.crewQuota) : 0
                  return (
                    <span className="text-body2-bold">
                      {formatNumber(crew + finite(r.course.individualQuota))}명
                    </span>
                  )
                },
              },
              {
                key: 'age',
                header: '최소 나이',
                render: (r) => (
                  <Controller
                    control={control}
                    name={`courses.${r.index}.minAge`}
                    render={({ field }) => (
                      <Select
                        compact
                        aria-label={`${r.course.name} 최소 나이`}
                        items={AGE_ITEMS}
                        value={String(field.value) as '14' | '19'}
                        onValueChange={(next) => next && field.onChange(Number(next))}
                      />
                    )}
                  />
                ),
              },
              {
                key: 'kit',
                header: '키트',
                render: (r) => (r.course.kitIncluded ? '포함' : '없음'),
              },
              {
                key: 'remove',
                header: '',
                align: 'right',
                render: (r) => (
                  <Button
                    variant="secondary"
                    size="s"
                    className="text-text-danger"
                    aria-label={`${r.course.name} 종목 삭제`}
                    onClick={() => courseFields.remove(r.index)}
                  >
                    삭제
                  </Button>
                ),
              },
            ]}
          />
          {/* 종목 줄에 입력칸이 있으면 종목 목록 전체 오류는 root에 담긴다 */}
          {(errors.courses?.message ?? errors.courses?.root?.message) && (
            <p className="text-body2-regular text-text-danger">
              {errors.courses.message ?? errors.courses.root?.message}
            </p>
          )}
        </ConsoleCard>
      </form>

      <CourseAddDialog
        open={courseOpen}
        onOpenChange={setCourseOpen}
        onAdd={(course) => courseFields.append(course)}
      />
      <ImageCropDialog
        open={cropOpen}
        onOpenChange={setCropOpen}
        file={dropped}
        onApply={(blob, name) => {
          setCover([{ id: 'cover', url: URL.createObjectURL(blob), name, meta: '1125×1125' }])
          setCoverFile(new File([blob], name, { type: blob.type }))
          setCoverError(undefined)
          setCropOpen(false)
        }}
      />
      <ImageListDialog
        open={listOpen}
        onOpenChange={(open) => {
          if (!open) releaseIntro(intro)
          setListOpen(open)
        }}
        items={list.items}
        onFilesAdd={(files) => void list.add(files)}
        onRemove={list.remove}
        onMove={list.move}
        onSave={() => {
          const saved = list.items
            .filter((item) => item.status === 'done')
            .map((item) => ({ id: item.id, url: item.url ?? item.previewUrl, name: item.name }))
          setIntro(saved)
          releaseIntro(saved)
          setListOpen(false)
        }}
      />
    </ConsolePage>
  )
}

/** 등록 실패 문구. 서버가 준 문구(승인되지 않은 판매자, 판매 시작일 규칙 등)를 그대로 보여 준다. */
function registerErrorMessage(error: Error | null): string {
  if (error instanceof KitOptionsNotSavedError) {
    return '대회는 등록했지만 키트 옵션을 저장하지 못했어요. 등록하기를 다시 누르면 등록된 대회에 키트 옵션만 저장해요.'
  }
  if (error instanceof ApiError && error.serverMessage) return error.serverMessage
  return '사진이나 대회를 올리지 못했어요. 잠시 후 다시 시도해 주세요.'
}
