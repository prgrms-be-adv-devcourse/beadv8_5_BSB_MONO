'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import { parseISO } from 'date-fns'
import { useEffect, useRef, useState, type PointerEvent } from 'react'
import { Controller, useForm } from 'react-hook-form'

import { DateTimeField } from '@/shared/date-picker'
import {
  Alert,
  Badge,
  BottomCta,
  Button,
  buttonClassName,
  Chip,
  Spinner,
  TextField,
} from '@/shared/ui'

import { useSubmitRecordCert } from '../api/record-cert-api'
import {
  COURSE_LABEL,
  EMPTY_VALUES,
  netSeconds,
  prefill,
  type CertFormValues,
} from '../lib/cert-form'
import type { Roi } from '../lib/cert-reader'
import { decodeImage } from '../lib/decode-image'
import { extractFields } from '../lib/extract'
import { toRoi, wholeImage } from '../lib/roi'
import { stripGps } from '../lib/strip-gps'
import { certFormSchema, type CertFormInput } from '../model/cert-form-schema'
import spec from '../model/ocr-spec.json'
import { COURSE_CLASSES, type CheckField, type Quad } from '../model/types'
import { useCertReader } from '../model/use-cert-reader'

// 피그마 기록증 심사 시안 P1(사진 고르기 · 읽을 부분 짚기 · 읽는 중) · P2(값 확인) · P3(제출 뒤). 대상은 노트북 브라우저.
// 사진은 자르지 않고 올린다(위치 GPS만 지우고 회전·픽셀은 그대로). 읽을 부분은 검출 범위로만 쓰고 좌표를 함께 보낸다.

type Photo = { file: File; url: string; image: ImageData }
type ReadResult = {
  values: CertFormValues
  boxes: Partial<Record<CheckField, Quad>>
  recSha256: string | null
  /** 직접 입력으로 넘어온 이유. 읽었으면 null */
  manual: 'timeout' | 'skipped' | 'error' | null
}
type Step =
  | { name: 'pick' }
  | { name: 'roi'; photo: Photo }
  | { name: 'reading'; photo: Photo; roi: Roi }
  | { name: 'confirm'; photo: Photo; roi: Roi; read: ReadResult }
  | { name: 'done' }

export type RecordCertFlowProps = {
  orderItemId: number
  /** 원본 사진을 올리고 파일 ID를 돌려준다 (file-upload 기능을 화면에서 넘긴다) */
  uploadPhoto: (file: File) => Promise<number>
  /** P3에서 돌아갈 참가권 화면 */
  doneHref: string
}

export function RecordCertFlow({ orderItemId, uploadPhoto, doneHref }: RecordCertFlowProps) {
  const reader = useCertReader()
  const [step, setStep] = useState<Step>({ name: 'pick' })
  const [pickError, setPickError] = useState<string>()

  // 사진 미리보기 주소는 다른 사진을 고르거나 화면을 떠날 때 푼다
  const photoUrl = 'photo' in step ? step.photo.url : undefined
  useEffect(
    () => () => {
      if (photoUrl) URL.revokeObjectURL(photoUrl)
    },
    [photoUrl],
  )

  async function pick(file: File | undefined) {
    if (!file) return
    setPickError(undefined)
    try {
      setStep({
        name: 'roi',
        photo: { file, url: URL.createObjectURL(file), image: await decodeImage(file) },
      })
    } catch {
      setPickError('사진을 열지 못했어요. JPG · PNG 사진을 골라 주세요.')
    }
  }

  async function read(photo: Photo, roi: Roi) {
    setStep({ name: 'reading', photo, roi })
    const outcome = await reader.read({ image: photo.image, roi })
    const read: ReadResult =
      outcome.kind === 'read'
        ? { ...prefill(extractFields(outcome.lines)), recSha256: outcome.recSha256, manual: null }
        : { values: EMPTY_VALUES, boxes: {}, recSha256: null, manual: outcome.reason }
    setStep({ name: 'confirm', photo, roi, read })
  }

  if (step.name === 'pick') {
    return (
      <div className="flex flex-1 flex-col gap-4 px-4 py-6">
        <h1 className="text-title2">완주 기록을 확인할게요</h1>
        <p className="text-body2-regular text-text-secondary">
          공식 기록증, 기록 조회 화면을 캡처한 사진 모두 괜찮아요. 사진은 자르지 않고 그대로 올려요.
        </p>
        <ul className="list-disc pl-5 text-body2-regular text-text-secondary">
          <li>넷타임 · 대회일 · 종목이 한 장에 다 보이게</li>
          <li>빛 번짐 없이, 글자가 흐리지 않게</li>
        </ul>
        {pickError && <Alert tone="danger" title={pickError} />}
        <label className="mt-auto">
          <span className="sr-only">기록증 사진 고르기</span>
          <input
            type="file"
            accept="image/jpeg,image/png"
            className="block w-full text-body2-regular"
            onChange={(e) => void pick(e.target.files?.[0])}
          />
        </label>
      </div>
    )
  }

  if (step.name === 'roi') {
    return <RoiPicker photo={step.photo} onRead={(roi) => void read(step.photo, roi)} />
  }

  if (step.name === 'reading') {
    return (
      <div className="flex flex-1 flex-col items-center gap-4 px-4 py-6">
        <PhotoWithRoi photo={step.photo} roi={step.roi} />
        <Spinner />
        <p className="text-body1-bold" role="status">
          기록증을 읽고 있어요
        </p>
        <p className="text-center text-body2-regular text-text-secondary">
          10초 안에 읽지 못하면 직접 입력하는 화면으로 넘어가요.
        </p>
        <Button variant="secondary" size="m" onClick={() => reader.skip()}>
          직접 입력하기
        </Button>
      </div>
    )
  }

  if (step.name === 'confirm') {
    return (
      <ConfirmForm
        photo={step.photo}
        roi={step.roi}
        read={step.read}
        orderItemId={orderItemId}
        uploadPhoto={uploadPhoto}
        onRetry={() => setStep({ name: 'pick' })}
        onDone={() => setStep({ name: 'done' })}
      />
    )
  }

  return (
    <div className="flex flex-1 flex-col items-center gap-3 px-4 py-12 text-center">
      <h1 className="text-title2">기록증을 확인하고 있어요</h1>
      <p className="text-body2-regular text-text-secondary">
        결과는 참가권 화면에서 볼 수 있어요. 판매자 확인이 필요하면 조금 더 걸릴 수 있어요.
      </p>
      <BottomCta
        className="mt-auto w-full"
        primary={
          <a href={doneHref} className={buttonClassName({ size: 'l' })}>
            참가권으로 돌아가기
          </a>
        }
      />
    </div>
  )
}

