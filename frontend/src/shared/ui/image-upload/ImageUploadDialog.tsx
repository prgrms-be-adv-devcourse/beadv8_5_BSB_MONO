'use client'

import { Dialog } from '@base-ui/react/dialog'
import { Slider } from '@base-ui/react/slider'
import {
  useEffect,
  useEffectEvent,
  useRef,
  useState,
  type KeyboardEvent,
  type PointerEvent,
  type ReactNode,
} from 'react'

import { cn } from '@/shared/lib'

import { Button } from '../button/Button'
import { IconButton } from '../button/IconButton'
import { FOCUS_RING } from '../focus-ring'
import { Icon } from '../icon/icons'
import {
  clampOffset,
  coverScale,
  cropRect,
  cropToSquare,
  IMAGE_ACCEPT,
  validateImageFile,
  zoomOffset,
  type CropOffset,
  type ImageRules,
} from './image-file'
import { useFileDrop } from './use-file-drop'
import type { ImageItem } from './use-image-list'

const FRAME = 260
const ZOOM_MAX = 3

type ShellProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: ReactNode
  description: ReactNode
  children: ReactNode
}

// 폭 640(모바일은 화면 폭 - 여백). 모양은 Dialog와 같고, 내용이 길어 닫기 버튼을 둔다.
function Shell({ open, onOpenChange, title, description, children }: ShellProps) {
  // 창이 열린 동안 창 밖에 파일을 놓쳐도 브라우저가 그 파일을 열지 않게 막는다.
  useEffect(() => {
    if (!open) return
    const block = (event: DragEvent) => event.preventDefault()
    window.addEventListener('dragover', block)
    window.addEventListener('drop', block)
    return () => {
      window.removeEventListener('dragover', block)
      window.removeEventListener('drop', block)
    }
  }, [open])

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-navy-950/50 transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0" />
        <Dialog.Popup className="fixed top-1/2 left-1/2 z-50 flex max-h-[calc(100dvh-32px)] w-160 max-w-[calc(100vw-var(--layout-page-margin)*2)] -translate-1/2 flex-col gap-5 overflow-y-auto rounded-lg bg-bg-surface p-6 shadow-lg transition-opacity duration-150 outline-none data-ending-style:opacity-0 data-starting-style:opacity-0">
          <div className="flex items-start gap-4">
            <div className="flex flex-1 flex-col gap-1">
              <Dialog.Title className="text-heading-m text-text-primary">{title}</Dialog.Title>
              <Dialog.Description className="text-body-m text-text-secondary">
                {description}
              </Dialog.Description>
            </div>
            <Dialog.Close render={<IconButton size="s" aria-label="닫기" />}>
              <Icon name="X" />
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

type DropzoneProps = {
  multiple?: boolean
  layout: 'large' | 'row'
  sub: string
  error?: string | null
  onFiles: (files: File[]) => void
}

function Dropzone({ multiple = false, layout, sub, error, onFiles }: DropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null)
  const { over, dropProps } = useFileDrop(onFiles)
  const pick = (
    <Button size="s" variant="secondary" onClick={() => inputRef.current?.click()}>
      파일 선택
    </Button>
  )
  const text = (
    <>
      <p className={cn('text-label-m', over ? 'text-text-brand' : 'text-text-primary')}>
        {over ? '여기에 놓으면 올라가요' : '이미지를 여기로 끌어다 놓으세요'}
      </p>
      <p className="text-caption text-text-tertiary">{sub}</p>
    </>
  )
  return (
    <div className="flex flex-col gap-2">
      <div
        {...dropProps}
        className={cn(
          'flex rounded-md',
          over
            ? 'border-2 border-dashed border-border-brand bg-bg-brand-subtle'
            : error
              ? 'border-2 border-dashed border-border-danger bg-bg-subtle'
              : 'border border-dashed border-border-strong bg-bg-subtle',
          layout === 'large'
            ? cn('flex-col items-center gap-2 text-center', over || error ? 'py-[47px]' : 'py-12')
            : cn('items-center gap-3', over || error ? 'px-[19px] py-[15px]' : 'px-5 py-4'),
        )}
      >
        {layout === 'large' ? (
          <>
            <span className="mb-1 rounded-full bg-bg-surface p-3 text-text-secondary">
              <Icon name="Upload" />
            </span>
            {text}
            <span className="mt-2">{pick}</span>
          </>
        ) : (
          <>
            <Icon name="Upload" className="text-text-secondary" />
            <div className="flex flex-1 flex-col gap-0.5">{text}</div>
            {pick}
          </>
        )}
        <input
          ref={inputRef}
          type="file"
          accept={IMAGE_ACCEPT.join(',')}
          multiple={multiple}
          hidden
          onChange={(event) => {
            const files = Array.from(event.target.files ?? [])
            event.target.value = ''
            if (files.length) onFiles(files)
          }}
        />
      </div>
      {error && <p className="text-caption text-text-danger">{error}</p>}
    </div>
  )
}

