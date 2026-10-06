import { act, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { createStore } from 'zustand/vanilla'

import { createStoreContext } from './create-store-context'

type CounterState = { count: number; increment: () => void }

const { Provider, useStore } = createStoreContext('Counter', (initial: number) =>
  createStore<CounterState>()((set) => ({
    count: initial,
    increment: () => set((s) => ({ count: s.count + 1 })),
  })),
)

function Counter({ testId }: { testId: string }) {
  const count = useStore((s) => s.count)
  const increment = useStore((s) => s.increment)
  return (
    <button data-testid={testId} onClick={increment}>
      {count}
    </button>
  )
}

describe('createStoreContext', () => {
  it('Provider마다 독립된 스토어를 만든다', () => {
    render(
      <>
        <Provider initialState={1}>
          <Counter testId="a" />
        </Provider>
        <Provider initialState={10}>
          <Counter testId="b" />
        </Provider>
      </>,
    )

    act(() => screen.getByTestId('a').click())

    expect(screen.getByTestId('a')).toHaveTextContent('2')
    expect(screen.getByTestId('b')).toHaveTextContent('10')
  })

  it('Provider 밖에서 쓰면 에러를 던진다', () => {
    expect(() => render(<Counter testId="x" />)).toThrow(/CounterProvider/)
  })
})
