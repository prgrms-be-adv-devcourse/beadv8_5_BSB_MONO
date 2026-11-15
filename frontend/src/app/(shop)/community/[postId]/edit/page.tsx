import { notFound } from 'next/navigation'

import { CommunityWritePage } from '@/views/community-write'

export default async function Page({ params }: PageProps<'/community/[postId]/edit'>) {
  const { postId } = await params
  const id = Number(postId)
  if (!Number.isSafeInteger(id) || id < 1) notFound()
  return <CommunityWritePage postId={id} />
}
