import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { completePostcode, installPostcodeFake, postcodeFake } from '@/shared/testing'

import { ADDRESS_SEARCH_UNAVAILABLE, AddressSearch } from './AddressSearch'

vi.mock('next/script', async () => ({ default: (await import('@/shared/testing')).FakeScript }))

beforeEach(() => installPostcodeFake())

function renderSearch() {
  const onSelect = vi.fn()
  const onUnavailable = vi.fn()
  render(<AddressSearch onSelect={onSelect} onUnavailable={onUnavailable} />)
  return { onSelect, onUnavailable }
}

describe('AddressSearch', () => {
  it('누르기 전에는 카카오 창을 만들지 않는다', () => {
    renderSearch()
    expect(postcodeFake.options).toBeUndefined()
  })

  it('주소 검색을 누르면 화면 안 칸에 창을 붙이고, 시·도는 줄이지 않은 이름으로 받는다', async () => {
    renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(postcodeFake.embeddedIn).toBeInTheDocument()
    // 「서울」이 아니라 「서울특별시」로 받아야 대회 찾기 지역 필터와 글자가 같다
    expect(postcodeFake.options?.shorthand).toBe(false)
  })

  it('도로명 주소를 고르면 우편번호 · 도로명 주소 · 시·도 · 시·군·구를 넘기고 창을 닫는다', async () => {
    const { onSelect } = renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode()

    expect(onSelect).toHaveBeenCalledWith({
      zipcode: '04768',
      address: '서울특별시 성동구 뚝섬로 273',
      sido: '서울특별시',
      sigungu: '성동구',
    })
    expect(screen.queryByRole('button', { name: '주소 검색 닫기' })).not.toBeInTheDocument()
  })

  it('지번 주소를 골라도 배송에 쓰는 도로명 주소를 넘긴다', async () => {
    const { onSelect } = renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode({ userSelectedType: 'J' })

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ address: '서울특별시 성동구 뚝섬로 273' }),
    )
  })

  it('도로명 주소가 비어 있으면 자동으로 이어 준 도로명 주소, 그것도 없으면 지번 주소를 넘긴다', async () => {
    const { onSelect } = renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode({ roadAddress: '', autoRoadAddress: '서울특별시 성동구 왕십리로 1' })
    expect(onSelect).toHaveBeenLastCalledWith(
      expect.objectContaining({ address: '서울특별시 성동구 왕십리로 1' }),
    )

    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    completePostcode({ roadAddress: '', autoRoadAddress: '' })
    expect(onSelect).toHaveBeenLastCalledWith(
      expect.objectContaining({ address: '서울특별시 성동구 성수동1가 678-1' }),
    )
  })

  it('고르지 않고 닫으면 아무것도 넘기지 않는다', async () => {
    const { onSelect } = renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))
    await userEvent.click(screen.getByRole('button', { name: '주소 검색 닫기' }))

    expect(screen.queryByRole('button', { name: '주소 검색 닫기' })).not.toBeInTheDocument()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('스크립트를 못 불러오면 직접 입력하라고 알리고 onUnavailable을 부른다', async () => {
    postcodeFake.failLoad = true
    const { onSelect, onUnavailable } = renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(await screen.findByText(ADDRESS_SEARCH_UNAVAILABLE)).toBeInTheDocument()
    expect(onUnavailable).toHaveBeenCalled()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('불러오기에 실패하면 주소 검색 버튼을 숨겨 빈 창을 다시 열지 않는다', async () => {
    postcodeFake.failLoad = true
    renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(await screen.findByText(ADDRESS_SEARCH_UNAVAILABLE)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '주소 검색' })).not.toBeInTheDocument()
  })

  it('스크립트는 불렀는데 카카오 창이 없으면(차단 등) 불러오기 실패로 본다', async () => {
    Reflect.deleteProperty(window, 'kakao')
    const { onUnavailable } = renderSearch()
    await userEvent.click(screen.getByRole('button', { name: '주소 검색' }))

    expect(await screen.findByText(ADDRESS_SEARCH_UNAVAILABLE)).toBeInTheDocument()
    expect(onUnavailable).toHaveBeenCalled()
  })
})
