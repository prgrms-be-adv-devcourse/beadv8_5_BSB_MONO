import {
  FUNNELS,
  rankFormErrors,
  resolveStatsPeriod,
  seoulToday,
  statsPeriodQuerySchema,
  TOP_SCREEN_COUNT,
  toTrendPoints,
  type FormErrorStats,
  type FunnelKind,
  type FunnelStats,
  type RealtimeStats,
  type StatsPeriod,
  type StatsResponse,
  type StatsSection,
  type TrendKind,
  type TrendStats,
} from '@/entities/usage-stats'
import { ApiError, customFetch } from '@/shared/api'
import { getGa4Reporter, type Ga4Reporter } from '@/shared/ga4'

/**
 * 어드민 이용 통계 숫자. GA4 서비스 계정 키는 이 서버 코드에서만 쓰고 브라우저에는 사람 수만 내려 준다.
 *
 * 주소가 /api 밖인 이유: 운영 Nginx는 /api를 백엔드 게이트웨이로 보내므로 Next까지 오지 않는다.
 *
 * GET /admin/stats/data?section=buyer|seller|formErrors|visitors|purchases&period=7d|30d|custom[&start&end]
 * GET /admin/stats/data?section=realtime
 * - 200 { status: 'ok', data } · { status: 'notConfigured' }(키 없음)
 * - 401 로그인 필요 · 403 관리자 아님 · 400 기간 오류 · 502 GA4 호출 실패(영역마다 따로)
 */
export async function GET(request: Request): Promise<Response> {
  const params = new URL(request.url).searchParams
  const section = params.get('section')
  if (!isSection(section)) {
    return error(400, `영역(section)은 ${SECTIONS.join(', ')} 중 하나입니다.`, 'INVALID_REQUEST')
  }

  const denied = await checkAdmin(request.headers.get('Authorization'))
  if (denied) return denied

  const ga4 = await getGa4Reporter()
  if (ga4.status === 'notConfigured') return ok({ status: 'notConfigured' })
  if (ga4.status === 'invalid') {
    return error(502, 'GA4 연결 정보의 형식이 잘못됐습니다.', 'GA4_CONFIG_INVALID')
  }

  try {
    if (section === 'realtime') {
      return ok<RealtimeStats>({ status: 'ok', data: await realtime(ga4.reporter) })
    }
    const query = statsPeriodQuerySchema.safeParse(Object.fromEntries(params))
    const period = query.success ? resolveStatsPeriod(query.data, seoulToday()) : null
    if (!period) return error(400, '기간이 올바르지 않습니다.', 'INVALID_REQUEST')
    return ok({ status: 'ok', data: await periodSection(ga4.reporter, section, period) })
  } catch (e) {
    // 키는 오류에 들어 있지 않지만, 혹시 몰라 오류 문구만 남긴다
    console.error(`[ga4] ${section} 불러오기 실패: ${e instanceof Error ? e.message : String(e)}`)
    return error(502, 'GA4에서 숫자를 불러오지 못했습니다.', 'GA4_REQUEST_FAILED')
  }
}

/**
 * 관리자인지 백엔드에 묻는다. 기존 어드민 화면과 같이 브라우저의 Access Token을 게이트웨이에 그대로 보내고,
 * 회원 서비스의 관리자 전용 API가 403(MEMBER_NOT_ADMIN)을 주면 막는다.
 * 관리자 여부만 알려 주는 공개 API가 아직 없어 판매자 신청 목록(심사 대기)으로 확인하고, 받은 목록은 버린다.
 */
async function checkAdmin(authorization: string | null): Promise<Response | null> {
  if (!authorization?.startsWith('Bearer ')) {
    return error(401, '로그인이 필요합니다.', 'LOGIN_REQUIRED')
  }
  try {
    await customFetch('/api/v1/member/admin/sellers?status=PENDING', {
      headers: { Authorization: authorization },
      cache: 'no-store',
    })
    return null
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      return error(401, '로그인이 필요합니다.', 'LOGIN_REQUIRED')
    }
    if (e instanceof ApiError && e.status === 403) {
      return error(403, '관리자만 이용할 수 있습니다.', 'MEMBER_NOT_ADMIN')
    }
    // 확인하지 못하면 숫자를 주지 않는다
    return error(502, '관리자인지 확인하지 못했습니다.', 'ADMIN_CHECK_FAILED')
  }
}

const SECTIONS: StatsSection[] = [
  'realtime',
  'buyer',
  'seller',
  'formErrors',
  'visitors',
  'purchases',
]

function isSection(value: string | null): value is StatsSection {
  return SECTIONS.includes(value as StatsSection)
}

function periodSection(
  reporter: Ga4Reporter,
  section: Exclude<StatsSection, 'realtime'>,
  period: StatsPeriod,
): Promise<FunnelStats | FormErrorStats | TrendStats> {
  switch (section) {
    case 'buyer':
    case 'seller':
      return funnel(reporter, section, period)
    case 'formErrors':
      return formErrors(reporter, period)
    case 'visitors':
    case 'purchases':
      return trend(reporter, section, period)
  }
}

async function funnel(
  reporter: Ga4Reporter,
  kind: FunnelKind,
  period: StatsPeriod,
): Promise<FunnelStats> {
  const { siteArea, steps } = FUNNELS[kind]
  const users = await reporter.usersByEvent({
    events: steps.map((step) => step.event),
    siteArea,
    dateRanges: [
      { name: 'current', startDate: period.current.start, endDate: period.current.end },
      { name: 'previous', startDate: period.previous.start, endDate: period.previous.end },
    ],
  })
  return {
    period,
    steps: steps.map((step) => ({
      ...step,
      users: users.current?.[step.event] ?? 0,
      previousUsers: users.previous?.[step.event] ?? 0,
    })),
  }
}

/** 판매자 대회 등록 입력 오류 묶음 순위. 고른 기간만 센다 */
async function formErrors(reporter: Ga4Reporter, period: StatsPeriod): Promise<FormErrorStats> {
  const rows = await reporter.usersByDimension({
    eventName: 'seller_race_form_error',
    dimension: 'customEvent:error_sections',
    dateRange: { name: 'current', startDate: period.current.start, endDate: period.current.end },
  })
  return { period, groups: rankFormErrors(rows) }
}

/** 일별 추이. 방문자는 그날 활성 사용자 수, 구매는 그날 purchase 이벤트를 보낸 사람 수 */
async function trend(
  reporter: Ga4Reporter,
  kind: TrendKind,
  period: StatsPeriod,
): Promise<TrendStats> {
  const byDate = await reporter.usersByDate({
    ...(kind === 'visitors'
      ? { metric: 'activeUsers' as const }
      : { metric: 'totalUsers' as const, eventName: 'purchase' }),
    dateRanges: [
      { name: 'current', startDate: period.current.start, endDate: period.current.end },
      { name: 'previous', startDate: period.previous.start, endDate: period.previous.end },
    ],
  })
  return { period, points: toTrendPoints(period, byDate) }
}

function realtime(reporter: Ga4Reporter): Promise<RealtimeStats> {
  return reporter.realtime({ topScreens: TOP_SCREEN_COUNT })
}

function ok<T>(body: StatsResponse<T>): Response {
  return Response.json(body, { headers: { 'Cache-Control': 'no-store' } })
}

// 오류는 백엔드와 같은 RsData 모양이라 customFetch의 ApiError가 문구와 코드를 그대로 읽는다
function error(status: number, message: string, code: string): Response {
  return Response.json(
    { status, message, code, data: null },
    { status, headers: { 'Cache-Control': 'no-store' } },
  )
}
