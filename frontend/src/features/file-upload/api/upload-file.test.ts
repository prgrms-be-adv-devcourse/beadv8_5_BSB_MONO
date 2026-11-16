import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'

import { ApiError } from '@/shared/api'
import { server } from '@/shared/testing'

import { uploadFile } from './upload-file'

const png = () => new File([new Uint8Array([137, 80, 78, 71])], 'cover.png', { type: 'image/png' })

describe('uploadFile', () => {
  it('업로드 URL을 받아 S3에 PUT 하고 완료를 알린 뒤 파일 ID를 돌려준다', async () => {
    const steps: string[] = []
    server.events.on('request:start', ({ request }) => {
      steps.push(`${request.method} ${new URL(request.url).pathname}`)
    })

    const uploaded = await uploadFile(png(), 'THUMBNAIL')

    server.events.removeAllListeners()
    expect(uploaded.fileId).toBeGreaterThan(0)
    expect(steps).toEqual([
      'POST /api/v1/file/files',
      `PUT /api/mock-s3/files/${uploaded.fileId}`,
      `POST /api/v1/file/files/${uploaded.fileId}/complete`,
    ])
  })

  it('발급 응답의 content-type 헤더를 붙이고 content-length는 브라우저에 맡긴다', async () => {
    let contentType: string | null = null
    server.use(
      http.put('*/api/mock-s3/files/:fileId', ({ request }) => {
        contentType = request.headers.get('content-type')
        return new HttpResponse(null, { status: 200 })
      }),
    )
    await uploadFile(png(), 'THUMBNAIL')
    expect(contentType).toBe('image/png')
  })

  it('취소 신호가 오면 S3 PUT을 멈추고 AbortError를 던지며 완료를 부르지 않는다', async () => {
    let completed = false
    server.use(
      http.put('*/api/mock-s3/files/:fileId', async ({ request }) => {
        // 취소되거나 0.3초가 지나면 답한다
        await new Promise((resolve) => {
          request.signal.addEventListener('abort', resolve)
          setTimeout(resolve, 300)
        })
        return new HttpResponse(null, { status: 200 })
      }),
      http.post('*/api/v1/file/files/:fileId/complete', () => {
        completed = true
        return HttpResponse.json({ status: 200, message: '', data: {} })
      }),
    )
    const controller = new AbortController()
    const uploading = uploadFile(png(), 'THUMBNAIL', { signal: controller.signal })
    setTimeout(() => controller.abort(), 20)

    await expect(uploading).rejects.toMatchObject({ name: 'AbortError' })
    expect(completed).toBe(false)
  })

  it('S3 PUT이 실패하면 완료를 부르지 않고 오류를 던진다', async () => {
    let completed = false
    server.use(
      http.put('*/api/mock-s3/files/:fileId', () => new HttpResponse(null, { status: 403 })),
      http.post('*/api/v1/file/files/:fileId/complete', () => {
        completed = true
        return HttpResponse.json({ status: 200, message: '', data: {} })
      }),
    )
    await expect(uploadFile(png(), 'THUMBNAIL')).rejects.toThrow('파일을 올리지 못했어요 (403)')
    expect(completed).toBe(false)
  })

  it('형식·크기 검사에 걸리면(400) 서버 문구를 담은 ApiError를 던진다', async () => {
    server.use(
      http.post('*/api/v1/file/files', () =>
        HttpResponse.json(
          { status: 400, message: '이미지는 10MB 이하만 올릴 수 있습니다.', data: null },
          { status: 400 },
        ),
      ),
    )
    const error = await uploadFile(png(), 'THUMBNAIL').catch((e: unknown) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).serverMessage).toBe('이미지는 10MB 이하만 올릴 수 있습니다.')
  })
})
