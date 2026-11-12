import { notFound } from 'next/navigation'

import { CommunityPostPage } from '@/views/community-post'

export default async function Page({ params }: PageProps<'/community/[postId]'>) {
  const { postId } = await params
  const id = Number(postId)
  if (!Number.isSafeInteger(id) || id < 1) notFound()
  return <CommunityPostPage postId={id} />
}
