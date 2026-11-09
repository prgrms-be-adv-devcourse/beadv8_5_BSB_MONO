'use client'

import {
  Children,
  useCallback,
  useEffect,
  useRef,
  useState,
  type MouseEvent,
  type PointerEvent,
  type ReactNode,
} from 'react'

import { cn } from '@/shared/lib'

import { FOCUS_RING } from '../focus-ring'

export type CarouselProps = {
  /** 화면 읽기 프로그램이 읽는 이름 (예: 추천 대회) */
  label: string
  /** 한 장씩 CarouselSlide로 넣는다. */
  children: ReactNode
  /** 자동으로 넘어가는 간격(ms). false면 자동으로 넘기지 않는다. */
  autoPlayMs?: number | false
  className?: string
}

// 홈 배너 캐러셀(피그마 v2 메인 배너). 한 장이 좌우 16 여백 안을 채우고, 손가락·트랙패드로 넘긴다(CSS scroll-snap).
// 데스크톱도 모바일과 같은 모양이라 화살표는 없고, 점을 누르거나 마우스로 잡고 끌어 이동한다.
// 자동 넘김만 마지막 장 다음에 첫 장으로 되감고, 끌기·손가락으로는 양 끝에서 멈춘다.
// 끝없이 이어지는 순환(양 끝 장 복제)은 쓰지 않기로 했다.

// 이만큼(px) 움직여야 끌기로 본다. 그보다 적으면 배너 클릭이다.
const DRAG_START = 5
// 한 장 폭의 이 비율 넘게 끌면 다음(이전) 장으로 넘긴다.
const DRAG_FLIP = 0.15
export function Carousel({ label, children, autoPlayMs = 5000, className }: CarouselProps) {
  const slides = Children.toArray(children)
  const count = slides.length
  const trackRef = useRef<HTMLDivElement>(null)
  const [index, setIndex] = useState(0)
  const [paused, setPaused] = useState(false)

  const drag = useRef<{ x: number; scroll: number; moved: boolean } | null>(null)
  const [dragging, setDragging] = useState(false)

  // 한 장이 차지하는 가로 길이(장 폭 + 간격)
  const slideStep = useCallback(() => {
    const track = trackRef.current
    const first = track?.children[0] as HTMLElement | undefined
    const second = track?.children[1] as HTMLElement | undefined
    if (!first) {
      return 0
    }
    return second ? second.offsetLeft - first.offsetLeft : first.offsetWidth
  }, [])

  const scrollToIndex = useCallback(
    (next: number) => {
      const track = trackRef.current
      const slide = track?.children[next] as HTMLElement | undefined
      if (!track || !slide) {
        return
      }
      setIndex(next)
      // 트랙 좌우 여백과 장 사이 간격이 같아서, n번째 장이 제자리에 오는 위치는 n × 한 장 길이다.
      track.scrollTo({ left: next * slideStep(), behavior: 'smooth' })
    },
    [slideStep],
  )

  // 손으로 넘겼을 때 지금 장 번호를 맞춘다.
  function handleScroll() {
    const track = trackRef.current
    const step = slideStep()
    if (!track || !step) {
      return
    }
    setIndex(Math.min(count - 1, Math.max(0, Math.round(track.scrollLeft / step))))
  }

  // 마우스로 잡고 끌기. 손가락·트랙패드는 브라우저 스크롤이 이미 해 주므로 마우스만 다룬다.
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
      handlePointerUp(event)
      return
    }
    const dx = event.clientX - start.x
    if (!start.moved) {
      if (Math.abs(dx) < DRAG_START) {
        return
      }
      // 움직이기 시작한 뒤에야 잡는다. 처음부터 잡으면 배너 링크 클릭이 트랙으로 빼앗긴다.
      start.moved = true
      track.setPointerCapture(event.pointerId)
      setDragging(true)
    }
    track.scrollLeft = start.scroll - dx
  }

  function handlePointerUp(event: PointerEvent<HTMLDivElement>) {
    const start = drag.current
    if (!start) {
      return
    }
    drag.current = null
    if (!start.moved) {
      return
    }
    // 클릭 막기(handleClickCapture)가 이 값을 봐야 해서 moved는 다음 클릭까지 남겨 둔다.
    dragMoved.current = true
    setDragging(false)
    const dx = event.clientX - start.x
    const step = slideStep()
    const from = step ? Math.round(start.scroll / step) : index
    const flip = step && Math.abs(dx) > step * DRAG_FLIP ? (dx < 0 ? 1 : -1) : 0
    scrollToIndex(Math.min(count - 1, Math.max(0, from + flip)))
  }

  const dragMoved = useRef(false)
  // 끌고 나서 손을 뗄 때 생기는 클릭은 배너를 연 것으로 치지 않는다.
  function handleClickCapture(event: MouseEvent) {
    if (dragMoved.current) {
      event.preventDefault()
      event.stopPropagation()
      dragMoved.current = false
    }
  }

  useEffect(() => {
    if (autoPlayMs === false || paused || count < 2) {
      return
    }
    // 움직임 줄이기를 켠 사용자에게는 자동으로 넘기지 않는다.
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      return
    }
    const timer = window.setInterval(() => scrollToIndex((index + 1) % count), autoPlayMs)
    return () => window.clearInterval(timer)
  }, [autoPlayMs, paused, count, index, scrollToIndex])

  // 일시정지 버튼은 없다. 대신 마우스를 올리거나, 만지거나, 키보드로 들어오면 멈춘다.
  const pause = () => setPaused(true)
  const resume = () => setPaused(false)

  return (
    <section
      aria-roledescription="carousel"
      aria-label={label}
      className={cn('flex flex-col items-center gap-3', className)}
      onPointerEnter={pause}
      onPointerLeave={resume}
      onTouchStart={pause}
      onTouchEnd={resume}
      onFocus={pause}
      onBlur={resume}
    >
      <div
        ref={trackRef}
        onScroll={handleScroll}
        onPointerDown={handlePointerDown}
        onPointerMove={handlePointerMove}
        onPointerUp={handlePointerUp}
        onPointerCancel={handlePointerUp}
        onClickCapture={handleClickCapture}
        // 이미지·링크를 브라우저 기본 끌기(파일 끌기)로 집어 가지 않게 막는다.
        onDragStart={(event) => event.preventDefault()}
        className={cn(
          // 장이 좌우 16 여백 안쪽 폭을 채우고 장 사이도 16이라, 넘기면 다음 장이 같은 자리에 온다.
          'flex w-full [scrollbar-width:none] gap-4 overflow-x-auto px-4 [&::-webkit-scrollbar]:hidden',
          // 끄는 동안 snap이 켜져 있으면 장 경계로 계속 끌려가서 끈다.
          dragging ? 'cursor-grabbing select-none' : 'cursor-grab snap-x snap-mandatory',
        )}
      >
        {slides.map((slide, i) => (
          <div
            key={i}
            role="group"
            aria-roledescription="slide"
            aria-label={`${i + 1} / ${count}`}
            className="relative w-full shrink-0 snap-center"
          >
            {slide}
            <span
              aria-hidden
              className="absolute right-5 bottom-4.5 text-caption-regular text-text-on-image"
            >
              {i + 1} / {count}
            </span>
          </div>
        ))}
      </div>
      {count > 1 && (
        <div className="flex items-center gap-1.5">
          {slides.map((_, i) => (
            <button
              key={i}
              type="button"
              aria-label={`${i + 1}번째 배너 보기`}
              aria-current={i === index}
              onClick={() => scrollToIndex(i)}
              className={cn(
                'h-1.5 rounded-full transition-[width,background-color]',
                i === index ? 'w-4.5 bg-bg-inverse' : 'w-1.5 bg-bg-control-off',
                FOCUS_RING,
              )}
            />
          ))}
        </div>
      )}
    </section>
  )
}

