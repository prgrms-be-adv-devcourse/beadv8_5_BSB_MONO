import { cn } from '@/shared/lib'

// 제공자 로그인 디자인 가이드 색(카카오 #FEE500 바탕 · 구글 4색 G)을 그대로 쓴다. 로그인 원형 버튼과 같은 그림이다.
export type SocialMarkProvider = 'KAKAO' | 'GOOGLE'

export function SocialMark({
  provider,
  className,
}: {
  provider: SocialMarkProvider
  className?: string
}) {
  if (provider === 'KAKAO') {
    return (
      <span
        aria-hidden
        className={cn(
          'flex shrink-0 items-center justify-center rounded-full bg-[#FEE500]',
          className,
        )}
      >
        <svg viewBox="0 0 24 24" className="size-[60%]">
          <path
            fill="#191919"
            d="M12 3C6.48 3 2 6.58 2 10.99c0 2.85 1.86 5.35 4.67 6.77l-.95 3.48c-.08.3.26.54.52.37l4.15-2.76c.53.07 1.07.11 1.61.11 5.52 0 10-3.58 10-7.97C22 6.58 17.52 3 12 3z"
          />
        </svg>
      </span>
    )
  }
  return (
    <span aria-hidden className={cn('flex shrink-0 items-center justify-center', className)}>
      <svg viewBox="0 0 48 48" className="size-full">
        <path
          fill="#EA4335"
          d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
        />
        <path
          fill="#4285F4"
          d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
        />
        <path
          fill="#FBBC05"
          d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
        />
        <path
          fill="#34A853"
          d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
        />
      </svg>
    </span>
  )
}
