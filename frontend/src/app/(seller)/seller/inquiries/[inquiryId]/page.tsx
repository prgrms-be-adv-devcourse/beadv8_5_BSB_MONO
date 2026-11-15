import { SellerInquiriesPage } from '@/views/seller-inquiries'

// 알림 링크(/seller/inquiries/12)로 들어오면 그 문의를 연다
export default async function Page({ params }: PageProps<'/seller/inquiries/[inquiryId]'>) {
  const { inquiryId } = await params
  const id = Number(inquiryId)
  return <SellerInquiriesPage initialId={Number.isInteger(id) ? id : undefined} />
}
