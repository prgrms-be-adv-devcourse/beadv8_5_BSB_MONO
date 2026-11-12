'use client'

import { ConsolePage } from '@/widgets/console-page'
import { InquiryAiDraft } from '@/features/inquiry-ai-draft'
import { InquiryBoard } from '@/features/inquiry-manage'

// 피그마 A13 문의 (관리자 담당 답변 · 판매자 문의 답변 · 판매자 담당 보기) + AI 초안(1388:68471)
export function AdminInquiriesPage({ initialId }: { initialId?: number }) {
  return (
    <ConsolePage title="문의">
      <InquiryBoard
        role="admin"
        initialId={initialId}
        renderAnswer={(args) => <InquiryAiDraft screen="admin" {...args} />}
      />
    </ConsolePage>
  )
}