// ---------- 대표 이미지: Select → Crop

export type ImageCropDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 칸에 끌어다 놓은 파일. 있으면 바로 자르기부터 연다. */
  file?: File | null
  rules?: ImageRules
  /** 저장 크기(px). 정책 권장 1125 */
  outputSize?: number
  /** 자른 이미지. 서버에 올리는 일은 부모가 한다. */
  onApply: (blob: Blob, fileName: string) => void | Promise<void>
}

export function ImageCropDialog({
  open,
  onOpenChange,
  file: initialFile = null,
  rules,
  outputSize = 1125,
  onApply,
}: ImageCropDialogProps) {
  const [file, setFile] = useState<File | null>(initialFile)
  const [error, setError] = useState<string | null>(null)
  const [prevOpen, setPrevOpen] = useState(open)
  // 창을 새로 열 때마다 처음 상태로 돌린다.
  if (open !== prevOpen) {
    setPrevOpen(open)
    if (open) {
      setFile(initialFile)
      setError(null)
    }
  }

  async function choose(files: File[]) {
    const next = files[0]
    if (!next) return
    const problem = await validateImageFile(next, rules)
    if (problem) {
      setError(`${problem}. 다른 파일을 골라 주세요`)
      return
    }
    setError(null)
    setFile(next)
  }

  if (!file) {
    return (
      <Shell
        open={open}
        onOpenChange={onOpenChange}
        title="대표 이미지 올리기"
        description="정사각형(1:1)으로 잘라서 대회 목록과 상세 맨 위에 보여 줘요"
      >
        <Dropzone
          layout="large"
          sub={`JPG · PNG · WebP, 10MB 이하 · ${outputSize}×${outputSize}px 권장`}
          error={error}
          onFiles={choose}
        />
      </Shell>
    )
  }

  return (
    <Shell
      open={open}
      onOpenChange={onOpenChange}
      title="대표 이미지 자르기"
      description="끌어서 위치를 옮기고, 아래 막대로 크기를 맞춰 주세요"
    >
      <Cropper
        key={`${file.name}-${file.lastModified}`}
        file={file}
        outputSize={outputSize}
        onPickAnother={() => setFile(null)}
        onCancel={() => onOpenChange(false)}
        onApply={onApply}
      />
    </Shell>
  )
}

type CropperProps = {
  file: File
  outputSize: number
  onPickAnother: () => void
  onCancel: () => void
  onApply: (blob: Blob, fileName: string) => void | Promise<void>
}

