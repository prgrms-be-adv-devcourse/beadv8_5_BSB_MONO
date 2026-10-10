import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { Button } from './button/Button'
import { Carousel, CarouselSlide } from './carousel/Carousel'
import { Textarea } from './field/Textarea'
import { TextField } from './field/TextField'
import { pageSlots } from './pagination/Pagination'
import { ProgressBar } from './progress-bar/ProgressBar'
import { QuantityStepper } from './quantity-stepper/QuantityStepper'

describe('Button', () => {
  it('loading이면 클릭을 막는다', async () => {
    const onClick = vi.fn()
    render(
      <Button loading onClick={onClick}>
        결제하기
      </Button>,
    )
    await userEvent.click(screen.getByRole('button', { name: '결제하기' }))

    expect(onClick).not.toHaveBeenCalled()
    expect(screen.getByRole('button')).toHaveAttribute('aria-busy', 'true')
  })
})

describe('QuantityStepper', () => {
  function Controlled({ initial, max }: { initial: number; max?: number }) {
    const [value, setValue] = useState(initial)
    return <QuantityStepper value={value} onValueChange={setValue} max={max} />
  }

  it('최소(1)에서는 줄이기를, 최대에서는 늘리기를 막는다', async () => {
    render(<Controlled initial={1} max={2} />)
    const decrease = screen.getByRole('button', { name: '수량 줄이기' })
    const increase = screen.getByRole('button', { name: '수량 늘리기' })

    expect(decrease).toBeDisabled()
    await userEvent.click(increase)

    expect(screen.getByRole('status')).toHaveTextContent('2')
    expect(increase).toBeDisabled()
    expect(decrease).toBeEnabled()
  })
})

describe('pageSlots', () => {
  it('7쪽 이하는 모두 보여 준다', () => {
    expect(pageSlots(3, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('가운데 페이지는 앞뒤를 줄여 7칸으로 맞춘다', () => {
    expect(pageSlots(10, 20)).toEqual([1, 'ellipsis', 9, 10, 11, 'ellipsis', 20])
  })

  it('앞쪽·뒤쪽 페이지는 한쪽만 줄인다', () => {
    expect(pageSlots(2, 20)).toEqual([1, 2, 3, 4, 5, 'ellipsis', 20])
    expect(pageSlots(19, 20)).toEqual([1, 'ellipsis', 16, 17, 18, 19, 20])
  })
})

describe('TextField', () => {
  it('error가 있으면 helper 대신 오류 문구를 보여 주고 invalid로 표시한다', () => {
    render(<TextField label="참가자 이름" helper="주민등록상 이름" error="이름을 입력해 주세요" />)

    expect(screen.getByText('이름을 입력해 주세요')).toBeInTheDocument()
    expect(screen.queryByText('주민등록상 이름')).not.toBeInTheDocument()
    expect(screen.getByLabelText('참가자 이름')).toHaveAttribute('aria-invalid', 'true')
  })
})

describe('Textarea', () => {
  it('입력한 글자 수를 세어 보여 준다', async () => {
    render(<Textarea label="배송 메모" maxLength={200} showCounter />)
    await userEvent.type(screen.getByLabelText('배송 메모'), '경비실')

    expect(screen.getByText('3/200')).toBeInTheDocument()
  })
})

describe('ProgressBar', () => {
  it('값을 0~100으로 자른다', () => {
    render(<ProgressBar value={140} />)

    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
  })
})

describe('Carousel', () => {
  function renderCarousel(autoPlayMs: number | false = false) {
    Element.prototype.scrollTo = vi.fn()
    return render(
      <Carousel label="추천 대회" autoPlayMs={autoPlayMs}>
        <CarouselSlide image={null} title="첫째" />
        <CarouselSlide image={null} title="둘째" />
        <CarouselSlide image={null} />
      </Carousel>,
    )
  }

  it('장마다 순서를 붙이고, 점을 누르면 그 장이 지금 장이 된다', async () => {
    renderCarousel()

    expect(screen.getByRole('group', { name: '3 / 3' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '2번째 배너 보기' }))

    expect(screen.getByRole('button', { name: '2번째 배너 보기' })).toHaveAttribute(
      'aria-current',
      'true',
    )
    expect(Element.prototype.scrollTo).toHaveBeenCalled()
  })

  it('자동 넘김은 마지막 장 다음에 첫 장으로 돌아간다', () => {
    vi.useFakeTimers()
    renderCarousel(5000)
    const current = () =>
      screen.getAllByRole('button').findIndex((b) => b.getAttribute('aria-current') === 'true')

    // 장이 바뀔 때마다 다시 그려지도록 5초씩 나눠 흘려보낸다.
    const tick = () => act(() => vi.advanceTimersByTime(5000))
    tick()
    expect(current()).toBe(1)
    tick()
    tick()
    expect(current()).toBe(0)
    vi.useRealTimers()
  })
})
