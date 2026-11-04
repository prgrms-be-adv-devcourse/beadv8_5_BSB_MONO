// GA4 Data API 연결 정보. 서버 환경 변수에서만 읽는다(NEXT_PUBLIC_ 없음 → 브라우저 번들에 들어가지 않는다).
// 키 값은 로그·오류 문구에 절대 넣지 않는다.

export type Ga4Credentials = { client_email: string; private_key: string }

export type Ga4Config = { propertyId: string; credentials: Ga4Credentials }

export type Ga4ConfigResult =
  | { status: 'ready'; config: Ga4Config }
  | { status: 'missing' }
  /** 값은 있는데 형식이 틀렸다. 어느 변수가 틀렸는지만 알린다 */
  | { status: 'invalid'; reason: string }

/**
 * GA4_PROPERTY_ID는 숫자 속성 ID다. 측정 ID(G-로 시작)를 넣으면 invalid.
 * GA4_SERVICE_ACCOUNT_KEY는 서비스 계정 키 JSON 한 줄, 또는 그 JSON을 base64로 바꾼 한 줄을 받는다.
 * base64를 권한다: JSON의 따옴표·줄바꿈(\n)이 .env와 compose에서 깨지지 않는다.
 */
export function readGa4Config(env: {
  GA4_PROPERTY_ID?: string
  GA4_SERVICE_ACCOUNT_KEY?: string
}): Ga4ConfigResult {
  const propertyId = env.GA4_PROPERTY_ID?.trim()
  const rawKey = env.GA4_SERVICE_ACCOUNT_KEY?.trim()
  if (!propertyId || !rawKey) return { status: 'missing' }
  if (!/^\d+$/.test(propertyId)) {
    return { status: 'invalid', reason: 'GA4_PROPERTY_ID는 숫자로 된 속성 ID여야 합니다.' }
  }
  const credentials = parseKey(rawKey)
  if (!credentials) {
    return {
      status: 'invalid',
      reason: 'GA4_SERVICE_ACCOUNT_KEY를 서비스 계정 키 JSON으로 읽지 못했습니다.',
    }
  }
  return { status: 'ready', config: { propertyId, credentials } }
}

function parseKey(raw: string): Ga4Credentials | null {
  const json = raw.startsWith('{') ? raw : Buffer.from(raw, 'base64').toString('utf8')
  try {
    const key: unknown = JSON.parse(json)
    if (
      typeof key === 'object' &&
      key !== null &&
      'client_email' in key &&
      'private_key' in key &&
      typeof key.client_email === 'string' &&
      typeof key.private_key === 'string'
    ) {
      return { client_email: key.client_email, private_key: key.private_key }
    }
  } catch {
    // 형식 오류 내용에 키 일부가 들어갈 수 있어 버린다
  }
  return null
}
