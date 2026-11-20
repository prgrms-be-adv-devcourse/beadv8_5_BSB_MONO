'use client'

import { useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react'

import { ConsoleCard } from '@/widgets/console-page'
import { cn } from '@/shared/lib'
import { Button, FOCUS_RING, Icon, ImageCropDialog, validateImageFile } from '@/shared/ui'

import type { ImageChange } from '../model/race-image-change'

// 등록 화면과 같은 한도
const MAX_INTRO_IMAGES = 5
const ACCEPT = 'image/jpeg,image/png,image/webp'

// 피그마 V4 · 판매자 · 판매 중 변경 · 이미지: 사진마다 바꾸기, 소개 이미지는 더 올리기만
export function RaceImagesCard({
  cover,
  value,
  onChange,
  disabled,
}: {
  /** 지금 대표 이미지 주소 */
  cover?: string
  value: ImageChange
  onChange: Dispatch<SetStateAction<ImageChange>>
  disabled?: boolean
}) {
  const [cropOpen, setCropOpen] = useState(false)
  const [error, setError] = useState<string>()
  const input = useRef<HTMLInputElement>(null)
  // 바꿀 소개 이미지 칸. -1이면 새로 더한다
  const target = useRef(-1)
  // 이 화면이 만든 미리보기 주소는 떠날 때 해제한다
  const created = useRef<string[]>([])
  useEffect(() => {
    const urls = created.current
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [])
  const preview = (file: Blob) => {
    const url = URL.createObjectURL(file)
    created.current.push(url)
    return url
  }

  function pickIntro(index: number) {
    target.current = index
    input.current?.click()
  }

  async function onIntroFile(file: File | undefined) {
    if (!file) return
    const problem = await validateImageFile(file, { minWidth: 1080 })
    if (problem) {
      setError(`${problem}. 다른 파일을 골라 주세요`)
      return
    }
    setError(undefined)
    const slot = { url: preview(file), file }
    const index = target.current
    // 파일 검사를 기다리는 사이 바뀐 값을 덮어쓰지 않게 최신 값에 더한다
    onChange((prev) => ({
      ...prev,
      intro:
        index < 0
          ? [...prev.intro, { key: `new-${created.current.length}`, ...slot }]
          : prev.intro.map((s, i) => (i === index ? { ...s, ...slot } : s)),
    }))
  }

  return (
    <ConsoleCard title="이미지">
      <div className="flex flex-col gap-2">
        <h3 className="text-body1-bold">
          대표 이미지 <span className="text-caption-regular text-text-danger">필수</span>
        </h3>
        <ImageSlot
          label="대표 이미지"
          url={value.cover?.url ?? cover}
          disabled={disabled}
          onChange={() => setCropOpen(true)}
        />
      </div>
      <div className="flex flex-col gap-2">
        <h3 className="text-body1-bold">소개 이미지</h3>
        <div className="flex flex-wrap gap-4">
          {value.intro.map((slot, i) => (
            <ImageSlot
              key={slot.key}
              label={`소개 이미지 ${i + 1}`}
              url={slot.url}
              disabled={disabled}
              onChange={() => pickIntro(i)}
            />
          ))}
          {value.intro.length < MAX_INTRO_IMAGES && (
            <button
              type="button"
              aria-label="소개 이미지 더 올리기"
              disabled={disabled}
              onClick={() => pickIntro(-1)}
              className={cn(
                'flex size-28 flex-col items-center justify-center gap-1 rounded-md border border-dashed border-border-strong text-text-tertiary hover:bg-bg-subtle disabled:opacity-50',
                FOCUS_RING,
              )}
            >
              <Icon name="Plus" className="size-5" aria-hidden />
              <span className="text-caption-regular">
                {value.intro.length} / {MAX_INTRO_IMAGES}
              </span>
            </button>
          )}
        </div>
        <input
          ref={input}
          type="file"
          accept={ACCEPT}
          hidden
          data-testid="intro-image-input"
          onChange={(e) => {
            void onIntroFile(e.target.files?.[0])
            e.target.value = ''
          }}
        />
        {error && (
          <p role="alert" className="text-caption-regular text-text-danger">
            {error}
          </p>
        )}
      </div>
      <p className="text-caption-regular text-text-tertiary">
        사진은 바꾸거나 더 올릴 수만 있어요. 대표 이미지는 꼭 1장 있어야 해요
      </p>
      <ImageCropDialog
        open={cropOpen}
        onOpenChange={setCropOpen}
        onApply={(blob, name) => {
          onChange((prev) => ({
            ...prev,
            cover: { file: new File([blob], name, { type: blob.type }), url: preview(blob) },
          }))
          setCropOpen(false)
        }}
      />
    </ConsoleCard>
  )
}

function ImageSlot({
  label,
  url,
  disabled,
  onChange,
}: {
  label: string
  url?: string
  disabled?: boolean
  onChange: () => void
}) {
  return (
    <div className="flex w-28 flex-col items-center gap-2">
      <div className="flex size-28 items-center justify-center overflow-hidden rounded-md bg-bg-subtle">
        {url ? (
          // 미리보기는 blob 주소, 저장된 사진은 만료되는 S3 주소라 next/image로 최적화하지 않는다
          // eslint-disable-next-line @next/next/no-img-element
          <img src={url} alt={label} className="size-full object-cover" />
        ) : (
          <Icon name="Image" className="size-6 text-text-tertiary" aria-hidden />
        )}
      </div>
      <Button
        type="button"
        variant="secondary"
        size="s"
        aria-label={`${label} 바꾸기`}
        disabled={disabled}
        onClick={onChange}
      >
        바꾸기
      </Button>
    </div>
  )
}
