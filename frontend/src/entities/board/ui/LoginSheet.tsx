'use client'

import Link from 'next/link'

import { BottomSheet, buttonClassName } from '@/shared/ui'

export type LoginSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 로그인 뒤 돌아올 경로 */
  next: string
}

// 피그마 M34-2 · M35-6 로그인 안내 시트. 로그인하지 않고 글쓰기·좋아요·댓글을 눌렀을 때 쓴다.
export function LoginSheet({ open, onOpenChange, next }: LoginSheetProps) {
  return (
    <BottomSheet
      open={open}
      onOpenChange={onOpenChange}
      title="로그인이 필요해요"
      footer={
        <div className="flex flex-col gap-2">
          <Link
            href={`/login?next=${encodeURIComponent(next)}` as '/login'}
            className={buttonClassName({ size: 'l' })}
          >
            로그인
          </Link>
          <Link href="/signup" className={buttonClassName({ variant: 'ghost', size: 'l' })}>
            회원가입
          </Link>
        </div>
      }
    >
      {null}
    </BottomSheet>
  )
}
