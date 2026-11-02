// GA4 Data API는 서비스 계정 키를 쓰므로 서버에서만 불러온다. 브라우저 코드가 import하면 빌드가 멈춘다.
import 'server-only'

export { getGa4Reporter, type Ga4ReporterResult } from './reporter'
export type { Ga4DateRange, Ga4Reporter, RealtimeOverview, UsersByEvent } from './report'
