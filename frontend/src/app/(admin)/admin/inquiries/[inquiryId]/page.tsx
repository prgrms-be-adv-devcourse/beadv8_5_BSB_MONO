import { AdminInquiriesPage } from '@/views/admin-inquiries'

// 알림 링크(/admin/inquiries/12)로 들어오면 그 문의를 연다
export default async function Page({ params }: PageProps<'/admin/inquiries/[inquiryId]'>) {
  const { inquiryId } = await params
  const id = Number(inquiryId)
  return <AdminInquiriesPage initialId={Number.isInteger(id) ? id : undefined} />
}
