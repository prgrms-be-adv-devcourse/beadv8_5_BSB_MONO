import { AiChat } from '@/widgets/ai-chat'

// 피그마 관리자 센터 · 크루런 AI (1388:68409 첫 화면, 1388:68427 대화)
export function AdminAiPage() {
  return (
    <AiChat
      screen="admin"
      greeting="안녕하세요, 운영자님"
      placeholder="처리를 기다리는 일이나 정산 현황을 물어보세요"
      scopeNote="회원 정보는 가린 값으로만 답해요"
      suggestions={['승인 대기 판매자', '예치금 환급 요청', '지급보류 중인 정산', '답변 대기 문의']}
    />
  )
}
