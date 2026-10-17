'use client'

import { useRef, useState, type MouseEvent, type PointerEvent, type ReactNode } from 'react'

import { cn } from '@/shared/lib'

export type DragScrollProps = {
  children: ReactNode
  className?: string
}

// 이만큼(px) 움직여야 끌기로 본다. 그보다 적으면 카드 클릭이다.
const DRAG_START = 5

// 가로로 넘기는 한 줄. 손가락·트랙패드는 브라우저 스크롤이 하고, 마우스는 잡고 끌어 넘긴다.
// 끌고 난 뒤 손을 뗄 때 생기는 클릭은 카드를 연 것으로 치지 않는다.
export function DragScroll({ children, className }: DragScrollProps) {
  const trackRef = useRef<HTMLDivElement>(null)
  const drag = useRef<{ x: number; scroll: number; moved: boolean } | null>(null)
  const dragMoved = useRef(false)
  const [dragging, setDragging] = useState(false)

  function handlePointerDown(event: PointerEvent<HTMLDivElement>) {
    if (event.pointerType !== 'mouse' || event.button !== 0 || !trackRef.current) {
      return
    }
    drag.current = { x: event.clientX, scroll: trackRef.current.scrollLeft, moved: false }
  }

  function handlePointerMove(event: PointerEvent<HTMLDivElement>) {
    const start = drag.current
    const track = trackRef.current
    if (!start || !track) {
      return
    }
    // 트랙 밖에서 버튼을 떼면 pointerup이 오지 않는다. 버튼이 이미 떼어졌으면 끌기를 끝낸다.
    if ((event.buttons & 1) === 0) {
      handlePointerUp()
      return
    }
    const dx = event.clientX - start.x
    if (!start.moved) {
      if (Math.abs(dx) < DRAG_START) {
        return
      }
      // 움직이기 시작한 뒤에야 잡는다. 처음부터 잡으면 카드 링크 클릭이 트랙으로 빼앗긴다.
      start.moved = true
      track.setPointerCapture(event.pointerId)
      setDragging(true)
    }
    track.scrollLeft = start.scroll - dx
  }

  function handlePointerUp() {
    const start = drag.current
    drag.current = null
    if (start?.moved) {
      dragMoved.current = true
      setDragging(false)
    }
  }

  function handleClickCapture(event: MouseEvent) {
    if (dragMoved.current) {
      event.preventDefault()
      event.stopPropagation()
      dragMoved.current = false
    }
  }

  return (
    <div
      ref={trackRef}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerCancel={handlePointerUp}
      onClickCapture={handleClickCapture}
      // 이미지·링크를 브라우저 기본 끌기(파일 끌기)로 집어 가지 않게 막는다.
      onDragStart={(event) => event.preventDefault()}
      className={cn(
        'flex [scrollbar-width:none] overflow-x-auto [&::-webkit-scrollbar]:hidden',
        // 끄는 동안 snap이 켜져 있으면 카드 경계로 계속 끌려가서 끈다.
        dragging ? 'cursor-grabbing select-none' : 'cursor-grab snap-x snap-proximity',
        className,
      )}
    >
      {children}
    </div>
  )
}