export type CarouselSlideProps = {
  /** 배너 이미지. next/image에 fill과 object-cover를 줘서 넣는다. */
  image: ReactNode
  /** 제목 위 작은 칩 (예: 이번 주 접수 마감) */
  chip?: ReactNode
  /** 없으면 글자를 그리지 않는다(이미지에 글이 박힌 배너). */
  title?: ReactNode
  /** 예: 10/31까지 · 5K · 10K · 서울숲 일대 */
  description?: ReactNode
}

// 피그마 v2 메인 배너 한 장. 모든 배너에 같은 어두운 막을 덮고, 아래쪽에 흰 글자를 놓는다.
// 이미지 위 반투명 색은 피그마에도 변수 없이 쓴 값이다.
export function CarouselSlide({ image, chip, title, description }: CarouselSlideProps) {
  return (
    <div className="relative aspect-568/300 overflow-hidden rounded-lg bg-bg-subtle">
      {image}
      <div aria-hidden className="absolute inset-0 bg-[rgb(10_15_28/0.55)]" />
      {title && (
        <div className="absolute inset-x-5 bottom-4.5 flex flex-col items-start gap-1.5 text-text-on-image">
          {chip && (
            <span className="rounded-sm bg-[rgb(255_255_255/0.2)] px-2 py-0.75 text-caption-medium">
              {chip}
            </span>
          )}
          <p className="text-title2 break-keep">{title}</p>
          {description && <p className="text-body2-regular opacity-80">{description}</p>}
        </div>
      )}
    </div>
  )
}
