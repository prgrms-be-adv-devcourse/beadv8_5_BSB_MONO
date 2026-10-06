'use client'

import { createContext, useContext, useState, type ReactNode } from 'react'
import { useStore as useZustandStore, type StoreApi } from 'zustand'

type ProviderProps<I> = [I] extends [void]
  ? { children: ReactNode }
  : { children: ReactNode; initialState: I }

/**
 * 요청(렌더 트리)마다 새 스토어를 만드는 Zustand 패턴.
 * 모듈 전역 스토어는 서버에서 여러 사용자의 요청이 공유하므로 쓰지 않는다.
 * 서버 데이터는 TanStack Query에 두고, 여기에는 UI 상태만 둔다.
 *
 * @example
 * const { Provider: CheckoutStepProvider, useStore: useCheckoutStep } =
 *   createStoreContext('CheckoutStep', () => createStore<State>()((set) => ({ ... })))
 */
export function createStoreContext<S, I = void>(
  name: string,
  createStore: (initialState: I) => StoreApi<S>,
) {
  const StoreContext = createContext<StoreApi<S> | null>(null)

  function Provider(props: ProviderProps<I>) {
    const [store] = useState(() =>
      createStore(('initialState' in props ? props.initialState : undefined) as I),
    )
    return <StoreContext.Provider value={store}>{props.children}</StoreContext.Provider>
  }
  Provider.displayName = `${name}Provider`

  function useStore<T>(selector: (state: S) => T): T {
    const store = useContext(StoreContext)
    if (!store) throw new Error(`use${name}Store는 ${name}Provider 안에서만 쓸 수 있습니다.`)
    return useZustandStore(store, selector)
  }

  return { Provider, useStore }
}
