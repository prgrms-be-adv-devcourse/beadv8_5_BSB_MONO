import { FileType, uploadFile } from '@/features/file-upload'
import type { RaceForEdit } from '@/entities/race'
import type { productModel } from '@/shared/api'

// 판매 중 변경 · 대회 수정의 이미지: 바꾸거나 더 올리기만 한다 (지우기 없음, 피그마 V4)

/** 소개 이미지 한 칸. index는 이미 올린 이미지의 순서(정렬 뒤 위치), file은 새로 고른 이미지 */
export type IntroSlot = { key: string; url?: string; index?: number; file?: File }

export type ImageChange = {
  /** 새로 자른 대표 이미지와 미리보기 주소 */
  cover?: { file: File; url: string }
  intro: IntroSlot[]
}

/** 소개 이미지는 등록 때 모든 종목에 같은 순서로 붙으므로, 첫 종목의 목록을 화면에 보여 준다. */
export function initialImageChange({ request, files }: RaceForEdit): ImageChange {
  const first = request.courses.find((c) => c.courseId !== undefined)
  const courseFiles = first?.courseId === undefined ? [] : (files.courses[first.courseId] ?? [])
  const intro = courseFiles
    .filter((f) => f.fileType === 'DETAIL' && f.status !== 'DELETED')
    .sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
    .map((f, index) => ({ key: `file-${f.id}`, url: f.url ?? undefined, index }))
  return { intro }
}

export function coverUrl({ files }: RaceForEdit): string | undefined {
  return files.race.find((f) => f.fileType === 'THUMBNAIL' && f.url)?.url ?? undefined
}

export function hasImageChange(change: ImageChange): boolean {
  return change.cover !== undefined || change.intro.some((s) => s.file !== undefined)
}

type Upload = (
  file: File,
  type: typeof FileType.THUMBNAIL | typeof FileType.DETAIL,
) => Promise<{ fileId: number }>

/**
 * 수정 요청의 이미지 목록을 바뀐 이미지로 갈아 끼운다. 새 파일은 여기서 올린다.
 * 한 파일은 한 곳에만 붙으므로, 새 소개 이미지는 종목마다 따로 올린다 (등록과 같은 방식).
 */
export async function applyImageChange(
  request: productModel.RaceSaveRequestDto,
  change: ImageChange,
  upload: Upload = uploadFile,
): Promise<productModel.RaceSaveRequestDto> {
  const images = change.cover
    ? [
        {
          fileId: (await upload(change.cover.file, FileType.THUMBNAIL)).fileId,
          fileType: FileType.THUMBNAIL,
          sortNo: 0,
        },
      ]
    : request.images
  if (!change.intro.some((s) => s.file)) return { ...request, images }

  const courses = await Promise.all(
    request.courses.map(async (course) => {
      const others = course.images.filter((i) => i.fileType !== FileType.DETAIL)
      // sortNo가 비거나 겹쳐도 짝이 맞게, 화면과 같은 정렬의 위치로 맞춘다
      const kept = course.images
        .filter((i) => i.fileType === FileType.DETAIL)
        .sort((a, b) => a.sortNo - b.sortNo)
      const detail = await Promise.all(
        change.intro.map(async (slot) => {
          if (slot.file) return (await upload(slot.file, FileType.DETAIL)).fileId
          // 등록 뒤에 더한 종목처럼 그 순서의 이미지가 없는 종목은 그 칸을 건너뛴다
          return slot.index === undefined ? undefined : kept[slot.index]?.fileId
        }),
      )
      // 화면(첫 종목)보다 이미지가 많은 종목의 남는 이미지는 지우지 않고 뒤에 붙인다
      const shown = new Set(change.intro.map((slot) => slot.index))
      const rest = kept.filter((_, i) => !shown.has(i)).map((i) => i.fileId)
      const ids = [...detail, ...rest].filter((id): id is number => id !== undefined)
      return {
        ...course,
        images: [
          ...others,
          ...ids.map((fileId, sortNo) => ({ fileId, fileType: FileType.DETAIL, sortNo })),
        ],
      }
    }),
  )
  return { ...request, images, courses }
}
