import { NotificationDetailPage } from '@/views/notifications'

export default async function Page({ params }: PageProps<'/notifications/[category]'>) {
  const { category } = await params
  return <NotificationDetailPage category={category} />
}
