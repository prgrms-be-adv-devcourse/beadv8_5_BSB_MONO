import type { Instrumentation } from 'next'

// 서버에서 난 오류를 JSON 한 줄로 남긴다(로그 수집기가 한 줄씩 읽는다).
// 경로는 조회 값(? 뒤)을 빼고, 헤더(쿠키·토큰)는 싣지 않는다.
export const onRequestError: Instrumentation.onRequestError = async (error, request) => {
  const message = error instanceof Error ? error.message : String(error)
  const digest =
    typeof error === 'object' && error !== null && 'digest' in error
      ? String(error.digest)
      : undefined
  console.error(
    JSON.stringify({
      event: 'request_error',
      path: request.path.split('?')[0],
      method: request.method,
      message,
      digest,
    }),
  )
}