function Cropper({ file, outputSize, onPickAnother, onCancel, onApply }: CropperProps) {
  const imgRef = useRef<HTMLImageElement>(null)
  const [src, setSrc] = useState<string | null>(null)
  const [natural, setNatural] = useState<{ width: number; height: number } | null>(null)
  const [zoom, setZoom] = useState(1)
  const [offset, setOffset] = useState<CropOffset>({ x: 0, y: 0 })
  const [applying, setApplying] = useState(false)
  const drag = useRef<{ x: number; y: number; from: CropOffset } | null>(null)

  // data URL로 읽어 두면 Strict Mode에서 정리 함수가 먼저 돌아도 주소가 끊기지 않는다.
  useEffect(() => {
    const reader = new FileReader()
    reader.onload = () => setSrc(reader.result as string)
    reader.readAsDataURL(file)
    return () => reader.abort()
  }, [file])

  const scale = natural ? coverScale(natural, FRAME, zoom) : 1

  function place(next: CropOffset, nextScale = scale) {
    if (natural) setOffset(clampOffset(next, natural, FRAME, nextScale))
  }

  function onLoad() {
    const img = imgRef.current
    if (!img) return
    const size = { width: img.naturalWidth, height: img.naturalHeight }
    const s = coverScale(size, FRAME, 1)
    setNatural(size)
    // 처음에는 가운데를 맞춘다.
    setOffset({ x: (FRAME - size.width * s) / 2, y: (FRAME - size.height * s) / 2 })
  }

  function onZoom(next: number) {
    if (!natural) return
    const nextScale = coverScale(natural, FRAME, next)
    setZoom(next)
    place(zoomOffset(offset, FRAME, scale, nextScale), nextScale)
  }

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { x: event.clientX, y: event.clientY, from: offset }
  }
  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    const start = drag.current
    if (!start) return
    place({ x: start.from.x + event.clientX - start.x, y: start.from.y + event.clientY - start.y })
  }
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.shiftKey ? 40 : 10
    const move: Record<string, CropOffset> = {
      ArrowLeft: { x: step, y: 0 },
      ArrowRight: { x: -step, y: 0 },
      ArrowUp: { x: 0, y: step },
      ArrowDown: { x: 0, y: -step },
    }
    const d = move[event.key]
    if (!d) return
    event.preventDefault()
    place({ x: offset.x + d.x, y: offset.y + d.y })
  }

  async function apply() {
    const img = imgRef.current
    if (!img || !natural) return
    setApplying(true)
    try {
      const blob = await cropToSquare(img, cropRect(offset, FRAME, scale), outputSize, file.type)
      await onApply(blob, file.name)
    } finally {
      setApplying(false)
    }
  }

  return (
    <>
      <div
        role="group"
        aria-label="자를 위치. 끌거나 방향키로 옮겨요"
        tabIndex={0}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={() => (drag.current = null)}
        onPointerCancel={() => (drag.current = null)}
        onKeyDown={onKeyDown}
        className={cn(
          'relative flex h-80 cursor-grab touch-none items-center justify-center overflow-hidden rounded-md bg-navy-950 select-none active:cursor-grabbing',
          FOCUS_RING,
        )}
      >
        {/* 이미지는 틀 왼쪽 위 기준으로 놓고, 그 위에 틀을 겹쳐 바깥을 큰 그림자로 어둡게 덮는다. */}
        <div className="absolute size-65">
          {src && (
            // eslint-disable-next-line @next/next/no-img-element -- 브라우저 임시 주소(blob:)를 그린다
            <img
              ref={imgRef}
              src={src}
              alt=""
              draggable={false}
              onLoad={onLoad}
              className="pointer-events-none absolute top-0 left-0 max-w-none"
              style={
                natural
                  ? {
                      width: natural.width * scale,
                      height: natural.height * scale,
                      transform: `translate(${offset.x}px, ${offset.y}px)`,
                    }
                  : { opacity: 0 }
              }
            />
          )}
        </div>
        <div className="pointer-events-none absolute size-65 shadow-[0_0_0_9999px_color-mix(in_srgb,var(--color-navy-950)_60%,transparent)] outline-2 outline-bg-surface" />
      </div>

      <div className="flex items-center gap-3 text-text-secondary">
        <Icon name="Minus" className="size-5" />
        <Slider.Root
          value={zoom}
          min={1}
          max={ZOOM_MAX}
          step={0.01}
          onValueChange={(value) => onZoom(Array.isArray(value) ? value[0] : value)}
          className="flex-1"
        >
          <Slider.Control className="flex h-5 items-center">
            <Slider.Track className="h-1 w-full rounded-full bg-bg-track">
              <Slider.Indicator className="rounded-full bg-bg-brand" />
              <Slider.Thumb
                aria-label="확대"
                className={cn(
                  'size-4 rounded-full bg-bg-surface ring-2 ring-border-brand',
                  FOCUS_RING,
                )}
              />
            </Slider.Track>
          </Slider.Control>
        </Slider.Root>
        <Icon name="Plus" className="size-5" />
      </div>

      <div className="flex items-center gap-3 rounded-md bg-bg-subtle p-3">
        <div className="relative size-14 shrink-0 overflow-hidden rounded-sm">
          {src && natural && (
            // eslint-disable-next-line @next/next/no-img-element -- 미리보기도 같은 임시 주소를 쓴다
            <img
              src={src}
              alt=""
              className="absolute top-0 left-0 max-w-none origin-top-left"
              style={{
                width: natural.width * scale,
                height: natural.height * scale,
                transform: `scale(${56 / FRAME}) translate(${offset.x}px, ${offset.y}px)`,
              }}
            />
          )}
        </div>
        <div className="flex min-w-0 flex-col gap-0.5">
          <p className="text-label-m text-text-primary">목록에서는 이렇게 보여요</p>
          <p className="truncate text-caption text-text-tertiary">
            {file.name} · {outputSize}×{outputSize}로 저장
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button variant="ghost" onClick={onPickAnother}>
          다른 이미지 고르기
        </Button>
        <span className="flex-1" />
        <Button variant="secondary" onClick={onCancel}>
          취소
        </Button>
        <Button loading={applying} onClick={apply}>
          적용하기
        </Button>
      </div>
    </>
  )
}

// ---------- 소개 이미지: Manage

export type ImageListDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** useImageList의 items */
  items: ImageItem[]
  onFilesAdd: (files: File[]) => void
  onRemove: (id: string) => void
  onMove: (from: number, to: number) => void
  /** 올리는 중인 이미지가 있으면 다 끝난 뒤에 부른다. 실패한 이미지는 부모가 빼고 저장한다. */
  onSave: () => void
}

