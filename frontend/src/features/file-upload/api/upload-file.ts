import { fileApi, fileModel, unwrapData } from '@/shared/api'

export type FileType = fileModel.FileUploadRequestDtoFileType
export const FileType = fileModel.FileUploadRequestDtoFileType

export type UploadedFile = {
  fileId: number
  /** 업로드를 확인한 뒤 받는 보기 주소 (30분 뒤 만료) */
  url?: string | null
}

// 브라우저가 직접 정하는 헤더라 fetch에 넣으면 막히거나 무시된다. 크기는 body로 브라우저가 붙인다.
const FORBIDDEN_HEADERS = new Set(['content-length', 'host'])

/**
 * 파일 하나를 S3에 올린다.
 * 1) 파일 서비스에서 presigned PUT URL을 받고 2) 그 주소로 파일을 바로 PUT 한 뒤 3) 업로드 완료를 알린다.
 * 2)는 S3로 바로 가는 요청이라 customFetch(토큰·/api 경로)를 지나지 않는다.
 */
export async function uploadFile(
  file: Blob,
  fileType: FileType,
  {
    fileName = file instanceof File ? file.name : 'upload',
    signal,
  }: {
    fileName?: string
    /** 화면을 떠날 때 S3 PUT을 멈춘다. 멈추면 AbortError를 던진다 */
    signal?: AbortSignal
  } = {},
): Promise<UploadedFile> {
  const issued = unwrapData(
    await fileApi.createUploadUrl(
      {
        fileType,
        fileName,
        contentType: file.type,
        fileSize: file.size,
      },
      { signal },
    ),
  )
  if (issued.fileId === undefined || !issued.uploadUrl) {
    throw new Error('업로드 주소를 받지 못했어요')
  }

  const headers = Object.fromEntries(
    Object.entries(issued.headers ?? {}).filter(
      ([name]) => !FORBIDDEN_HEADERS.has(name.toLowerCase()),
    ),
  )
  const put = await fetch(issued.uploadUrl, {
    method: issued.method ?? 'PUT',
    headers,
    body: file,
    signal,
  })
  if (!put.ok) {
    throw new Error(`파일을 올리지 못했어요 (${put.status})`)
  }

  const completed = unwrapData(await fileApi.completeUpload(issued.fileId))
  return { fileId: issued.fileId, url: completed.url }
}
