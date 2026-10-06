import { getServerEnv } from '@/shared/config'

/**
 * API_MOCKING=on일 때 브라우저의 /api/... 요청에 MSW 핸들러로 응답한다.
 * off일 때는 next.config rewrites가 먼저 가로채서 여기까지 오지 않는다.
 */
async function handle(request: Request): Promise<Response> {
  if (getServerEnv().API_MOCKING !== 'on') {
    return Response.json({ message: 'API mocking is off' }, { status: 404 })
  }
  const { resolveMockResponse } = await import('@/shared/mocks')
  return resolveMockResponse(request)
}

export { handle as DELETE, handle as GET, handle as PATCH, handle as POST, handle as PUT }
