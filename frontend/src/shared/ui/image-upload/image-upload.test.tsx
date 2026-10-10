import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeAll, describe, expect, it, vi } from 'vitest'

import { checkImageFile, clampOffset, cropRect, zoomOffset } from './image-file'
import { ImageUpload } from './ImageUpload'
import { ImageListDialog } from './ImageUploadDialog'
import { useImageList, type ImageItem } from './use-image-list'

beforeAll(() => {
  // jsdom에는 blob 주소 함수가 없다.
  URL.createObjectURL = vi.fn(() => 'blob:test')
  URL.revokeObjectURL = vi.fn()
})

const file = (name: string, type: string, size = 10) =>
  new File([new Uint8Array(size)], name, { type })

describe('checkImageFile', () => {
  it('JPG·PNG·WebP가 아니면 막는다', () => {
    expect(checkImageFile(file('a.gif', 'image/gif'))).toBe('JPG · PNG · WebP만 올릴 수 있어요')
    expect(checkImageFile(file('a.webp', 'image/webp'))).toBeNull()
  })

  it('10MB를 넘으면 막는다', () => {
    expect(checkImageFile(file('a.jpg', 'image/jpeg', 10 * 1024 * 1024 + 1))).toBe(
      '10MB가 넘는 파일이에요',
    )
  })
})

describe('자르기 계산', () => {
  const natural = { width: 2000, height: 1000 }
  const frame = 260
  const scale = 0.26 // 짧은 변 1000이 틀 260을 꽉 채우는 배율

  it('틀 밖으로 빈 곳이 보이지 않게 위치를 막는다', () => {
    expect(clampOffset({ x: 50, y: 50 }, natural, frame, scale)).toEqual({ x: 0, y: 0 })
    expect(clampOffset({ x: -9999, y: -10 }, natural, frame, scale)).toEqual({ x: -260, y: 0 })
  })

  it('확대해도 틀 가운데가 가리키는 지점은 그대로다', () => {
    const before = { x: -130, y: 0 }
    const after = zoomOffset(before, frame, scale, scale * 2)
    const centerBefore = cropRect(before, frame, scale)
    const centerAfter = cropRect(after, frame, scale * 2)
    expect(centerAfter.sx + centerAfter.size / 2).toBeCloseTo(
      centerBefore.sx + centerBefore.size / 2,
    )
    expect(centerAfter.sy + centerAfter.size / 2).toBeCloseTo(
      centerBefore.sy + centerBefore.size / 2,
    )
  })

  it('틀 안 영역을 원본 픽셀로 바꾼다', () => {
    expect(cropRect({ x: -130, y: 0 }, frame, scale)).toEqual({ sx: 500, sy: 0, size: 1000 })
  })
})

describe('useImageList', () => {
  it('형식이 틀린 파일은 올리지 않고 실패로 남긴다', async () => {
    const upload = vi.fn(async () => 'https://cdn/ok.jpg')
    const { result } = renderHook(() => useImageList({ upload }))

    await act(() => result.current.add([file('a.gif', 'image/gif'), file('b.jpg', 'image/jpeg')]))

    expect(upload).toHaveBeenCalledTimes(1)
    expect(result.current.items.map((i) => [i.name, i.status])).toEqual([
      ['a.gif', 'error'],
      ['b.jpg', 'done'],
    ])
  })

  it('순서를 바꾼다', async () => {
    const { result } = renderHook(() => useImageList({ upload: async () => 'u' }))
    await act(() =>
      result.current.add([
        file('1.jpg', 'image/jpeg'),
        file('2.jpg', 'image/jpeg'),
        file('3.jpg', 'image/jpeg'),
      ]),
    )
    act(() => result.current.move(2, 0))
    expect(result.current.items.map((i) => i.name)).toEqual(['3.jpg', '1.jpg', '2.jpg'])
  })
})

function dropEvent(files: File[]) {
  return { dataTransfer: { files, types: ['Files'] } }
}

describe('ImageUpload', () => {
  it('칸을 누르면 창을 열고, 파일을 놓으면 그 파일을 넘긴다', async () => {
    const onOpen = vi.fn()
    const onFilesDrop = vi.fn()
    render(<ImageUpload label="대표 이미지" onOpen={onOpen} onFilesDrop={onFilesDrop} />)
    const zone = screen.getByRole('button', { name: '대표 이미지' })

    await userEvent.click(zone)
    expect(onOpen).toHaveBeenCalled()

    const dropped = file('a.jpg', 'image/jpeg')
    fireEvent.dragEnter(zone, dropEvent([dropped]))
    expect(screen.getByText('여기에 놓으면 올라가요')).toBeInTheDocument()
    fireEvent.drop(zone, dropEvent([dropped]))
    expect(onFilesDrop).toHaveBeenCalledWith([dropped])
    expect(screen.queryByText('여기에 놓으면 올라가요')).not.toBeInTheDocument()
  })
})

describe('ImageListDialog', () => {
  const base = { id: 'a', name: 'a.jpg', previewUrl: 'blob:a', progress: 1 }

  it('올리는 중에 저장하면 다 올라간 뒤에 저장한다', async () => {
    const onSave = vi.fn()
    const props = {
      open: true,
      onOpenChange: vi.fn(),
      onFilesAdd: vi.fn(),
      onRemove: vi.fn(),
      onMove: vi.fn(),
      onSave,
    }
    const uploading: ImageItem[] = [{ ...base, status: 'uploading', progress: 0.5 }]
    const { rerender } = render(<ImageListDialog {...props} items={uploading} />)

    await userEvent.click(screen.getByRole('button', { name: '저장하기' }))
    expect(onSave).not.toHaveBeenCalled()

    rerender(<ImageListDialog {...props} items={[{ ...base, status: 'done' }]} />)
    expect(onSave).toHaveBeenCalledTimes(1)
  })
})
