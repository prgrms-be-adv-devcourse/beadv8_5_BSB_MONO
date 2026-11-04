import { describe, expect, it } from 'vitest'

import * as api from '@/shared/api'
import * as apiZod from '@/shared/api-zod'
import * as config from '@/shared/config'
import * as serverEnv from '@/shared/server-env'

// @/shared/api · @/shared/config는 거의 모든 화면이 받는다. zod를 쓰는 것은 따로 꺼내야 브라우저 묶음에 zod가 실리지 않는다
describe('zod를 쓰는 공개 경로', () => {
  it('@/shared/api에는 zod 스키마가 없고 @/shared/api-zod에 있다', () => {
    expect(Object.keys(api).filter((name) => name.endsWith('Zod'))).toEqual([])
    expect(Object.keys(apiZod).sort()).toEqual([
      'cashZod',
      'fileZod',
      'memberZod',
      'orderZod',
      'payoutZod',
      'productZod',
      'queueZod',
    ])
  })

  it('@/shared/config에는 서버 환경변수 검사가 없고 @/shared/server-env에 있다', () => {
    expect(config).not.toHaveProperty('getServerEnv')
    expect(serverEnv.getServerEnv).toBeTypeOf('function')
  })
})
