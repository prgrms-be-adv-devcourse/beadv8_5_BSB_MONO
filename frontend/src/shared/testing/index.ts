// 테스트 전용 묶음. 앱 코드에서 가져오지 않는다 (msw/node가 번들에 들어간다).
// 다른 층의 테스트는 shared 안쪽 파일을 직접 가져올 수 없어(FSD 규칙) 여기로 모은다.
export { server } from '@/shared/mocks/node'
// 가짜 응답 모드의 Refresh Token 쿠키. 요청에 Cookie 헤더로 붙이면 기본 재발급 목이 성공한다.
export { MOCK_LOGGED_IN_COOKIE } from '@/shared/mocks/mock-session'
// 가짜 응답 모드의 GA4 이용 통계 예시 (서버 코드 테스트용)
export { mockGa4Reporter } from '@/shared/mocks/ga4-stats'

// orval이 만든 응답 예시 목: productMock.getSearchResponseMock() 등
export * as cashMock from '@/shared/api/generated/cash/cash.msw'
export * as fileMock from '@/shared/api/generated/file/file.msw'
export * as memberMock from '@/shared/api/generated/member/member.msw'
export * as orderMock from '@/shared/api/generated/order/order.msw'
export * as payoutMock from '@/shared/api/generated/payout/payout.msw'
export * as productMock from '@/shared/api/generated/product/product.msw'

// 공용 달력 칸에서 날짜 고르기: await pickDate(user, '생년월일', '1995-03-02')
export { pickDate } from './pick-date'
