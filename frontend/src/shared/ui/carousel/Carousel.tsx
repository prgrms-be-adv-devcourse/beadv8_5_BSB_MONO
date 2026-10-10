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

// 홈 배너 캐러셀. 지금 장이 가운데 오고 양옆 장이 살짝 보이며, 손가락·트랙패드로 넘긴다(CSS scroll-snap).
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
      // 트랙 좌우 여백이 (트랙 폭 - 장 폭) / 2라서, n번째 장이 가운데 오는 위치는 n × 한 장 길이다.
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
      className={cn('flex flex-col items-center gap-3 pt-4 pb-2', className)}
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
          // 좌우 여백 6.25%씩 두고 장이 안쪽 폭을 채우면, 장 폭이 전체의 87.5%가 되고 어느 장이든 가운데에 온다.
          'flex w-full [scrollbar-width:none] gap-2.5 overflow-x-auto px-[6.25%] [&::-webkit-scrollbar]:hidden',
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
            // 시안 420/480 폭. 트랙 안쪽 폭(전체의 87.5%)을 꽉 채우고, 양옆에 이전·다음 장이 살짝 보인다.
            className="relative w-full shrink-0 snap-center"
          >
            {slide}
            <span
              aria-hidden
              // 이미지 위 반투명 검정은 피그마에도 변수 없이 쓴 값이다.
              className="absolute right-3.5 bottom-4 rounded-full bg-[rgb(0_0_0/0.4)] px-2.5 py-0.75 text-label-s text-text-on-hero"
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
  /** 없으면 글자와 아래 그림자를 그리지 않는다(이미지에 글이 박힌 배너). */
  title?: ReactNode
  /** 예: #함께뛰자 #5K #10K */
  tags?: ReactNode
}

// 배너 한 장. 10:7 비율, 아래쪽 그림자 위에 흰 글자.
export function CarouselSlide({ image, title, tags }: CarouselSlideProps) {
  return (
    // 둥글기 20은 피그마 배너 값 그대로다(radius 토큰에 없음).
    <div className="relative aspect-[10/7] overflow-hidden rounded-[20px] bg-bg-subtle">
      {image}
      {title && (
        <>
          <div
            aria-hidden
            className="absolute inset-x-0 bottom-0 h-[58%] bg-linear-to-b from-[rgb(0_0_0/0)] to-[rgb(0_0_0/0.55)]"
          />
          <div className="absolute inset-x-5.5 bottom-5.5 flex flex-col gap-3 text-text-on-hero">
            <p className="text-heading-xl break-keep whitespace-pre-line">{title}</p>
            {/* 태그 사이 두 칸 띄어쓰기를 살린다. */}
            {tags && <p className="text-label-m whitespace-pre-wrap">{tags}</p>}
          </div>
        </>
      )}
    </div>
  )
}
