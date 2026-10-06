'use client'

import type { ImageLoaderProps } from 'next/image'

/**
 * NEXT_PUBLIC_IMAGE_CDN_URL이 있을 때만 next.config에서 연결되는 이미지 로더.
 * 서버(sharp) 대신 CDN이 리사이즈하도록 넘긴다.
 * 쿼리 형식(w, q)은 붙일 CDN(예: CloudFront + 이미지 리사이즈 함수)에 맞춰 바꾼다.
 */
export default function cdnLoader({ src, width, quality }: ImageLoaderProps) {
  const base = process.env.NEXT_PUBLIC_IMAGE_CDN_URL
  // S3 전체 URL이 와도 경로만 떼서 CDN 기준으로 다시 붙인다.
  const path = /^https?:\/\//.test(src) ? new URL(src).pathname : src
  const url = new URL(path.replace(/^\/+/, ''), `${base?.replace(/\/+$/, '')}/`)
  url.searchParams.set('w', String(width))
  url.searchParams.set('q', String(quality ?? 75))
  return url.toString()
}
