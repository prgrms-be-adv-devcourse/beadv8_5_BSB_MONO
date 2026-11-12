import { ConsolePage } from '@/widgets/console-page'
import { InquiryBoard } from '@/features/inquiry-manage'

// 피그마 A13 문의 (관리자 담당 답변 · 판매자 문의 답변 · 판매자 담당 보기)
export function AdminInquiriesPage({ initialId }: { initialId?: number }) {
  return (
    <ConsolePage title="문의">
      <InquiryBoard role="admin" initialId={initialId} />
    </ConsolePage>
  )
}
