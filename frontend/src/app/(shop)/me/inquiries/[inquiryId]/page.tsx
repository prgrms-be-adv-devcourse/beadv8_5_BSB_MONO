import { notFound } from 'next/navigation'

import { InquiryDetailPage } from '@/views/inquiry'

export default async function Page({ params }: PageProps<'/me/inquiries/[inquiryId]'>) {
  const { inquiryId } = await params
  if (!/^[1-9]\d*$/.test(inquiryId)) notFound()
  return <InquiryDetailPage inquiryId={Number(inquiryId)} />
}
