'use client'

import { useId, type ReactNode } from 'react'

import { cn } from '@/shared/lib'

import { Button } from '../button/Button'
import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'
import { useFileDrop } from './use-file-drop'

export type ImageUploadValue = {
  id: string
  url: string
  name: string
  /** 파일 이름 아래 작은 글씨 (예: 1125×1125 · 1.2MB) */
  meta?: string
}

export type ImageUploadProps = {
  /** single: 대표 이미지 1장(1:1), multiple: 소개 이미지 여러 장(4:3 미리보기) */
  type?: 'single' | 'multiple'
  label: ReactNode
  helper?: ReactNode
  /** 있으면 오류 상태가 되고 helper 자리에 이 문구를 보여 준다. */
  error?: ReactNode
  /** 지금 올라가 있는 이미지 */
  value?: ImageUploadValue[]
  /** 칸을 누르면 ImageUploadDialog를 연다. */
  onOpen: () => void
  /** 칸에 파일을 끌어다 놓았을 때. 보통 이 파일을 담아 ImageUploadDialog를 연다. */
  onFilesDrop: (files: File[]) => void
  /** 대표 이미지의 삭제 버튼 */
  onRemove?: (id: string) => void
  className?: string
}

const BOX = 'flex w-full items-center gap-4 rounded-md text-left'
// Empty 1px 점선, Dragover·Error 2px. 테두리가 두꺼워져도 칸 크기가 그대로이도록 안쪽 여백을 1px 줄인다.
const BOX_STATE = {
  empty: 'border border-dashed border-border-strong bg-bg-surface p-4 hover:bg-bg-subtle',
  over: 'border-2 border-dashed border-border-brand bg-bg-brand-subtle p-[15px]',
  error: 'border-2 border-dashed border-border-danger bg-bg-surface p-[15px]',
  filled: 'border border-border-strong bg-bg-surface p-4',
}

// 폼 안에서 이미지를 올리는 칸. TextField 모양으로 두면 글자 입력칸으로 오해해서 미리보기 타일로 만든다.
// 칸 전체가 파일을 놓는 곳이다.
export function ImageUpload({
  type = 'single',
  label,
  helper,
  error,
  value = [],
  onOpen,
  onFilesDrop,
  onRemove,
  className,
}: ImageUploadProps) {
  const labelId = useId()
  const helperId = useId()
  const { over, dropProps } = useFileDrop(onFilesDrop)
  const description = error ?? helper
  const filled = value.length > 0
  const state = over ? 'over' : error ? 'error' : filled ? 'filled' : 'empty'
  const tile = type === 'single' ? 'size-24' : 'h-[90px] w-30'
  const sub =
    type === 'single'
      ? 'JPG · PNG · WebP, 10MB 이하'
      : '가로 1080px 이상 · 여러 장을 한 번에 올릴 수 있어요'

  let body: ReactNode
  if (!filled || over) {
    body = (
      <button
        type="button"
        onClick={onOpen}
        aria-labelledby={labelId}
        aria-describedby={description ? helperId : undefined}
        className={cn(BOX, BOX_STATE[state], FOCUS_RING, 'cursor-pointer')}
      >
        <span
          className={cn(
            'flex shrink-0 items-center justify-center rounded-sm',
            tile,
            over ? 'bg-bg-surface text-text-brand' : 'bg-bg-subtle text-text-tertiary',
          )}
        >
          <Icon name="Image" className="size-7" />
        </span>
        <span className="flex flex-col gap-1">
          <span className={cn('text-label-m', over ? 'text-text-brand' : 'text-text-primary')}>
            {over ? '여기에 놓으면 올라가요' : '끌어다 놓거나 눌러서 올리기'}
          </span>
          <span className="text-caption text-text-tertiary">{sub}</span>
          {!over && (
            // 칸 전체가 버튼이라 안에 버튼을 또 두지 않는다. 모양만 보조 버튼이다.
            <span className="mt-2 inline-flex w-fit items-center rounded-md bg-bg-surface px-3 py-2 text-label-s text-text-primary inset-ring inset-ring-border-strong">
              이미지 올리기
            </span>
          )}
        </span>
      </button>
    )
  } else if (type === 'single') {
    const image = value[0]
    body = (
      <div className={cn(BOX, BOX_STATE[state])}>
        {/* eslint-disable-next-line @next/next/no-img-element -- 업로드 전 blob 주소도 보여 줘야 한다 */}
        <img src={image.url} alt="" className="size-24 shrink-0 rounded-sm object-cover" />
        <div className="flex min-w-0 flex-col gap-1">
          <p className="truncate text-label-m text-text-primary">{image.name}</p>
          {image.meta && <p className="text-caption text-text-tertiary">{image.meta}</p>}
          <div className="mt-2 flex gap-2">
            <Button size="s" variant="secondary" onClick={onOpen}>
              바꾸기
            </Button>
            {onRemove && (
              <Button size="s" variant="ghost" onClick={() => onRemove(image.id)}>
                삭제
              </Button>
            )}
          </div>
        </div>
      </div>
    )
  } else {
    body = (
      <div className={cn(BOX, BOX_STATE[state], 'flex-wrap gap-3')}>
        <ol className="contents">
          {value.map((image, index) => (
            <li key={image.id} className="relative">
              {/* eslint-disable-next-line @next/next/no-img-element -- 업로드 전 blob 주소도 보여 줘야 한다 */}
              <img
                src={image.url}
                alt={`${index + 1}번째 소개 이미지`}
                className="h-[90px] w-30 rounded-sm object-cover"
              />
              <span className="absolute top-1.5 left-1.5 rounded-full bg-bg-inverse px-1.5 py-0.5 text-label-tag text-text-inverse">
                {index + 1}
              </span>
            </li>
          ))}
        </ol>
        <button
          type="button"
          onClick={onOpen}
          aria-describedby={description ? helperId : undefined}
          className={cn(
            'flex h-[90px] w-30 flex-col items-center justify-center gap-1 rounded-sm border border-dashed border-border-strong bg-bg-surface text-text-secondary hover:bg-bg-subtle',
            FOCUS_RING,
          )}
        >
          <Icon name="Plus" className="size-5" />
          <span className="text-caption">추가 · 편집</span>
        </button>
      </div>
    )
  }

  return (
    <div className={cn('flex w-full flex-col gap-2', className)}>
      <span id={labelId} className="text-label-m text-text-primary">
        {label}
      </span>
      <div {...dropProps}>{body}</div>
      {description && (
        <p
          id={helperId}
          className={cn('text-caption', error ? 'text-text-danger' : 'text-text-tertiary')}
        >
          {description}
        </p>
      )}
    </div>
  )
}
