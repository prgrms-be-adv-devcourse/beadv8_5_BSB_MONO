'use client'

import { usePathname } from 'next/navigation'
import { useEffect } from 'react'

import { syncClarity } from './clarity'

/** 루트 레이아웃에 한 번 둔다. 화면을 옮길 때마다 어드민인지 보고 Clarity를 넣거나 멈춘다 */
export function ClarityTag({ id }: { id: string }) {
  const pathname = usePathname()
  useEffect(() => syncClarity(id, pathname), [id, pathname])
  return null
}
