import { describe, expect, it, vi } from 'vitest'

import Page from './page'

vi.mock('next/navigation', () => ({
  notFound: () => {
    throw new Error('NEXT_NOT_FOUND')
  },
}))

describe('알림 상세 주소', () => {
  it('없는 알림 종류면 찾을 수 없음(404)으로 보낸다', async () => {
    await expect(
      Page({ params: Promise.resolve({ category: 'abc' }), searchParams: Promise.resolve({}) }),
    ).rejects.toThrow('NEXT_NOT_FOUND')
  })
})
