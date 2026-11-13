import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'

import { resetAuthSessionForTesting } from '@/shared/api/auth-session'
import {
  resetMockKitsForTesting,
  resetMockOrderForTesting,
  resetMockQueueForTesting,
} from '@/shared/mocks/handlers'
import { server } from '@/shared/mocks/node'
import { resetMockWalletForTesting } from '@/shared/mocks/wallet'
import { navigationMock, resetNavigationMock } from '@/shared/testing/navigation-mock'

// 목록 상태를 URL에 담는 화면이 라우터 없이도 그려지도록 기본 가짜를 둔다. 파일에서 따로 mock하면 그쪽이 우선한다.
vi.mock('next/navigation', async (importOriginal) => ({
  ...(await importOriginal<typeof import('next/navigation')>()),
  ...navigationMock('/'),
}))

// 개발 서버와 같은 MSW 핸들러를 테스트에서도 쓴다.
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }))
afterEach(() => {
  server.resetHandlers()
  cleanup()
  resetNavigationMock()
  // 메모리의 Access Token·재발급 상태가 다음 테스트로 넘어가지 않게 한다
  resetAuthSessionForTesting()
  // 가짜 응답 모드의 주문 1 상태(결제 대기 · 결제 완료)도 처음으로 돌린다
  resetMockOrderForTesting()
  // 대기열 목의 줄 선 순서 · 입장 끝 시각도 처음으로 돌린다
  resetMockQueueForTesting()
  // 예치금 목의 잔액 · 충전 기록도 처음으로 돌린다
  resetMockWalletForTesting()
  // 키트 목의 발송 · 배송 완료 상태도 처음으로 돌린다
  resetMockKitsForTesting()
  // 가짜 로그인 상태는 요청 쿠키로만 판단한다. Node의 MSW는 쿠키를 저장하지 않으므로 지울 것이 없다.
})
afterAll(() => server.close())
