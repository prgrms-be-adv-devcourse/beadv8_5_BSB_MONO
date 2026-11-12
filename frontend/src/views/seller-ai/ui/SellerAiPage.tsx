'use client'

import { AiChat } from '@/widgets/ai-chat'
import { memberApi, unwrapData } from '@/shared/api'

// 피그마 판매자 센터 · 크루런 AI (1388:65063 첫 화면, 1388:65090 대화). 인사에 상호를 넣는다(못 불러오면 뺀다)
export function SellerAiPage() {
  const seller = memberApi.useFindMine({ query: { select: unwrapData, retry: false } })
  const name = seller.data?.businessName
  return (
    <AiChat
      screen="seller"
      greeting={`안녕하세요, ${name ? `${name} ` : ''}판매자님`}
      placeholder="정산이나 주문, 대회 판매 현황을 물어보세요"
      scopeNote="내 판매자 정보로만 답해요"
      suggestions={['다음 정산 일정', '송장 입력 대기 키트', '이번 달 판매액', '지급보류 이유']}
      footnote="크루런 AI는 틀릴 수 있어요. 돈이 오가는 일은 각 화면에서 한 번 더 확인해 주세요."
    />
  )
}
