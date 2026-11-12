import Link from 'next/link'

import { AppHeader } from '@/widgets/app-header'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon } from '@/shared/ui'

// 피그마 문의 · M41 고객센터. 참가 · 결제 문의만 연다(대회 문의는 대회 상세에서)
export function SupportPage() {
  return (
    <>
      <AppHeader type="sub" title="고객센터" backHref="/me" />
      <main className="flex flex-1 flex-col px-4 pt-6">
        <h2 className="pb-1 text-caption-regular text-text-secondary">1:1 문의하기</h2>
        <Link href="/me/inquiries/new" className={cn('flex items-center gap-3 py-3', FOCUS_RING)}>
          <span className="flex-1 text-body1-medium">참가 · 결제 문의하기</span>
          <Icon name="ChevronRight" className="size-5 text-text-tertiary" />
        </Link>
      </main>
    </>
  )
}
