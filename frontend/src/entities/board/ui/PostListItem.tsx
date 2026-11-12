import Image from 'next/image'
import Link from 'next/link'

import { cn } from '@/shared/lib'
import { Badge, FOCUS_RING, Icon } from '@/shared/ui'

import type { PostSummary } from '../api/types'
import { formatRelative } from '../lib/format'
import { CommentIcon } from './CommentIcon'

// 피그마 M34 목록의 글 한 줄 · M34-4 검색 결과. 사진이 있으면 오른쪽에 72 썸네일.
export function PostListItem({ post }: { post: PostSummary }) {
  const { writer } = post
  return (
    <li className="border-b border-border-default">
      <Link
        href={`/community/${post.postId}`}
        className={cn('flex items-start gap-4 p-4', FOCUS_RING)}
      >
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="text-caption-medium text-text-secondary">{post.categoryLabel}</span>
          <span className="truncate text-body1-bold text-text-primary">{post.title}</span>
          <span className="truncate text-body2-regular text-text-secondary">{post.excerpt}</span>
          <span className="flex items-center gap-1 pt-1 text-caption-regular text-text-tertiary">
            <span
              className={cn(
                'text-caption-medium',
                writer.withdrawn ? 'text-text-tertiary' : 'text-text-secondary',
              )}
            >
              {writer.nickname}
            </span>
            {writer.seller && <Badge>판매자</Badge>}
            <span>· {formatRelative(post.createdAt)}</span>
            <Icon name="Heart" className="ml-1 size-4" />
            <span>{post.likeCount}</span>
            <CommentIcon className="size-4" />
            <span>{post.commentCount}</span>
          </span>
        </div>
        {post.thumbnailUrl && (
          <div className="relative size-18 shrink-0 overflow-hidden rounded-sm">
            <Image src={post.thumbnailUrl} alt="" fill sizes="72px" className="object-cover" />
          </div>
        )}
      </Link>
    </li>
  )
}
