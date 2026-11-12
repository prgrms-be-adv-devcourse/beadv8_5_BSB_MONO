import Link from 'next/link'

import { ConsolePage } from '@/widgets/console-page'
import { InquiryBoard, SellerNewInquiry } from '@/features/inquiry-manage'
import { buttonClassName } from '@/shared/ui'

// 피그마 S13 문의 (답변 쓰기 · 답변 완료 · 주문 없는 문의 · 취소된 주문의 문의)
export function SellerInquiriesPage({ initialId }: { initialId?: number }) {
  return (
    <ConsolePage
      title="문의"
      actions={
        <Link href="/seller/inquiries/new" className={buttonClassName({ variant: 'secondary' })}>
          크루런에 문의하기
        </Link>
      }
    >
      <InquiryBoard role="seller" initialId={initialId} />
    </ConsolePage>
  )
}

// 피그마 S14 크루런에 문의하기
export function SellerNewInquiryPage() {
  return (
    <ConsolePage title="크루런에 문의하기">
      <SellerNewInquiry />
    </ConsolePage>
  )
}
