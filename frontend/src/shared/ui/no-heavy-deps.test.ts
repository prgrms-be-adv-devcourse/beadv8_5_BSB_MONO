import { describe, expect, it, vi } from 'vitest'

// @/shared/api · @/shared/config · @/shared/ui는 거의 모든 화면이 받는다.
// 안쪽 어느 파일이 zod나 달력(react-day-picker)을 가져와도 여기서 실패해, 모든 화면에 다시 실리는 것을 막는다.
vi.mock('zod', () => {
  throw new Error('공용 묶음이 zod를 불러왔어요')
})
vi.mock('react-day-picker', () => {
  throw new Error('공용 묶음이 달력(react-day-picker)을 불러왔어요')
})

describe('모든 화면이 받는 공용 묶음', () => {
  it('zod와 달력 없이 불러온다', async () => {
    await expect(import('@/shared/api')).resolves.toBeDefined()
    await expect(import('@/shared/config')).resolves.toBeDefined()
    await expect(import('@/shared/ui')).resolves.toBeDefined()
  })
})
