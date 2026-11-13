'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSyncExternalStore } from 'react'

import {
  customFetch,
  fileApi,
  getAuthStatus,
  subscribeAuthStatus,
  unwrapData,
  type RsData,
} from '@/shared/api'

import type {
  AdminReportList,
  BoardCategory,
  BoardComment,
  Like,
  PostDetail,
  PostPhoto,
  PostSaveRequest,
  PostSort,
  PostSummary,
  ReportReason,
  ReportTargetType,
} from './types'

const BASE = '/api/v1/board'
const KEY = ['board'] as const

const json = (method: string, body?: unknown): RequestInit => ({
  method,
  headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
  body: body === undefined ? undefined : JSON.stringify(body),
})

/**
 * 읽기는 로그인 없이 되지만, 토큰이 있으면 「내 글」 「내가 누른 좋아요」가 함께 온다.
 * 새로고침 직후 로그인 확인이 끝나기 전에 부르면 그 값이 빠지므로, 확인이 끝난 뒤에 부르고 로그인 상태가 바뀌면 다시 부른다.
 */
function useReadAuth() {
  const status = useSyncExternalStore(subscribeAuthStatus, getAuthStatus, () => 'pending' as const)
  return { status, enabled: status !== 'pending' }
}

export type PostListParams = { category: BoardCategory | null; sort: PostSort; q: string }

/** 글 목록·검색 (GET /api/v1/board/posts). q가 비어 있으면 목록, 있으면 검색이다 */
export function usePosts({ category, sort, q }: PostListParams, options?: { enabled?: boolean }) {
  const auth = useReadAuth()
  return useQuery({
    queryKey: [...KEY, 'posts', { category, sort, q }, auth.status],
    queryFn: async () => {
      const params = new URLSearchParams({ sort })
      if (category) params.set('category', category)
      if (q) params.set('q', q)
      return unwrapData(await customFetch<RsData<PostSummary[]>>(`${BASE}/posts?${params}`))
    },
    enabled: auth.enabled && (options?.enabled ?? true),
    retry: false,
  })
}

export function usePost(postId: number) {
  const auth = useReadAuth()
  return useQuery({
    queryKey: [...KEY, 'post', postId, auth.status],
    queryFn: async () =>
      unwrapData(await customFetch<RsData<PostDetail>>(`${BASE}/posts/${postId}`)),
    enabled: auth.enabled,
    retry: false,
  })
}

export function useComments(postId: number) {
  const auth = useReadAuth()
  return useQuery({
    queryKey: [...KEY, 'comments', postId, auth.status],
    queryFn: async () =>
      unwrapData(await customFetch<RsData<BoardComment[]>>(`${BASE}/posts/${postId}/comments`)),
    enabled: auth.enabled,
    retry: false,
  })
}

/** 글에 붙은 사진 (file-service. 글은 refType BoardPost로 연결된다) */
export function usePostPhotos(postId: number) {
  return useQuery({
    queryKey: [...KEY, 'photos', postId],
    queryFn: async () => {
      const files = unwrapData(await fileApi.findByRef({ refType: 'BoardPost', refId: postId }))
      return files.flatMap((f): PostPhoto[] =>
        f.id !== undefined && f.url ? [{ fileId: f.id, url: f.url }] : [],
      )
    },
    retry: false,
  })
}

function useInvalidate() {
  const client = useQueryClient()
  return () => client.invalidateQueries({ queryKey: KEY })
}

/** 글 쓰기. 만든 글 번호를 돌려준다 */
export function useCreatePost() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: async (data: PostSaveRequest) =>
      unwrapData(
        await customFetch<RsData<{ postId: number }>>(`${BASE}/posts`, json('POST', data)),
      ),
    onSuccess: invalidate,
  })
}

export function useUpdatePost(postId: number) {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (data: PostSaveRequest) =>
      customFetch<RsData<null>>(`${BASE}/posts/${postId}`, json('PUT', data)),
    onSuccess: invalidate,
  })
}

export function useDeletePost(postId: number) {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: () => customFetch<RsData<null>>(`${BASE}/posts/${postId}`, json('DELETE')),
    onSuccess: invalidate,
  })
}

/** 좋아요 누르기·취소 */
export function useToggleLike(postId: number) {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: async (liked: boolean) =>
      unwrapData(
        await customFetch<RsData<Like>>(
          `${BASE}/posts/${postId}/like`,
          json(liked ? 'DELETE' : 'PUT'),
        ),
      ),
    onSuccess: invalidate,
  })
}

/** 글 신고 (POST /api/v1/board/posts/{postId}/report). 백엔드에 아직 없다 */
export function useReportPost(postId: number) {
  return useMutation({
    mutationFn: (reason: ReportReason) =>
      customFetch<RsData<null>>(`${BASE}/posts/${postId}/report`, json('POST', { reason })),
  })
}

export function useCreateComment(postId: number) {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (data: { content: string; parentId?: number }) =>
      customFetch<RsData<{ commentId: number }>>(
        `${BASE}/posts/${postId}/comments`,
        json('POST', data),
      ),
    onSuccess: invalidate,
  })
}

export function useUpdateComment() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: ({ commentId, content }: { commentId: number; content: string }) =>
      customFetch<RsData<null>>(`${BASE}/comments/${commentId}`, json('PUT', { content })),
    onSuccess: invalidate,
  })
}

export function useDeleteComment() {
  const invalidate = useInvalidate()
  return useMutation({
    mutationFn: (commentId: number) =>
      customFetch<RsData<null>>(`${BASE}/comments/${commentId}`, json('DELETE')),
    onSuccess: invalidate,
  })
}

// 관리자 신고 관리 (GET /api/v1/board/admin/reports 등). 백엔드에 아직 없다
const REPORTS_KEY = [...KEY, 'admin-reports'] as const

export function useAdminReports() {
  return useQuery({
    queryKey: REPORTS_KEY,
    queryFn: async () =>
      unwrapData(await customFetch<RsData<AdminReportList>>(`${BASE}/admin/reports`)),
    retry: false,
  })
}

/** 숨겨진 글·댓글을 되살리거나(restore) 지운다(delete) */
export function useHandleReport() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({
      targetType,
      targetId,
      action,
    }: {
      targetType: ReportTargetType
      targetId: number
      action: 'restore' | 'delete'
    }) =>
      customFetch<RsData<null>>(
        `${BASE}/admin/reports/${targetType.toLowerCase()}s/${targetId}/${action}`,
        json('POST'),
      ),
    onSuccess: () => client.invalidateQueries({ queryKey: REPORTS_KEY }),
  })
}
