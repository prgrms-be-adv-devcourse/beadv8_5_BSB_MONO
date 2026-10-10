import type { Meta, StoryObj } from '@storybook/nextjs-vite'
import { useState } from 'react'

import { ImageUpload, type ImageUploadValue } from './ImageUpload'
import { ImageCropDialog, ImageListDialog } from './ImageUploadDialog'
import { useImageList, type UploadImage } from './use-image-list'

// 실제 사진 대신 피그마 시안과 같은 산 그림을 SVG로 만든다.
function sampleSvg(width: number, height: number, sky: string) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}"><rect width="100%" height="100%" fill="${sky}"/><circle cx="${width * 0.78}" cy="${height * 0.27}" r="${height * 0.11}" fill="#c6f432"/><path d="M${-width * 0.1} ${height} L${width * 0.35} ${height * 0.46} L${width * 0.8} ${height} Z" fill="#94a3c4"/><path d="M${width * 0.38} ${height} L${width * 0.78} ${height * 0.58} L${width * 1.18} ${height} Z" fill="#b3c7ff"/></svg>`
}
const sampleUrl = (sky: string, w = 400, h = 300) =>
  `data:image/svg+xml;charset=utf-8,${encodeURIComponent(sampleSvg(w, h, sky))}`
const sampleFile = () =>
  new File([sampleSvg(1600, 1067, '#0052ff')], 'cookierun-seoul.svg', { type: 'image/svg+xml' })

const SKY = ['#0052ff', '#4a5a80', '#1c2541']
const COVER: ImageUploadValue[] = [
  {
    id: 'cover',
    url: sampleUrl(SKY[2], 300, 300),
    name: '2026-cookierun-seoul.jpg',
    meta: '1125×1125 · 1.2MB',
  },
]
const INTRO: ImageUploadValue[] = SKY.map((sky, i) => ({
  id: `intro-${i}`,
  url: sampleUrl(sky),
  name: `intro-${i}.jpg`,
}))

const noop = () => {}

const meta = {
  title: 'Inputs/ImageUpload',
  component: ImageUpload,
  args: {
    type: 'single',
    label: '대표 이미지 (필수 · 1장)',
    helper: '정사각(1:1) 1125×1125px 권장 · 대회 목록과 대회 상세 맨 위에 보여요',
    onOpen: noop,
    onFilesDrop: noop,
  },
  render: (args) => (
    <div className="max-w-[560px]">
      <ImageUpload {...args} />
    </div>
  ),
} satisfies Meta<typeof ImageUpload>

export default meta
type Story = StoryObj<typeof meta>

/** 칸에 파일을 끌어 올리면 파란 점선(Dragover)으로 바뀐다. 데스크톱에서 파일을 끌어 와 확인해 보세요. */
export const Empty: Story = {}

export const Filled: Story = { args: { value: COVER, onRemove: noop } }

export const Error: Story = { args: { error: '대표 이미지를 올려 주세요' } }

export const MultipleEmpty: Story = {
  args: {
    type: 'multiple',
    label: '소개 이미지 (여러 장)',
    helper: '코스 · 타임테이블 · 기념품 사이즈표를 넣어 주세요',
  },
}

export const MultipleFilled: Story = {
  args: { ...MultipleEmpty.args, value: INTRO },
}

// 업로드를 흉내 낸다. 1.2초 동안 진행률을 올리고, 이름에 fail이 있으면 실패한다.
const fakeUpload: UploadImage = (file, onProgress) =>
  new Promise((resolve, reject) => {
    let ratio = 0
    const timer = setInterval(() => {
      ratio = Math.min(1, ratio + 0.1)
      onProgress(ratio)
      if (ratio < 1) return
      clearInterval(timer)
      if (file.name.includes('fail')) reject(new globalThis.Error('upload failed'))
      else resolve(URL.createObjectURL(file))
    }, 120)
  })

const sizeText = (bytes: number) => `${(bytes / 1024 / 1024).toFixed(1)}MB`

