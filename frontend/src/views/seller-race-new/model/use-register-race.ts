import { useMutation, useQueryClient } from '@tanstack/react-query'

import { FileType, uploadFile } from '@/features/file-upload'
import { ApiError, productApi, productModel, unwrapData, useIdempotencyKey } from '@/shared/api'

import { toKitOptionsRequest, toRaceSaveRequest, type RaceFormValues } from './race-form'

export type RaceImages = {
  /** 대표 이미지 1장 (자른 결과) */
  cover: File
  /** 소개 이미지 (보이는 순서대로) */
  intro: File[]
}

/** 한 종목에 붙일 이미지를 올리고 파일 ID 목록을 만든다. */
async function uploadCourseImages(images: RaceImages): Promise<productModel.RaceImageRequestDto[]> {
  const [cover, ...intro] = await Promise.all([
    uploadFile(images.cover, FileType.THUMBNAIL),
    ...images.intro.map((file) => uploadFile(file, FileType.DETAIL)),
  ])
  return [
    { fileId: cover.fileId, fileType: FileType.THUMBNAIL, sortNo: 0 },
    ...intro.map((file, i) => ({ fileId: file.fileId, fileType: FileType.DETAIL, sortNo: i })),
  ]
}

/** 같은 파일인지 알아볼 값. File은 JSON으로 바꾸면 내용이 비어서 이름 · 크기 · 수정 시각으로 대신한다. */
function fileSignature(file: File) {
  return [file.name, file.size, file.lastModified]
}

/** 대회는 등록했지만 키트 옵션을 저장하지 못했다. 등록하기를 다시 누르면 같은 대회에 옵션만 다시 보낸다 */
export class KitOptionsNotSavedError extends Error {
  constructor(readonly cause: unknown) {
    super('키트 옵션을 저장하지 못했어요')
    this.name = 'KitOptionsNotSavedError'
  }
}

/** 대회의 키트 옵션을 처음 등록한다. 이미 있으면(409: 앞선 저장의 응답만 잃은 재시도) 같은 목록으로 통째로 바꾼다 */
async function saveKitOptions(raceId: number, body: productModel.KitOptionsSaveRequestDto) {
  try {
    await productApi.registerKitOptions(raceId, body)
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 409)) throw error
    await productApi.updateKitOptions(raceId, body)
  }
}

/**
 * 대회 등록: 사진을 올린 뒤 대회를 등록하고, 받은 대회 ID로 키트 옵션을 따로 등록한다.
 * 이미지는 종목마다 붙고 한 파일은 한 종목에만 연결되므로, 화면에서 고른 사진을 종목마다 따로 올린다.
 *
 * 같은 입력으로 다시 누르면(두 번 클릭 · 실패 뒤 재시도) 같은 Idempotency-Key를 보내 대회가 한 번만 등록된다.
 * 키트 옵션은 키에 넣지 않는다. 옵션 저장만 실패했을 때 옵션을 고쳐 다시 눌러도 같은 대회에 붙는다.
 * 대회 칸이나 사진을 바꾸면 새 키를 쓴다.
 */
export function useRegisterRace() {
  const queryClient = useQueryClient()
  const registerKey = useIdempotencyKey()
  return useMutation({
    mutationFn: async ({ values, images }: { values: RaceFormValues; images: RaceImages }) => {
      const { kitOptions, ...race } = values
      const idempotencyKey = registerKey({
        values: race,
        cover: fileSignature(images.cover),
        intro: images.intro.map(fileSignature),
      })
      const courseImages = await Promise.all(values.courses.map(() => uploadCourseImages(images)))
      const saved = unwrapData(
        await productApi.register(toRaceSaveRequest(values, courseImages), { idempotencyKey }),
      )
      if (kitOptions.length > 0) {
        await saveKitOptions(saved.raceId, toKitOptionsRequest(kitOptions)).catch((error) => {
          throw new KitOptionsNotSavedError(error)
        })
      }
      return saved
    },
    // 옵션 저장만 실패해도 대회는 생겼으므로 목록을 다시 불러온다
    onSettled: () => queryClient.invalidateQueries({ queryKey: productApi.getFindMineQueryKey() }),
  })
}
