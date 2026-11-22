import Link from 'next/link'

import { ConsoleCard, ConsolePage } from '@/widgets/console-page'
import { buttonClassName } from '@/shared/ui'

// 피그마 판매자 공통 · 권한 없음(심사 중인 판매자, 2070:59948): 위아래 96, 제목 · 설명 사이 8, 설명 · 버튼 사이 28
export function SellerPendingNotice() {
  return (
    <ConsolePage title="대회 등록">
      <ConsoleCard className="items-center gap-2 py-24 text-center">
        <h2 className="text-title3 text-text-primary">승인된 판매자만 대회를 등록할 수 있어요</h2>
        <p className="text-caption-regular text-text-secondary">판매자 신청을 심사하고 있어요</p>
        <Link
          href="/seller-apply/result"
          className={buttonClassName({ variant: 'secondary', className: 'mt-5' })}
        >
          신청 결과 보기
        </Link>
      </ConsoleCard>
    </ConsolePage>
  )
}
