import { getServerEnv } from '@/shared/server-env'

import { createGa4Reporter } from './client'
import { readGa4Config } from './config'
import type { Ga4Reporter } from './report'

export type Ga4ReporterResult =
  | { status: 'ready'; reporter: Ga4Reporter }
  /** 키가 없다(로컬 개발 등). 화면에 「GA4 연결 정보가 없습니다」를 보인다 */
  | { status: 'notConfigured' }
  /** 값의 형식이 틀렸다. 서버 로그에 어느 변수인지만 남기고 화면은 「불러오지 못함」 */
  | { status: 'invalid' }

let cached: { key: string; reporter: Ga4Reporter } | undefined

/** 가짜 응답 모드면 예시 데이터, 아니면 환경 변수의 키로 실제 GA4를 부른다. */
export async function getGa4Reporter(): Promise<Ga4ReporterResult> {
  const env = getServerEnv()
  if (env.API_MOCKING === 'on') {
    const { mockGa4Reporter } = await import('@/shared/mocks/ga4-stats')
    return { status: 'ready', reporter: mockGa4Reporter }
  }

  const result = readGa4Config(env)
  if (result.status === 'missing') return { status: 'notConfigured' }
  if (result.status === 'invalid') {
    console.error(`[ga4] ${result.reason}`)
    return { status: 'invalid' }
  }
  // 키가 같으면 gRPC 연결을 다시 만들지 않는다
  const key = `${result.config.propertyId}:${result.config.credentials.client_email}`
  if (cached?.key !== key) cached = { key, reporter: createGa4Reporter(result.config) }
  return { status: 'ready', reporter: cached.reporter }
}
