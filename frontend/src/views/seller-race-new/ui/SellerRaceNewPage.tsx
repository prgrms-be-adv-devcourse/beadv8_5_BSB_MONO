'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { isAfter, isBefore, parseISO, startOfToday } from 'date-fns'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { parseRaceDateInput } from '@/entities/race'
import { ApiError } from '@/shared/api'
import { cn, formatNumber, formatWon, type SellerRaceFormEntry } from '@/shared/lib'
import {
  Alert,
  Button,
  DataTable,
  DateTimeField,
  FOCUS_RING,
  ImageCropDialog,
  ImageListDialog,
  ImageUpload,
  Radio,
  RadioGroup,
  TextField,
  useImageList,
  type ImageUploadValue,
  type UploadImage,
} from '@/shared/ui'

import {
  createRaceFormSchema,
  earliestSaleOpenDate,
  MAX_INTRO_IMAGES,
  RACE_FORM_DEFAULTS,
  RACE_FORM_ERRORS,
  type RaceCourseInput,
  type RaceFormValues,
} from '../model/race-form'
import { errorSections, useRaceFormTracking } from '../model/use-race-form-tracking'
import { useRegisterRace } from '../model/use-register-race'
import { CourseAddDialog } from './CourseAddDialog'

const FORM_ID = 'seller-race-new'

// 지난 날은 대회 날짜·접수 기간·환불 기한으로 고를 수 없다
const today = startOfToday()
const isPast = (date: Date) => isBefore(date, today)
// 접수 시작(판매 시작일)은 오늘로부터 이틀 뒤부터
const saleOpenEarliest = earliestSaleOpenDate(today)
const raceFormSchema = createRaceFormSchema({ today })

// 대회 날짜와 순서를 비교하는 칸들. 한 칸이 바뀌면 다 쓴 나머지 칸도 다시 검사한다
const ORDERED_FIELDS = ['individualOpenAt', 'applicationCloseAt', 'refundDeadline'] as const

/** 'yyyy-MM-dd' 글자의 날짜 (날짜를 아직 안 골랐으면 undefined) */
function dayOf(text: string): Date | undefined {
  const day = parseRaceDateInput(text)
  return day ? parseISO(day) : undefined
}

const KITS = [
  {
    option: '기념 티셔츠',
    size: 'S · M · L · XL · 2XL',
    courses: '10K · 5K',
    method: '배송',
    note: '사이즈표는 소개 이미지에',
  },
  {
    option: '배번',
    size: '없음',
    courses: '10K · 5K',
    method: '배송',
    note: '환불 기한 뒤 함께 배송',
  },
]

const LINK = cn('rounded-sm text-label-m text-text-brand', FOCUS_RING)

