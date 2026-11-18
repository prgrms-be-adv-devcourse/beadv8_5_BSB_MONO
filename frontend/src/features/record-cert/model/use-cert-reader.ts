import { useEffect, useRef } from 'react'

import { createCertReader, type CertReader, type ReaderRequest } from '../lib/cert-reader'

/**
 * 화면에 들어오면 OCR worker를 띄워 모델을 미리 받는다(사진을 고르는 동안 준비가 끝나게). 화면을 떠나면 끝낸다.
 * read는 10초 안에 못 읽으면 직접 입력으로 넘긴다.
 */
export function useCertReader() {
  const reader = useRef<CertReader | null>(null)

  useEffect(() => {
    // type: 'module'을 주면 안 된다. 운영 빌드의 turbopack worker 시작 코드가 importScripts를 쓴다
    const created = createCertReader(
      () => new Worker(new URL('../lib/ocr.worker.ts', import.meta.url)),
    )
    reader.current = created
    return () => {
      created.dispose()
      reader.current = null
    }
  }, [])

  return {
    read: (request: ReaderRequest) =>
      reader.current?.read(request) ??
      Promise.resolve({ kind: 'manual', reason: 'error' } as const),
    skip: () => reader.current?.skip(),
  }
}
