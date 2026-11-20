import type { Roi } from './cert-reader'

/** 이보다 작은 네모는 실수로 누른 것으로 보고 사진 전체를 읽는다 (원본 픽셀) */
const MIN_SIDE = 32

/**
 * 화면에서 끈 두 점(화면 픽셀) → 원본 픽셀 네모. 화면 크기 대비 원본 크기 비율로 늘리고 사진 밖은 자른다.
 * 너무 작으면 사진 전체.
 */
export function toRoi(
  a: { x: number; y: number },
  b: { x: number; y: number },
  shown: { width: number; height: number },
  natural: { width: number; height: number },
): Roi {
  const sx = natural.width / shown.width
  const sy = natural.height / shown.height
  const clampX = (v: number) => Math.min(Math.max(Math.round(v * sx), 0), natural.width)
  const clampY = (v: number) => Math.min(Math.max(Math.round(v * sy), 0), natural.height)
  const x0 = clampX(Math.min(a.x, b.x))
  const x1 = clampX(Math.max(a.x, b.x))
  const y0 = clampY(Math.min(a.y, b.y))
  const y1 = clampY(Math.max(a.y, b.y))
  if (x1 - x0 < MIN_SIDE || y1 - y0 < MIN_SIDE) return wholeImage(natural)
  return { x: x0, y: y0, width: x1 - x0, height: y1 - y0 }
}

export const wholeImage = (natural: { width: number; height: number }): Roi => ({
  x: 0,
  y: 0,
  width: natural.width,
  height: natural.height,
})
