import { AiChat } from '@/widgets/ai-chat'

// 피그마 판매자 센터 · 크루런 AI (1388:65063 첫 화면, 1388:65090 대화)
export function SellerAiPage() {
  return (
    <AiChat
      screen="seller"
      greeting="안녕하세요, 판매자님"
      placeholder="정산이나 주문, 대회 판매 현황을 물어보세요"
      scopeNote="내 판매자 정보로만 답해요"
      suggestions={['다음 정산 일정', '송장 입력 대기 키트', '이번 달 판매액', '지급보류 이유']}
    />
  )
}
