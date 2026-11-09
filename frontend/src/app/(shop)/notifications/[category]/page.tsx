import { notFound } from 'next/navigation'

import { isNoticeCategory, NotificationDetailPage } from '@/views/notifications'

export default async function Page({ params }: PageProps<'/notifications/[category]'>) {
  const { category } = await params
  if (!isNoticeCategory(category)) notFound()
  return <NotificationDetailPage category={category} />
}
