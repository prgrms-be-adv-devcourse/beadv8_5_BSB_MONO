import { getResponse, HttpResponse } from 'msw'

import { handlers } from './handlers'

/** 요청을 MSW 핸들러에 넘겨 응답을 받는다. 맞는 핸들러가 없으면 404. */
export async function resolveMockResponse(request: Request): Promise<Response> {
  const response = await getResponse(handlers, request)
  return (
    response ??
    HttpResponse.json(
      { message: `목 핸들러 없음: ${request.method} ${new URL(request.url).pathname}` },
      { status: 404 },
    )
  )
}
