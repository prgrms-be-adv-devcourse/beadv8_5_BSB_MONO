import { http, HttpResponse } from 'msw'

/**
 * 모든 MSW 핸들러 목록. 개발 서버(/api 라우트)와 테스트가 같이 쓴다.
 * orval로 생성한 목(예: getRacesMock())은 여기에 펼쳐 넣는다.
 */
export const handlers = [
  // 목 파이프라인이 살아있는지 확인하는 용도
  http.get('*/api/health', () => HttpResponse.json({ status: 'ok', mocked: true })),
]
