import type { Ga4Config } from './config'
import {
  parseRealtime,
  parseUsersByDate,
  parseUsersByDimension,
  parseUsersByEvent,
  realtimeScreensRequest,
  realtimeTotalRequest,
  usersByDateRequest,
  usersByDimensionRequest,
  usersByEventRequest,
  type Ga4Reporter,
} from './report'

/** GA4가 늦게 답해도 어드민 화면이 오래 멈추지 않게 한다 */
const CALL_TIMEOUT_MS = 10_000

/**
 * 실제 GA4 Data API(v1beta)를 부르는 Ga4Reporter.
 * 패키지가 gRPC를 쓰므로 실제로 부를 때만 불러온다. 가짜 응답 모드와 테스트는 이 패키지를 읽지 않는다.
 */
export function createGa4Reporter(config: Ga4Config): Ga4Reporter {
  const property = `properties/${config.propertyId}`
  let client: Promise<import('@google-analytics/data').BetaAnalyticsDataClient> | undefined
  const getClient = () =>
    (client ??= import('@google-analytics/data').then(
      ({ BetaAnalyticsDataClient }) =>
        new BetaAnalyticsDataClient({ credentials: config.credentials }),
    ))
  const options = { timeout: CALL_TIMEOUT_MS }

  return {
    async usersByEvent(input) {
      const ga4 = await getClient()
      const [response] = await ga4.runReport(usersByEventRequest(property, input), options)
      return parseUsersByEvent(response.rows, input)
    },
    async usersByDate(input) {
      const ga4 = await getClient()
      const [response] = await ga4.runReport(usersByDateRequest(property, input), options)
      return parseUsersByDate(response.rows, input)
    },
    async usersByDimension(input) {
      const ga4 = await getClient()
      const [response] = await ga4.runReport(usersByDimensionRequest(property, input), options)
      return parseUsersByDimension(response.rows)
    },
    async realtime({ topScreens }) {
      const ga4 = await getClient()
      const [[total], [screens]] = await Promise.all([
        ga4.runRealtimeReport(realtimeTotalRequest(property), options),
        ga4.runRealtimeReport(realtimeScreensRequest(property, topScreens), options),
      ])
      return parseRealtime(total.rows, screens.rows)
    },
  }
}