// 종목 표의 한 줄 (TODO(PRO-144): 키트 칸 나누기)
function courseRow(course: RaceCourseInput, id: string, index: number) {
  const sizes = course.tshirtSizeOption ?? []
  return {
    id,
    index,
    course: course.name,
    price: formatWon(course.price),
    capacity: `${formatNumber(course.crewQuota + course.individualQuota)}명`,
    age: `만 ${course.minAge}세 ~ ${course.maxAge ? `만 ${course.maxAge}세` : '제한 없음'}`,
    kit: course.kitIncluded ? (sizes.length ? `티셔츠 ${sizes.join(' · ')}` : '포함') : '없음',
  }
}

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
    formState: { errors, isSubmitted },
  } = useForm<RaceFormValues>({
    resolver: zodResolver(raceFormSchema),
    mode: 'onTouched',
    defaultValues: RACE_FORM_DEFAULTS,
  })
  // 종목 표의 줄 키는 useFieldArray의 id다. 이름이 같은 종목도 따로 지울 수 있다
  const courseFields = useFieldArray({ control, name: 'courses' })
  const [courseOpen, setCourseOpen] = useState(false)
  const schedule = useWatch({
    control,
    name: ['raceDate', 'individualOpenAt', 'applicationCloseAt', 'refundDeadline'],
  })
  const [raceDateText] = schedule
  const raceDay = dayOf(raceDateText)
  // 순서 오류는 서로 다른 칸을 비교해서 생긴다. 대회 날짜를 나중에 바꿔도 접수 · 환불 칸 오류가 다시 계산되도록,
  // 일정 칸 중 하나가 바뀌면 날짜를 고른 칸을 모두 다시 검사한다(등록하기를 누르기 전에도 칸 아래에 보인다)
  const scheduleKey = schedule.join('|')
  useEffect(() => {
    const [, ...ordered] = scheduleKey.split('|')
    const ready = ORDERED_FIELDS.filter((_, i) => parseRaceDateInput(ordered[i]) !== null)
    if (ready.length > 0) void trigger([...ready])
  }, [scheduleKey, trigger])
  const [cover, setCover] = useState<ImageUploadValue[]>([])
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [coverError, setCoverError] = useState<string>()
  const [cropOpen, setCropOpen] = useState(false)
  const [dropped, setDropped] = useState<File | null>(null)
  const [intro, setIntro] = useState<ImageUploadValue[]>([])
  const [listOpen, setListOpen] = useState(false)
  // 소개 이미지 미리보기 주소 → 원본 파일. 등록할 때 원본을 올린다
  const [introFiles, setIntroFiles] = useState<Record<string, File>>({})
  const keepLocal: UploadImage = async (file, onProgress) => {
    const url = URL.createObjectURL(file)
    setIntroFiles((prev) => ({ ...prev, [url]: file }))
    onProgress(1)
    return url
  }
  const list = useImageList({ upload: keepLocal, rules: { minWidth: 1080 } })
  const introError =
    intro.length > MAX_INTRO_IMAGES
      ? `소개 이미지는 ${MAX_INTRO_IMAGES}장까지 올릴 수 있어요.`
      : undefined

  const submit = handleSubmit(
    (values) => {
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
            router.push(`/seller/races?registered=${saved.raceId}`)
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
      description="판매가 시작되면 가격과 취소 · 환불 정책은 바꿀 수 없어요. 구매자에게 유리하게 바꾸는 것만 돼요."
      actions={
        <>
          <Button variant="secondary">미리보기</Button>
          <Button type="submit" form={FORM_ID} loading={registerRace.isPending}>
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
        <ConsoleCard title="기본 정보">
          <div className="flex gap-4">
            <TextField
              label="대회 이름"
              placeholder="2026 용감한 쿠키RUN in 서울"
              error={errors.title?.message}
              className="flex-1"
              {...register('title')}
            />
            <Controller
              control={control}
              name="raceDate"
              render={({ field }) => (
                <DateTimeField
                  mode="single"
                  withTime={false}
                  label="대회 날짜"
                  error={errors.raceDate?.message}
                  isDateDisabled={isPast}
                  startMonth={today}
                  onValueChange={field.onChange}
                  className="flex-1"
                />
              )}
            />
          </div>
          <div className="flex gap-4">
            <TextField
              label="장소"
              placeholder="서울숲 일대"
              error={errors.location?.message}
              className="flex-1"
              {...register('location')}
            />
            <TextField
              label="시·도"
              placeholder="서울특별시"
              helper="대회 찾기의 지역 필터에 쓰여요"
              error={errors.regionSido?.message}
              className="flex-1"
              {...register('regionSido')}
            />
            <TextField
              label="시·군·구"
              placeholder="성동구"
              error={errors.regionSigungu?.message}
              className="flex-1"
              {...register('regionSigungu')}
            />
          </div>
          <div className="flex gap-4">
            {/* 시작은 개인 예매 오픈(그날 00:00), 끝은 접수 마감(그날 23:59:59)으로 보낸다 */}
            <DateTimeField
              mode="range"
              withTime={false}
              label="접수 기간"
              helper="마감일 23:59까지 받아요 · 오늘로부터 이틀 뒤부터 · 대회 전날까지"
              error={errors.individualOpenAt?.message ?? errors.applicationCloseAt?.message}
              isDateDisabled={(date) =>
                isBefore(date, saleOpenEarliest) ||
                (raceDay !== undefined && !isBefore(date, raceDay))
              }
              startMonth={today}
              onValueChange={(range) => {
                setValue('individualOpenAt', range.from, {
                  shouldValidate: isSubmitted,
                })
                setValue('applicationCloseAt', range.to, {
                  shouldValidate: isSubmitted,
                })
              }}
              className="flex-1"
            />
            <TextField
              label="참가 가능 나이"
              defaultValue="종목마다 최소 · 최대 나이를 정해요"
              helper="대회 당일 만 나이 · 최소는 만 14세 이상"
              readOnly
              className="flex-1"
            />
          </div>
        </ConsoleCard>

        <ConsoleCard title="이미지">
          <div className="flex gap-4">
            <ImageUpload
              label="대표 이미지 (필수 · 1장)"
              helper="정사각(1:1) 1125×1125px 권장 · 대회 목록과 대회 상세 맨 위에 보여요"
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
              label="소개 이미지 (여러 장)"
              helper="코스 · 타임테이블 · 기념품 사이즈표를 넣어 주세요"
              value={intro}
              error={introError}
              onOpen={() => openList()}
              onFilesDrop={openList}
              onRemove={(id) => setIntro((prev) => prev.filter((v) => v.id !== id))}
              className="flex-1"
            />
          </div>
        </ConsoleCard>

        <ConsoleCard
          title="종목 · 참가권"
          aside={
            <button type="button" className={LINK} onClick={() => setCourseOpen(true)}>
              종목 추가
            </button>
          }
        >
          <DataTable
            label="종목 · 참가권"
            rows={courseFields.fields.map((f, i) => courseRow(f, f.id, i))}
            rowKey={(r) => r.id}
            columns={[
              { key: 'course', header: '종목', render: (r) => r.course },
              { key: 'price', header: '참가비', align: 'right', render: (r) => r.price },
              { key: 'capacity', header: '모집 정원', align: 'right', render: (r) => r.capacity },
              { key: 'age', header: '참가 나이 (최소 ~ 최대)', render: (r) => r.age },
              { key: 'kit', header: '키트 (참가권 포함)', render: (r) => r.kit },
              {
                key: 'remove',
                header: '',
                align: 'right',
                render: (r) => (
                  <button
                    type="button"
                    className={LINK}
                    aria-label={`${r.course} 종목 삭제`}
                    onClick={() => courseFields.remove(r.index)}
                  >
                    삭제
                  </button>
                ),
              },
            ]}
          />
          {errors.courses?.message && (
            <p className="text-body-s text-text-danger">{errors.courses.message}</p>
          )}
          <Alert
            title="다른 곳에서도 함께 팔 수 있어요"
            description="정원에는 크루런에 배정할 수량만 적어 주세요. 한 사람 한 대회 1건 규칙은 크루런 안에서만 지켜져요."
          />
        </ConsoleCard>

        <ConsoleCard title="취소 · 환불 정책">
          <Controller
            control={control}
            name="refundDeadline"
            render={({ field }) => (
              // 백엔드는 날짜만 받고 그 날 23:59:59까지로 본다
              <DateTimeField
                mode="single"
                withTime={false}
                label="환불 기한"
                helper="이 날짜(23:59)까지 취소하면 전액 환불, 그 뒤 개인 사정 환불은 안 돼요. 결제 전에 구매자에게 보여 줘요."
                error={errors.refundDeadline?.message}
                isDateDisabled={(date) =>
                  isPast(date) || (raceDay !== undefined && isAfter(date, raceDay))
                }
                startMonth={today}
                onValueChange={field.onChange}
              />
            )}
          />
        </ConsoleCard>

        <ConsoleCard
          title="키트 배송"
          aside={
            <button type="button" className={LINK}>
              옵션 추가
            </button>
          }
        >
          <p className="text-caption text-text-tertiary">
            키트는 배송으로만 보내요. 환불 기한이 지난 뒤, 참가 신청 때 받은 주소로 보내요. 사이즈는
            옵션마다 신청 때 받아요.
          </p>
          <DataTable
            label="키트 옵션"
            rows={KITS}
            rowKey={(r) => r.option}
            columns={[
              { key: 'option', header: '키트 옵션', render: (r) => r.option },
              { key: 'size', header: '사이즈', render: (r) => r.size },
              { key: 'courses', header: '포함 종목', render: (r) => r.courses },
              { key: 'method', header: '받는 방법', render: (r) => r.method },
              { key: 'note', header: '안내', render: (r) => r.note },
            ]}
          />
        </ConsoleCard>

        <ConsoleCard title="공개 범위">
          <RadioGroup defaultValue="public" className="flex-row gap-6">
            <Radio value="public" label="전체 공개 (기본)" />
            <Radio value="link" label="링크로만 공개" />
          </RadioGroup>
          <p className="text-caption text-text-tertiary">
            링크로만 공개하면 대회 목록 · 검색에 나오지 않아요. 링크로 들어온 회원은 신청할 수 있고,
            링크가 퍼져도 막지 않아요.
          </p>
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
        onOpenChange={setListOpen}
        items={list.items}
        onFilesAdd={(files) => void list.add(files)}
        onRemove={list.remove}
        onMove={list.move}
        onSave={() => {
          setIntro(
            list.items
              .filter((item) => item.status === 'done')
              .map((item) => ({ id: item.id, url: item.url ?? item.previewUrl, name: item.name })),
          )
          setListOpen(false)
        }}
      />
    </ConsolePage>
  )
}

/** 등록 실패 문구. 서버가 준 문구(승인되지 않은 판매자, 판매 시작일 규칙 등)를 그대로 보여 준다. */
function registerErrorMessage(error: Error | null): string {
  if (error instanceof ApiError && error.serverMessage) return error.serverMessage
  return '사진이나 대회를 올리지 못했어요. 잠시 후 다시 시도해 주세요.'
}
