'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

import { validateImageFile, type ImageRules } from './image-file'

export type ImageItem = {
  id: string
  name: string
  /** 화면에 보여 줄 주소. 올리기 전에는 브라우저 임시 주소(blob:) */
  previewUrl: string
  status: 'uploading' | 'done' | 'error'
  /** 0~1 */
  progress: number
  error?: string
  /** 서버에 올라간 뒤 받은 주소 */
  url?: string
}

export type UploadImage = (file: File, onProgress: (ratio: number) => void) => Promise<string>

let seq = 0
const nextId = () => `img-${Date.now()}-${(seq += 1)}`

// 소개 이미지처럼 여러 장을 올리는 목록. 검사 → 올리기 → 순서 바꾸기 · 지우기를 맡는다.
// 실제 업로드 방식(서버 직접 / S3 바로)은 upload 함수로 받는다.
export function useImageList({
  upload,
  rules,
  initial = [],
}: {
  upload: UploadImage
  rules?: ImageRules
  initial?: ImageItem[]
}) {
  const [items, setItems] = useState<ImageItem[]>(initial)
  const blobUrls = useRef(new Set<string>())

  const patch = useCallback((id: string, next: Partial<ImageItem>) => {
    setItems((prev) => prev.map((item) => (item.id === id ? { ...item, ...next } : item)))
  }, [])

  const add = useCallback(
    async (files: File[]) => {
      const added = files.map((file) => {
        const previewUrl = URL.createObjectURL(file)
        blobUrls.current.add(previewUrl)
        return {
          file,
          item: {
            id: nextId(),
            name: file.name,
            previewUrl,
            status: 'uploading' as const,
            progress: 0,
          },
        }
      })
      setItems((prev) => [...prev, ...added.map((a) => a.item)])

      await Promise.all(
        added.map(async ({ file, item }) => {
          const error = await validateImageFile(file, rules)
          if (error) {
            patch(item.id, { status: 'error', error })
            return
          }
          try {
            const url = await upload(file, (progress) => patch(item.id, { progress }))
            patch(item.id, { status: 'done', progress: 1, url })
          } catch {
            patch(item.id, { status: 'error', error: '올리지 못했어요. 다시 시도해 주세요' })
          }
        }),
      )
    },
    [patch, rules, upload],
  )

  const remove = useCallback((id: string) => {
    setItems((prev) => {
      const target = prev.find((item) => item.id === id)
      if (target && blobUrls.current.has(target.previewUrl)) {
        URL.revokeObjectURL(target.previewUrl)
        blobUrls.current.delete(target.previewUrl)
      }
      return prev.filter((item) => item.id !== id)
    })
  }, [])

  const move = useCallback((from: number, to: number) => {
    setItems((prev) => {
      if (from === to || from < 0 || to < 0 || from >= prev.length || to >= prev.length) return prev
      const next = [...prev]
      const [moved] = next.splice(from, 1)
      next.splice(to, 0, moved)
      return next
    })
  }, [])

  useEffect(() => {
    const urls = blobUrls.current
    return () => urls.forEach((url) => URL.revokeObjectURL(url))
  }, [])

  return {
    items,
    setItems,
    add,
    remove,
    move,
    uploading: items.some((i) => i.status === 'uploading'),
  }
}
