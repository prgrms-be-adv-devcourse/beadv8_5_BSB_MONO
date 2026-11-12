import { notFound } from 'next/navigation'

import { InquiryEditPage } from '@/views/inquiry'

export default async function Page({ params }: PageProps<'/me/inquiries/[inquiryId]/edit'>) {
  const { inquiryId } = await params
  if (!/^[1-9]\d*$/.test(inquiryId)) notFound()
  return <InquiryEditPage inquiryId={Number(inquiryId)} />
}
