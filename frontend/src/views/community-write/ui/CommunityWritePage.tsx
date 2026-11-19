'use client'

import { zodResolver } from '@hookform/resolvers/zod'
import type { Route } from 'next'
import Image from 'next/image'
import { useRouter } from 'next/navigation'
import { useEffect, useId, useRef, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'

import { AppHeader } from '@/widgets/app-header'
import { loginHref, useAuthStatus } from '@/features/auth'
import { uploadFile, type FileType } from '@/features/file-upload'
import {
  BOARD_CATEGORIES,
  CONTENT_MAX,
  PHOTO_MAX,
  postWriteSchema,
  TITLE_MAX,
  useCreatePost,
  usePost,
  usePostPhotos,
  useUpdatePost,
  type BoardCategory,
  type PostWriteValues,
} from '@/entities/board'
import { ApiError } from '@/shared/api'
import { cn, useGoBack } from '@/shared/lib'
import {
  BottomCta,
  Button,
  Chip,
  EmptyState,
  FOCUS_RING,
  Icon,
  Skeleton,
  Textarea,
  TextField,
  useToast,
} from '@/shared/ui'

// 게시판 사진 용도. 생성된 파일 API 명세(openapi/file.json)에 아직 없는 값이라 형 변환한다 (공용 변경 필요).
const POST_IMAGE = 'POST_IMAGE' as unknown as FileType

type Photo = { key: string; previewUrl: string; fileId?: number; uploading?: boolean }

const errorMessage = (e: unknown, fallback: string) =>
  e instanceof ApiError && e.serverMessage ? e.serverMessage : fallback

// 피그마 M36 글쓰기 · M36-1 사진 첨부 · M36-2 글 수정. postId가 있으면 고치기다.
export function CommunityWritePage({ postId }: { postId?: number }) {
  const router = useRouter()
  const auth = useAuthStatus()
  const editing = postId !== undefined

  useEffect(() => {
    if (auth === 'anonymous') {
      router.replace(loginHref(editing ? `/community/${postId}/edit` : '/community/write'))
    }
  }, [auth, editing, postId, router])

  if (auth !== 'authenticated')
    return (
      <AppHeader
        type="sub"
        close
        titleAlign="center"
        title={editing ? '글 수정' : '글쓰기'}
        backHref="/community"
      />
    )
  return editing ? <EditLoader postId={postId} /> : <WriteForm />
}

function EditLoader({ postId }: { postId: number }) {
  const post = usePost(postId)
  const photos = usePostPhotos(postId)
  const back = `/community/${postId}` as Route

  if (post.isPending || photos.isPending) {
    return (
      <>
        <AppHeader type="sub" close titleAlign="center" title="글 수정" backHref={back} />
        <main role="status" aria-label="글 불러오는 중" className="flex flex-col gap-3 p-4">
          <Skeleton className="w-16" />
          <Skeleton className="h-11 w-full" />
          <Skeleton className="h-30 w-full" />
        </main>
      </>
    )
  }
  // 내 글이 아니거나 신고로 숨겨진 글은 고칠 수 없다
  if (post.isError || !post.data.mine || post.data.status === 'HIDDEN') {
    return (
      <>
        <AppHeader type="sub" close titleAlign="center" title="글 수정" backHref="/community" />
        <main className="flex flex-1 items-center justify-center px-4 pb-16">
          <EmptyState role="alert" icon="CircleAlert" title="고칠 수 없는 글이에요" />
        </main>
      </>
    )
  }
  return (
    <WriteForm
      postId={postId}
      initial={{
        category: post.data.category,
        title: post.data.title,
        content: post.data.content,
        photos: (photos.data ?? []).map((p) => ({
          key: `f${p.fileId}`,
          previewUrl: p.url,
          fileId: p.fileId,
        })),
      }}
    />
  )
}

function WriteForm({
  postId,
  initial,
}: {
  postId?: number
  initial?: { category: BoardCategory; title: string; content: string; photos: Photo[] }
}) {
  const router = useRouter()
  const goBack = useGoBack()
  const toast = useToast()
  const create = useCreatePost()
  const update = useUpdatePost(postId ?? 0)
  const { control, register, handleSubmit } = useForm<PostWriteValues>({
    resolver: zodResolver(postWriteSchema),
    defaultValues: {
      category: initial?.category ?? 'FREE',
      title: initial?.title ?? '',
      content: initial?.content ?? '',
    },
  })
  const titleCountId = useId()
  const contentCountId = useId()
  const title = useWatch({ control, name: 'title' })
  const content = useWatch({ control, name: 'content' })
  const [photos, setPhotos] = useState<Photo[]>(initial?.photos ?? [])
  const fileInput = useRef<HTMLInputElement>(null)
  // 화면을 떠나면 올리던 사진을 멈춘다
  const uploads = useRef<AbortController>(null)
  useEffect(() => {
    const controller = new AbortController()
    uploads.current = controller
    return () => controller.abort()
  }, [])
  const editing = postId !== undefined
  const uploading = photos.some((p) => p.uploading)
  const pending = create.isPending || update.isPending
  const canSubmit = title.trim() !== '' && content.trim() !== '' && !uploading && !pending

  async function addFiles(files: File[]) {
    const room = PHOTO_MAX - photos.length
    const added = files.slice(0, room).map((file) => ({
      file,
      photo: {
        key: `${Date.now()}-${file.name}`,
        previewUrl: URL.createObjectURL(file),
        uploading: true,
      },
    }))
    if (added.length === 0) return
    setPhotos((prev) => [...prev, ...added.map((a) => a.photo)])
    await Promise.all(
      added.map(async ({ file, photo }) => {
        try {
          const { fileId } = await uploadFile(file, POST_IMAGE, {
            signal: uploads.current?.signal,
          })
          setPhotos((prev) =>
            prev.map((p) => (p.key === photo.key ? { ...p, fileId, uploading: false } : p)),
          )
        } catch (e) {
          if (e instanceof DOMException && e.name === 'AbortError') return
          setPhotos((prev) => prev.filter((p) => p.key !== photo.key))
          toast({ message: errorMessage(e, '사진을 올리지 못했어요'), tone: 'error' })
        }
      }),
    )
  }

  function submit(values: PostWriteValues) {
    const data = {
      ...values,
      fileIds: photos.flatMap((p) => (p.fileId === undefined ? [] : [p.fileId])),
    }
    const onError = (e: unknown) =>
      toast({ message: errorMessage(e, '글을 올리지 못했어요'), tone: 'error' })
    if (editing) {
      update.mutate(data, { onSuccess: () => goBack(`/community/${postId}`), onError })
    } else {
      create.mutate(data, {
        onSuccess: ({ postId: id }) => router.replace(`/community/${id}`),
        onError,
      })
    }
  }

  return (
    <form
      className="flex flex-1 flex-col"
      onSubmit={(e) => {
        void handleSubmit((values) => {
          if (canSubmit) submit(values)
        })(e)
      }}
    >
      <AppHeader
        type="sub"
        close
        titleAlign="center"
        title={editing ? '글 수정' : '글쓰기'}
        backHref={editing ? (`/community/${postId}` as Route) : '/community'}
      />
      <div className="flex flex-1 flex-col gap-7 px-4 pt-4 pb-6">
        <fieldset className="flex flex-col gap-2">
          <legend className="mb-3 text-body1-medium">말머리</legend>
          <Controller
            control={control}
            name="category"
            render={({ field }) => (
              <div className="flex flex-wrap gap-2">
                {BOARD_CATEGORIES.map((c) => (
                  <Chip
                    key={c.value}
                    selected={field.value === c.value}
                    onClick={() => field.onChange(c.value)}
                  >
                    {c.label}
                  </Chip>
                ))}
              </div>
            )}
          />
        </fieldset>
        <div className="flex flex-col gap-2">
          <TextField
            label="제목"
            placeholder="제목"
            maxLength={TITLE_MAX}
            aria-describedby={titleCountId}
            {...register('title')}
          />
          <p id={titleCountId} className="text-right text-caption-regular text-text-secondary">
            {title.length}/{TITLE_MAX}
          </p>
        </div>
        <div className="flex flex-col gap-2">
          <Textarea
            label="본문"
            placeholder="내용"
            maxLength={CONTENT_MAX}
            aria-describedby={contentCountId}
            // 고치기에서 글자 수 표시가 처음부터 맞도록 초기값을 알려 준다
            defaultValue={initial?.content}
            className="[&_textarea]:h-30"
            {...register('content')}
          />
          <p id={contentCountId} className="text-caption-regular text-text-secondary">
            {content.length.toLocaleString('en-US')}/{CONTENT_MAX.toLocaleString('en-US')}
          </p>
        </div>
        <section aria-label="사진" className="flex flex-col gap-2">
          <h2 className="text-body1-medium">사진</h2>
          <ul className="flex flex-wrap gap-2">
            <li>
              <button
                type="button"
                disabled={photos.length >= PHOTO_MAX}
                onClick={() => fileInput.current?.click()}
                className={cn(
                  'flex size-22 flex-col items-center justify-center gap-1 rounded-md bg-bg-surface text-caption-regular text-text-secondary inset-ring inset-ring-border-default disabled:opacity-50',
                  FOCUS_RING,
                )}
              >
                <Icon name="Image" className="size-6" />
                <span aria-label={`사진 추가, ${photos.length}장 / 최대 ${PHOTO_MAX}장`}>
                  {photos.length}/{PHOTO_MAX}
                </span>
              </button>
              <input
                ref={fileInput}
                type="file"
                accept="image/*"
                multiple
                hidden
                data-testid="photo-input"
                onChange={(e) => {
                  void addFiles(Array.from(e.target.files ?? []))
                  e.target.value = ''
                }}
              />
            </li>
            {photos.map((photo) => (
              <li key={photo.key} className="relative size-22 overflow-hidden rounded-md">
                <Image
                  src={photo.previewUrl}
                  alt=""
                  fill
                  sizes="88px"
                  unoptimized
                  className={cn('object-cover', photo.uploading && 'opacity-50')}
                />
                <button
                  type="button"
                  aria-label="사진 지우기"
                  onClick={() => setPhotos((prev) => prev.filter((p) => p.key !== photo.key))}
                  className={cn(
                    'absolute top-1 right-1 flex size-5 items-center justify-center rounded-full bg-bg-overlay text-text-inverse',
                    FOCUS_RING,
                  )}
                >
                  <Icon name="X" className="size-3" />
                </button>
              </li>
            ))}
          </ul>
        </section>
      </div>
      <BottomCta
        className="sticky bottom-0"
        primary={
          <Button type="submit" size="l" disabled={!canSubmit} loading={pending}>
            {editing ? '수정 완료' : '등록'}
          </Button>
        }
      />
    </form>
  )
}
