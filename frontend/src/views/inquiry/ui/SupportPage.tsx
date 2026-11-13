import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon } from '@/shared/ui'

// 피그마 문의 · M41 고객센터. 참가 · 결제 문의만 연다(대회 문의는 대회 상세에서). 보낸 문의는 「내 문의」에서 본다
export function SupportPage() {
  return (
    <>
      <AppHeader type="sub" title="고객센터" backHref="/me" />
      <main className="flex flex-1 flex-col gap-4 px-4 pt-6">
        <h2 className="text-title2">1:1 문의하기</h2>
        <Link
          href="/me/inquiries/new"
          className={cn(
            'flex h-13 items-center justify-center rounded-lg bg-bg-brand-subtle text-body1-bold text-text-brand',
            FOCUS_RING,
          )}
        >
          참가 · 결제 문의하기
        </Link>
        <Link
          href="/me/inquiries"
          className={cn('flex items-center justify-between py-3 text-body1-regular', FOCUS_RING)}
        >
          내 문의
          <Icon name="ChevronRight" className="size-5 text-text-tertiary" />
        </Link>
      </main>
    </>
  )
}
