'use client'

import Link from 'next/link'

import { PostListItem, usePosts } from '@/entities/board'
import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon } from '@/shared/ui'

/** 홈에 보여 줄 인기 글 수 */
const STORY_COUNT = 3

// 피그마 v3 홈 · 러너 이야기(커뮤니티 인기 글 3개). 못 불러오거나 글이 없으면 영역을 그리지 않는다.
export function RunnerStories() {
  const { data } = usePosts({ category: null, sort: 'POPULAR', q: '' })
  const posts = data?.slice(0, STORY_COUNT) ?? []
  if (posts.length === 0) return null

  return (
    <section aria-labelledby="runner-stories" className="flex flex-col gap-4 px-4 pt-2 pb-6">
      <h2 id="runner-stories" className="text-title3">
        <Link href="/community" className={cn('flex items-center gap-1 rounded-sm', FOCUS_RING)}>
          러너 이야기
          <Icon name="ChevronRight" className="size-5 text-text-tertiary" />
        </Link>
      </h2>
      <ul className="[&>li:last-child]:border-b-0">
        {posts.map((post) => (
          <PostListItem key={post.postId} post={post} className="px-0" />
        ))}
      </ul>
    </section>
  )
}