/** 사진 위에 끌어서 읽을 부분 네모를 그린다. 그리지 않으면 사진 전체를 읽는다 */
function RoiPicker({ photo, onRead }: { photo: Photo; onRead: (roi: Roi) => void }) {
  const natural = { width: photo.image.width, height: photo.image.height }
  const [roi, setRoi] = useState<Roi>(wholeImage(natural))
  const drag = useRef<{ x: number; y: number } | null>(null)

  const point = (e: PointerEvent<HTMLDivElement>) => {
    const box = e.currentTarget.getBoundingClientRect()
    return { pos: { x: e.clientX - box.left, y: e.clientY - box.top }, shown: box }
  }

  return (
    <div className="flex flex-1 flex-col gap-4 px-4 py-6">
      <h1 className="text-title2">읽을 부분을 짚어 주세요</h1>
      <p className="text-body2-regular text-text-secondary">
        넷타임 · 대회일 · 종목이 들어가게 사진 위를 끌어 네모를 그려 주세요. 그리지 않으면 사진
        전체를 읽어요.
      </p>
      <div
        className="relative touch-none select-none"
        onPointerDown={(e) => {
          e.currentTarget.setPointerCapture(e.pointerId)
          drag.current = point(e).pos
        }}
        onPointerMove={(e) => {
          if (!drag.current) return
          const { pos, shown } = point(e)
          setRoi(toRoi(drag.current, pos, shown, natural))
        }}
        onPointerUp={() => (drag.current = null)}
      >
        <PhotoWithRoi photo={photo} roi={roi} />
      </div>
      <BottomCta
        className="mt-auto"
        secondary={
          <Button variant="secondary" size="l" onClick={() => setRoi(wholeImage(natural))}>
            사진 전체
          </Button>
        }
        primary={
          <Button size="l" onClick={() => onRead(roi)}>
            이 부분 읽기
          </Button>
        }
      />
    </div>
  )
}

function PhotoWithRoi({ photo, roi }: { photo: Photo; roi: Roi }) {
  const { width, height } = photo.image
  return (
    <div className="relative w-full overflow-hidden">
      {/* 고른 사진의 blob 주소라 next/image로 최적화할 수 없다 */}
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src={photo.url} alt="고른 기록증 사진" className="block w-full" draggable={false} />
      <div
        aria-hidden
        className="pointer-events-none absolute border-2 border-border-brand shadow-[0_0_0_9999px_rgb(0_0_0/0.35)]"
        style={{
          left: `${(roi.x / width) * 100}%`,
          top: `${(roi.y / height) * 100}%`,
          width: `${(roi.width / width) * 100}%`,
          height: `${(roi.height / height) * 100}%`,
        }}
      />
    </div>
  )
}

