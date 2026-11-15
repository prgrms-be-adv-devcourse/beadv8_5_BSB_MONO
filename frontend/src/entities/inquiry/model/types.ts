// 백엔드 inquiry-service의 InquiryDto · InquiryManagePageDto · InquiryOrderDto와 같은 모양
export type InquiryType = 'APPLY_PAYMENT' | 'RACE_VENUE' | 'KIT_DELIVERY' | 'ETC' | 'SELLER_SUPPORT'
export type InquiryAssignee = 'SELLER' | 'ADMIN'
export type InquiryStatus = 'WAITING' | 'ANSWERED'

export type InquiryOrderItem = {
  raceId: number
  raceName: string
  courseName: string
  kitSummary: string | null
  deliveryStatus: string | null
}

export type InquiryOrder = {
  orderNo: string
  /** 관리자 상세에만 온다 */
  paymentAmount: number | null
  status: string
  items: InquiryOrderItem[]
}

export type Inquiry = {
  id: number
  raceId: number | null
  orderId: number | null
  type: InquiryType
  assignee: InquiryAssignee
  title: string
  body: string
  status: InquiryStatus
  answer: string | null
  answeredAt: string | null
  createDate: string
  /** 판매자 · 관리자 화면에만 온다 */
  memberNickname: string | null
  order: InquiryOrder | null
}

export type InquiryPage = {
  inquiries: Inquiry[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type InquiryManagePage = InquiryPage & { waitingCount: number; answeredCount: number }

export type CreateInquiryRequest = {
  raceId?: number | null
  orderId?: number | null
  type: InquiryType
  title: string
  body: string
}

/** 서버 글자 수 제한 (Inquiry.MAX_*_LENGTH) */
export const TITLE_MAX = 100
export const BODY_MAX = 1000
export const ANSWER_MAX = 2000
