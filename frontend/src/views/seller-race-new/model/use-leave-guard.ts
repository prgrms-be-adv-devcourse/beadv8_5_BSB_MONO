'use client'

import type { Route } from 'next'
import { useRouter } from 'next/navigation'
import { useEffect, useState } from 'react'

type Target = { href: string } | { back: true }

/**
 * 작성 중에 화면을 떠나려 하면 막고 확인 창을 띄운다 (enabled일 때만).
 * Next App Router에는 이동을 막는 이벤트가 없어서 세 곳을 본다.
 * - 같은 사이트 링크 클릭(사이드바 메뉴 등): 캡처 단계에서 막고 가려던 주소를 들고 있는다
 * - 뒤로 가기: 같은 주소를 한 번 더 쌓아 두었다가, 뒤로 가면 다시 쌓고 창을 띄운다
 * - 새로 고침 · 탭 닫기: 브라우저 기본 경고(beforeunload)
 */
export function useLeaveGuard(enabled: boolean) {
  const router = useRouter()
  const [target, setTarget] = useState<Target | null>(null)
  // 나가기를 누른 뒤에는 이동을 다시 막지 않는다
  const [released, setReleased] = useState(false)
  const active = enabled && !released

  useEffect(() => {
    if (!active) return
    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0) return
      if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return
      const anchor = (event.target as Element | null)?.closest?.(
        'a[href]',
      ) as HTMLAnchorElement | null
      if (!anchor || anchor.target === '_blank' || anchor.hasAttribute('download')) return
      const url = new URL(anchor.href, window.location.href)
      const here = window.location
      if (url.origin !== here.origin) return
      if (url.pathname === here.pathname && url.search === here.search) return
      event.preventDefault()
      event.stopPropagation()
      setTarget({ href: `${url.pathname}${url.search}${url.hash}` })
    }
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    const onPopState = () => {
      window.history.pushState(window.history.state, '', window.location.href)
      setTarget({ back: true })
    }
    window.history.pushState(window.history.state, '', window.location.href)
    document.addEventListener('click', onClick, true)
    window.addEventListener('beforeunload', onBeforeUnload)
    window.addEventListener('popstate', onPopState)
    return () => {
      document.removeEventListener('click', onClick, true)
      window.removeEventListener('beforeunload', onBeforeUnload)
      window.removeEventListener('popstate', onPopState)
    }
  }, [active])

  return {
    /** 확인 창을 띄울 때 true */
    leaving: target !== null,
    /** 계속 쓰기: 창만 닫는다 */
    stay: () => setTarget(null),
    /** 나가기: 가려던 곳으로 간다. 뒤로 가기였다면 우리가 쌓아 둔 한 칸까지 두 칸 되돌아간다 */
    leave: () => {
      const next = target
      setReleased(true)
      setTarget(null)
      if (!next) return
      if ('href' in next) router.push(next.href as Route)
      else window.history.go(-2)
    },
  }
}
