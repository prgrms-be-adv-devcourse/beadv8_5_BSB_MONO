import { readFileSync } from 'node:fs'
import path from 'node:path'
import { expect } from '@playwright/test'

import { eventually, sql, type Api } from '../lunch/support'

// 합성 기록증(실제 기록증 아님). 10km · 넷타임 01:00:01 · 대회일 2024-05-03 · 배번 A-8592
export const CERT = path.join(__dirname, 'fixtures', 'synthetic-s-006.png')
export const CERT_VALUES = {
  netTimeSeconds: 3601,
  raceDate: '2024-05-03',
  courseClass: 'TEN_K',
  bib: 'A-8592',
} as const

export type MySubmission = {
  submissionId: number
  orderItemId: number
  status: string
  reasons: string[]
  denyReason: string | null
  denyMemo: string | null
}

export const RECORD_CERTS = '/api/v1/member/record-certs'
export const SELLER = '/api/v1/member/seller'

export const codeOf = (res: { raw: unknown }) => (res.raw as { code?: string } | null)?.code

/** 종목에 기록증 규칙을 넣는다: 2024-01-01 이후 10km 기록, 넷타임 2시간 이내. 등록 API의 규칙 칸 대신 DB에 바로 넣는다(규칙 조회만 본다) */
export function addCertRule(courseId: number) {
  sql(`update race_course set record_recognized_since = '2024-01-01', record_max_net_seconds = 7200
    where race_course_id = ${courseId};
    insert into product_record_cert_course_type (race_course_id, course_type) values (${courseId}, 'KM_10');`)
}

// 합성 기록증에서 브라우저 OCR이 실제로 찾은 칸 상자(시나리오 1 실행 때 저장된 값). 서버가 같은 자리를 다시 읽는다
export const CERT_BOXES = {
  NET_TIME: [
    [445, 505],
    [755, 505],
    [755, 567],
    [445, 567],
  ],
  NET_LABEL: [
    [414, 443],
    [782, 443],
    [782, 481],
    [414, 481],
  ],
  RACE_DATE: [
    [520, 1291],
    [679, 1291],
    [679, 1323],
    [520, 1323],
  ],
  COURSE: [
    [298, 339],
    [900, 339],
    [900, 376],
    [298, 376],
  ],
}
export const REC_MODEL_SHA256 = 'cd6e2ea50f6943ca7271eb8c56a877a5a90720b7047fe9c41a2e541a25773c9b'

/**
 * 열리지 않는 사진: 합성 기록증의 PNG 머리(서명 + 크기 칸, 33바이트)만 남기고 그림 데이터를 0으로 바꾼다.
 * 파일 검사(형식 · 크기 칸)는 통과하고 디코딩은 실패한다. 뒤만 자른 파일은 디코더가 앞부분만으로 열어 버려 쓰지 않는다
 */
export function brokenCert(): Buffer {
  const chunk = (type: string, data: Buffer) => {
    const length = Buffer.alloc(4)
    length.writeUInt32BE(data.length)
    return Buffer.concat([length, Buffer.from(type, 'ascii'), data, Buffer.alloc(4)])
  }
  return Buffer.concat([
    readFileSync(CERT).subarray(0, 33),
    chunk('IDAT', Buffer.alloc(4096)),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

/** 화면의 uploadFile과 같은 순서로 올린다: 업로드 주소 받기 → 저장소에 PUT → 완료 알리기. 파일 ID를 돌려준다 */
export async function uploadCert(api: Api, bytes: Buffer = readFileSync(CERT)): Promise<number> {
  const issued = await api.call<{
    fileId: number
    uploadUrl: string
    method?: string
    headers?: Record<string, string>
  }>('POST', '/api/v1/file/files', {
    fileType: 'RECORD_CERT',
    fileName: 'cert.png',
    contentType: 'image/png',
    fileSize: bytes.length,
  })
  expect(issued.status, `업로드 주소 ${JSON.stringify(issued.raw)}`).toBe(201)
  const headers = Object.fromEntries(
    Object.entries(issued.data.headers ?? {}).filter(([k]) => k.toLowerCase() !== 'content-length'),
  )
  const put = await fetch(issued.data.uploadUrl, {
    method: issued.data.method ?? 'PUT',
    headers,
    body: new Uint8Array(bytes),
  })
  expect(put.status, '저장소에 올리기').toBeLessThan(300)
  // 막 뜬 file-service는 첫 확인이 느려 게이트웨이가 504로 끊는다. 완료 API는 다시 불러도 같은 결과다
  await eventually(async () => {
    const done = await api.call('POST', `/api/v1/file/files/${issued.data.fileId}/complete`)
    return done.status === 200 || done.status === 201
  }, 90_000)
  return issued.data.fileId
}

/** 기록증 제출 API. 상자를 보내면 서버가 그 자리를 다시 읽고, 안 보내면 모든 칸을 읽지 못함으로 본다(→ 심사 필요) */
export const submitCert = (
  api: Api,
  orderItemId: number,
  fileId: number,
  fieldBoxes?: typeof CERT_BOXES,
) =>
  api.call<{ submissionId: number; status: string }>('POST', RECORD_CERTS, {
    orderItemId,
    fileId,
    roi: { x: 0, y: 0, width: 1200, height: 1750 },
    ...CERT_VALUES,
    fieldBoxes,
    specVersion: '1',
    recModelSha256: REC_MODEL_SHA256,
  })

export async function mySubmission(api: Api, orderItemId: number) {
  const res = await api.call<MySubmission[]>('GET', RECORD_CERTS)
  expect(res.status, JSON.stringify(res.raw)).toBe(200)
  return res.data.find((s) => s.orderItemId === orderItemId)
}

/** 서버 확인(ai-service, Kafka)이 끝나 확인 중을 벗어난 제출 */
export const waitChecked = (api: Api, orderItemId: number) =>
  eventually(async () => {
    const s = await mySubmission(api, orderItemId)
    return s && s.status !== 'CHECKING' ? s : null
  }, 2 * 60_000) as Promise<MySubmission>