/** P2. 테스트를 위해 내보낸다 (슬라이스 밖에서는 RecordCertFlow만 쓴다) */
export function ConfirmForm({
  photo,
  roi,
  read,
  orderItemId,
  uploadPhoto,
  onRetry,
  onDone,
}: {
  photo: Photo
  roi: Roi
  read: ReadResult
  orderItemId: number
  uploadPhoto: (file: File) => Promise<number>
  onRetry: () => void
  onDone: () => void
}) {
  const submit = useSubmitRecordCert()
  const [uploadError, setUploadError] = useState(false)
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CertFormInput>({ resolver: zodResolver(certFormSchema), defaultValues: read.values })
  const auto = (value: string | undefined) => read.manual === null && !!value

  // 한 번 올린 사진은 제출이 실패해 다시 눌러도 다시 올리지 않는다(연결 안 된 파일이 쌓이지 않게)
  const [uploadedFileId, setUploadedFileId] = useState<number | null>(null)

  const onSubmit = handleSubmit(async (values) => {
    setUploadError(false)
    let fileId = uploadedFileId
    if (fileId === null) {
      try {
        fileId = await uploadPhoto(await stripGps(photo.file))
      } catch {
        setUploadError(true)
        return
      }
      setUploadedFileId(fileId)
    }
    try {
      await submit.mutateAsync({
        orderItemId,
        fileId,
        roi,
        netTimeSeconds: netSeconds(values.netTime) ?? 0,
        raceDate: values.raceDate,
        courseClass: values.courseClass,
        bib: values.bib.trim() || undefined,
        // 참가자가 값을 고쳐도 상자는 그대로 보낸다. 서버가 같은 자리를 다시 읽어 다르면 심사 필요가 된다
        fieldBoxes: read.boxes,
        specVersion: spec.specVersion,
        recModelSha256: read.recSha256,
      })
      onDone()
    } catch {
      // 실패 안내는 submit.isError가 그린다. 다시 누르면 같은 파일 ID로 다시 낸다
    }
  })

  return (
    <form className="flex flex-1 flex-col gap-4 px-4 py-6" onSubmit={onSubmit} noValidate>
      <h1 className="text-title2">기록증 확인</h1>
      <PhotoWithRoi photo={photo} roi={roi} />
      {read.manual === 'timeout' || read.manual === 'error' ? (
        <Alert
          tone="warning"
          title="기록증을 읽지 못했어요"
          description="사진을 보며 직접 입력해 주세요. 다른 사진으로 다시 읽어도 돼요."
        />
      ) : read.manual === null ? (
        <p className="text-caption-medium text-text-brand">
          AI가 읽은 값이에요. 틀리면 고쳐 주세요.
        </p>
      ) : null}

      <Controller
        control={control}
        name="raceDate"
        render={({ field }) => (
          <DateTimeField
            mode="single"
            withTime={false}
            label={<FieldLabel text="대회일" auto={auto(read.values.raceDate)} />}
            error={errors.raceDate?.message}
            defaultValue={field.value ? parseISO(field.value) : undefined}
            ref={field.ref}
            isDateDisabled={(date) => date > new Date()}
            onValueChange={field.onChange}
          />
        )}
      />
      <Controller
        control={control}
        name="courseClass"
        render={({ field }) => (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 text-body2-medium">
              <FieldLabel text="종목" auto={auto(read.values.courseClass)} />
            </legend>
            <div className="flex flex-wrap gap-2">
              {COURSE_CLASSES.map((course) => (
                <Chip
                  key={course}
                  selected={field.value === course}
                  onClick={() => field.onChange(course)}
                >
                  {COURSE_LABEL[course]}
                </Chip>
              ))}
            </div>
            {errors.courseClass && (
              <p className="text-caption-medium text-text-danger">{errors.courseClass.message}</p>
            )}
          </fieldset>
        )}
      />
      <TextField
        label={<FieldLabel text="넷타임" auto={auto(read.values.netTime)} />}
        placeholder="시:분:초 (예: 1:30:05)"
        inputMode="numeric"
        error={errors.netTime?.message}
        {...register('netTime')}
      />
      <TextField
        label={<FieldLabel text="배번 (선택)" auto={auto(read.values.bib)} />}
        error={errors.bib?.message}
        {...register('bib')}
      />

      {uploadError && (
        <Alert
          tone="danger"
          title="사진을 올리지 못했어요"
          description="잠시 뒤 다시 시도해 주세요."
        />
      )}
      {submit.isError && (
        <Alert
          tone="danger"
          title="제출하지 못했어요"
          description={submitErrorMessage(submit.error)}
        />
      )}

      <BottomCta
        className="mt-auto"
        secondary={
          <Button type="button" variant="secondary" size="l" onClick={onRetry}>
            다른 사진
          </Button>
        }
        primary={
          <Button type="submit" size="l" loading={isSubmitting}>
            제출하기
          </Button>
        }
      />
    </form>
  )
}

function FieldLabel({ text, auto }: { text: string; auto: boolean }) {
  return (
    <span className="inline-flex items-center gap-1">
      {text}
      {auto && <Badge tone="brand">AI</Badge>}
    </span>
  )
}

function submitErrorMessage(error: Error): string {
  const message = (error as { serverMessage?: string }).serverMessage
  return message || '잠시 뒤 다시 시도해 주세요.'
}