function RegisterDemo() {
  const [cover, setCover] = useState<ImageUploadValue[]>([])
  const [cropOpen, setCropOpen] = useState(false)
  const [dropped, setDropped] = useState<File | null>(null)

  const [intro, setIntro] = useState<ImageUploadValue[]>([])
  const [listOpen, setListOpen] = useState(false)
  const list = useImageList({ upload: fakeUpload, rules: { minWidth: 1080 } })

  function openList(files: File[] = []) {
    // 창을 열 때는 저장된 목록에서 시작한다. 지난번에 저장하지 않은 변경은 버린다.
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
    <div className="flex max-w-[1120px] flex-col gap-6 rounded-lg bg-bg-surface p-6 tablet:flex-row">
      <ImageUpload
        label="대표 이미지 (필수 · 1장)"
        helper="정사각(1:1) 1125×1125px 권장 · 대회 목록과 대회 상세 맨 위에 보여요"
        value={cover}
        onOpen={() => {
          setDropped(null)
          setCropOpen(true)
        }}
        onFilesDrop={(files) => {
          setDropped(files[0] ?? null)
          setCropOpen(true)
        }}
        onRemove={() => setCover([])}
      />
      <ImageUpload
        type="multiple"
        label="소개 이미지 (여러 장)"
        helper="코스 · 타임테이블 · 기념품 사이즈표를 넣어 주세요"
        value={intro}
        onOpen={() => openList()}
        onFilesDrop={openList}
      />
      <ImageCropDialog
        open={cropOpen}
        onOpenChange={setCropOpen}
        file={dropped}
        onApply={(blob, name) => {
          setCover([
            {
              id: 'cover',
              url: URL.createObjectURL(blob),
              name,
              meta: `1125×1125 · ${sizeText(blob.size)}`,
            },
          ])
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
    </div>
  )
}

/** S02 대회 등록의 이미지 카드. 칸을 누르거나 파일을 끌어다 놓으면 창이 열린다. 이름에 fail이 든 파일은 업로드 실패로 보인다. */
export const RegisterFlow: Story = {
  name: '대회 등록 흐름',
  render: () => <RegisterDemo />,
}

/** 대표 이미지 자르기 창. 끌거나 방향키로 위치를, 막대로 크기를 맞춘다. */
export const CropDialog: Story = {
  name: '창 · 대표 이미지 자르기',
  render: function Render() {
    const [open, setOpen] = useState(true)
    const [file] = useState(sampleFile)
    return (
      <>
        <button
          type="button"
          className="text-label-m text-text-brand"
          onClick={() => setOpen(true)}
        >
          창 열기
        </button>
        <ImageCropDialog
          open={open}
          onOpenChange={setOpen}
          file={file}
          onApply={() => setOpen(false)}
        />
      </>
    )
  },
}

/** 파일을 고르기 전 단계. 파일을 고르면 자르기로 넘어간다. */
export const SelectDialog: Story = {
  name: '창 · 대표 이미지 올리기',
  render: function Render() {
    const [open, setOpen] = useState(true)
    return (
      <>
        <button
          type="button"
          className="text-label-m text-text-brand"
          onClick={() => setOpen(true)}
        >
          창 열기
        </button>
        <ImageCropDialog open={open} onOpenChange={setOpen} onApply={() => setOpen(false)} />
      </>
    )
  },
}

/** 소개 이미지 창. 올린 이미지, 올리는 중, 실패를 함께 보여 준다. 타일을 끌어서 순서를 바꾼다. */
export const ListDialog: Story = {
  name: '창 · 소개 이미지 올리기',
  render: function Render() {
    const [open, setOpen] = useState(true)
    const list = useImageList({
      upload: fakeUpload,
      initial: [
        { id: 'a', name: 'course.jpg', previewUrl: sampleUrl(SKY[0]), status: 'done', progress: 1 },
        {
          id: 'b',
          name: 'timetable.jpg',
          previewUrl: sampleUrl(SKY[1]),
          status: 'done',
          progress: 1,
        },
        {
          id: 'c',
          name: 'kit.jpg',
          previewUrl: sampleUrl(SKY[2]),
          status: 'uploading',
          progress: 0.62,
        },
        {
          id: 'd',
          name: 'size-chart.png',
          previewUrl: '',
          status: 'error',
          progress: 0,
          error: '가로 1080px보다 작아요',
        },
      ],
    })
    return (
      <>
        <button
          type="button"
          className="text-label-m text-text-brand"
          onClick={() => setOpen(true)}
        >
          창 열기
        </button>
        <ImageListDialog
          open={open}
          onOpenChange={setOpen}
          items={list.items}
          onFilesAdd={(files) => void list.add(files)}
          onRemove={list.remove}
          onMove={list.move}
          onSave={() => setOpen(false)}
        />
      </>
    )
  },
}
