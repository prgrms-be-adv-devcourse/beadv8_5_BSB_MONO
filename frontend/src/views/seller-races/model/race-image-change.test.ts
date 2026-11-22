import { describe, expect, it, vi } from 'vitest'

import type { productModel } from '@/shared/api'

import { applyImageChange } from './race-image-change'

const detail = (fileId: number, sortNo: number) => ({ fileId, fileType: 'DETAIL' as const, sortNo })

const request = {
  images: [{ fileId: 1, fileType: 'THUMBNAIL', sortNo: 0 }],
  courses: [
    { courseId: 10, images: [detail(11, 0), detail(12, 1)] },
    {
      courseId: 20,
      images: [detail(21, 0), detail(22, 1), { fileId: 23, fileType: 'COURSE', sortNo: 0 }],
    },
  ],
} as unknown as productModel.RaceSaveRequestDto

const file = (name: string) => new File(['x'], name, { type: 'image/png' })

describe('applyImageChange', () => {
  it('바뀐 것이 없으면 이미지를 그대로 다시 보낸다', async () => {
    const upload = vi.fn()
    const result = await applyImageChange(
      request,
      {
        intro: [
          { key: 'a', index: 0 },
          { key: 'b', index: 1 },
        ],
      },
      upload,
    )
    expect(upload).not.toHaveBeenCalled()
    expect(result).toEqual(request)
  })

  it('대표 이미지만 바꾸면 새 파일 하나만 올리고 종목 이미지는 그대로 둔다', async () => {
    const upload = vi.fn().mockResolvedValue({ fileId: 99 })
    const result = await applyImageChange(
      request,
      { cover: { file: file('c.png'), url: 'blob:c' }, intro: [{ key: 'a', index: 0 }] },
      upload,
    )
    expect(upload).toHaveBeenCalledTimes(1)
    expect(result.images).toEqual([{ fileId: 99, fileType: 'THUMBNAIL', sortNo: 0 }])
    expect(result.courses).toBe(request.courses)
  })

  it('소개 이미지를 바꾸고 더하면 종목마다 따로 올리고, 남긴 이미지는 그 종목의 파일을 쓴다', async () => {
    let next = 100
    const upload = vi.fn().mockImplementation(async () => ({ fileId: next++ }))
    const result = await applyImageChange(
      request,
      {
        intro: [
          { key: 'a', index: 0, file: file('new0.png') },
          { key: 'b', index: 1 },
          { key: 'c', file: file('added.png') },
        ],
      },
      upload,
    )
    expect(upload).toHaveBeenCalledTimes(4)
    const [c1, c2] = result.courses.map((c) => c.images)
    expect(c1[1].fileId).toBe(12)
    expect(c1.map((i) => i.sortNo)).toEqual([0, 1, 2])
    // 다른 용도(코스 안내) 이미지는 남기고, 남긴 소개 이미지는 그 종목의 파일 ID(22)를 쓴다
    expect(c2[0]).toEqual({ fileId: 23, fileType: 'COURSE', sortNo: 0 })
    expect(c2.slice(1).map((i) => i.sortNo)).toEqual([0, 1, 2])
    expect(c2[2].fileId).toBe(22)
    const uploaded = [...c1, ...c2].filter((i) => i.fileId >= 100).map((i) => i.fileId)
    expect(new Set(uploaded).size).toBe(4)
  })

  it('첫 종목보다 소개 이미지가 많은 종목은 남는 이미지를 지우지 않고, sortNo가 겹쳐도 같은 파일을 두 번 넣지 않는다', async () => {
    const uneven = {
      images: [],
      courses: [
        { courseId: 10, images: [detail(11, 0), detail(12, 0)] },
        { courseId: 20, images: [detail(21, 0), detail(22, 0), detail(23, 0)] },
      ],
    } as unknown as productModel.RaceSaveRequestDto
    const upload = vi.fn().mockResolvedValue({ fileId: 200 })
    const result = await applyImageChange(
      uneven,
      {
        intro: [
          { key: 'a', index: 0 },
          { key: 'b', index: 1 },
          { key: 'c', file: file('added.png') },
        ],
      },
      upload,
    )
    expect(result.courses[0].images.map((i) => i.fileId)).toEqual([11, 12, 200])
    expect(result.courses[1].images.map((i) => i.fileId)).toEqual([21, 22, 200, 23])
  })
})
