import Link from 'next/link'

import { buttonClassName, EmptyState } from '@/shared/ui'

// 피그마 세미 · 판매자 · 관리자 화면 · 권한 없음
// TODO(auth): proxy.ts에서 판매자가 아닌 회원을 여기로 보낸다
export function ForbiddenPage() {
  return (
    <main className="flex flex-1 items-center justify-center py-20">
      <EmptyState
        title="판매자만 쓸 수 있는 화면이에요"
        description="판매자 등록을 신청하고 관리자 승인을 받으면 열려요."
        className="max-w-90"
        action={
          <Link href="/seller-apply" className={buttonClassName({ variant: 'secondary' })}>
            판매자 등록 신청
          </Link>
        }
      />
    </main>
  )
}