export function ImageListDialog({
  open,
  onOpenChange,
  items,
  onFilesAdd,
  onRemove,
  onMove,
  onSave,
}: ImageListDialogProps) {
  const [waiting, setWaiting] = useState(false)
  const [saveCount, setSaveCount] = useState(0)
  const dragFrom = useRef<number | null>(null)
  const uploading = items.some((item) => item.status === 'uploading')
  const done = items.filter((item) => item.status === 'done').length
  const failed = items.filter((item) => item.status === 'error').length
  // 순서 번호는 저장될 이미지(실패 제외)끼리 센다.
  const order = new Map(
    items.filter((item) => item.status !== 'error').map((item, i) => [item.id, i + 1]),
  )

  // 저장하기를 누른 뒤 올리기가 끝나면 그때 저장한다.
  if (waiting && !uploading) {
    setWaiting(false)
    setSaveCount((count) => count + 1)
  }
  const save = useEffectEvent(onSave)
  useEffect(() => {
    if (saveCount > 0) save()
  }, [saveCount])

  return (
    <Shell
      open={open}
      onOpenChange={onOpenChange}
      title="소개 이미지 올리기"
      description="끌어서 순서를 바꿀 수 있어요. 1번부터 차례로 보여요"
    >
      <Dropzone
        multiple
        layout="row"
        sub="JPG · PNG · WebP, 10MB 이하 · 가로 1080px 이상"
        onFiles={onFilesAdd}
      />

      {items.length > 0 && (
        <ol className="grid grid-cols-2 gap-3 tablet:grid-cols-4">
          {items.map((item, index) => (
            <li
              key={item.id}
              draggable={item.status !== 'uploading'}
              onDragStart={(event) => {
                dragFrom.current = index
                event.dataTransfer.effectAllowed = 'move'
              }}
              onDragOver={(event) => {
                if (dragFrom.current !== null) event.preventDefault()
              }}
              onDrop={(event) => {
                if (dragFrom.current === null) return
                event.preventDefault()
                onMove(dragFrom.current, index)
                dragFrom.current = null
              }}
              onDragEnd={() => (dragFrom.current = null)}
              className="relative aspect-[4/3] overflow-hidden rounded-sm"
            >
              {item.status === 'error' ? (
                <div className="flex h-full flex-col items-center justify-center gap-1 rounded-sm bg-bg-danger-subtle px-2 text-center inset-ring inset-ring-border-danger">
                  <Icon name="CircleAlert" className="size-5 text-text-danger" />
                  <p className="text-caption text-text-danger">{item.error}</p>
                  <p className="w-full truncate text-caption text-text-tertiary">{item.name}</p>
                </div>
              ) : (
                <>
                  {/* eslint-disable-next-line @next/next/no-img-element -- 올리기 전 임시 주소도 보여 준다 */}
                  <img
                    src={item.url ?? item.previewUrl}
                    alt={`${order.get(item.id)}번째 소개 이미지`}
                    className="size-full cursor-grab object-cover"
                  />
                  {item.status === 'done' && (
                    <span className="absolute top-1.5 left-1.5 rounded-full bg-bg-inverse px-1.5 py-0.5 text-label-tag text-text-inverse">
                      {order.get(item.id)}
                    </span>
                  )}
                  {item.status === 'uploading' && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center gap-1.5 bg-navy-950/55 px-4">
                      <p className="text-caption text-text-inverse">
                        올리는 중 {Math.round(item.progress * 100)}%
                      </p>
                      <div className="h-1 w-full overflow-hidden rounded-full bg-bg-surface/30">
                        <div
                          className="h-full rounded-full bg-bg-surface transition-[width]"
                          style={{ width: `${item.progress * 100}%` }}
                        />
                      </div>
                    </div>
                  )}
                </>
              )}
              <button
                type="button"
                aria-label={`${item.name} 지우기`}
                onClick={() => onRemove(item.id)}
                className={cn(
                  'absolute top-1.5 right-1.5 rounded-full bg-bg-inverse/85 p-1 text-text-inverse',
                  FOCUS_RING,
                )}
              >
                <Icon name="X" className="size-3.5" />
              </button>
            </li>
          ))}
        </ol>
      )}

      <div className="flex items-center gap-2">
        <p className="text-caption text-text-secondary" aria-live="polite">
          {done}장{failed > 0 && ` · ${failed}장은 올리지 못했어요`}
        </p>
        <span className="flex-1" />
        <Button variant="secondary" onClick={() => onOpenChange(false)}>
          취소
        </Button>
        <Button loading={waiting} onClick={() => (uploading ? setWaiting(true) : onSave())}>
          저장하기
        </Button>
      </div>
    </Shell>
  )
}
