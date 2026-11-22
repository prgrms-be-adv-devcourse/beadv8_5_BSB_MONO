'use client'

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'

import { ConsoleLoading, ConsoleMessage, ConsolePage } from '@/widgets/console-page'
import {
  adminHomeBannersQueryOptions,
  adminRacesQueryOptions,
  toBannerCandidate,
  toHomeBannerRow,
  type HomeBannerRow,
} from '@/entities/race'
import { ApiError, productApi, unwrapData } from '@/shared/api'
import { cn, useNow } from '@/shared/lib'
import {
  Button,
  DataTable,
  Icon,
  IconButton,
  Modal,
  Skeleton,
  TextField,
  useToast,
} from '@/shared/ui'

/** 배너로 고를 수 있는 최대 대회 수 (백엔드 계약, CRU-146) */
const MAX_BANNERS = 5

// 피그마 v4 · 관리자 · 12 홈 배너 (2254:29194). 추가 · 빼기 · 순서 바꾸기는 모두 목록 전체를 저장(PUT)한다
export function AdminBannersPage() {
  const now = useNow()
  const banners = useQuery(adminHomeBannersQueryOptions())
  const save = useSaveBanners()
  const [adding, setAdding] = useState(false)

  const rows = now ? (banners.data ?? []).map((b) => toHomeBannerRow(b, now)) : []
  // 개수 · 순서는 현재 시각과 상관없으므로 받은 목록에서 바로 센다(마운트 직후 0 / 5가 깜빡이지 않게)
  const ids = (banners.data ?? []).map((b) => b.raceId)
  const full = ids.length >= MAX_BANNERS

  const move = (index: number, by: -1 | 1) => {
    const next = [...ids]
    ;[next[index], next[index + by]] = [next[index + by], next[index]]
    save.mutate(next)
  }

  return (
    <ConsolePage
      title="홈 배너"
      titleAside={banners.data ? `${ids.length} / ${MAX_BANNERS}` : undefined}
      actions={
        <Button disabled={!banners.data || full} onClick={() => setAdding(true)}>
          대회 추가
        </Button>
      }
    >
      {banners.isPending || !now ? (
        <ConsoleLoading />
      ) : banners.isError && !banners.data ? (
        <ConsoleMessage
          kind="error"
          onRetry={() => void banners.refetch()}
          retrying={banners.isFetching}
        >
          홈 배너를 불러오지 못했어요
        </ConsoleMessage>
      ) : rows.length === 0 ? (
        <ConsoleMessage kind="empty">배너로 고른 대회가 없어요</ConsoleMessage>
      ) : (
        <DataTable
          label="홈 배너"
          framed
          rows={rows}
          rowKey={(r) => String(r.raceId)}
          columns={[
            {
              key: 'order',
              header: '순서',
              width: 'w-16',
              render: (r) => <Dim row={r}>{ids.indexOf(r.raceId) + 1}</Dim>,
            },
            {
              key: 'title',
              header: '대회',
              render: (r) => (
                <Dim row={r} className="text-body1-medium">
                  {r.title}
                </Dim>
              ),
            },
            {
              key: 'stage',
              header: '접수 단계',
              width: 'w-52',
              render: (r) => (
                <Dim row={r} className={cn(r.visible && 'text-body1-medium')}>
                  {r.stage}
                </Dim>
              ),
            },
            {
              key: 'close',
              header: '접수 마감',
              align: 'right',
              width: 'w-24',
              render: (r) => <Dim row={r}>{r.closeDate}</Dim>,
            },
            {
              key: 'move',
              header: '순서 바꾸기',
              width: 'w-24',
              render: (r) => {
                const i = ids.indexOf(r.raceId)
                return (
                  <div className="flex gap-2">
                    <IconButton
                      variant="secondary"
                      size="s"
                      aria-label={`${r.title} 위로`}
                      disabled={i === 0 || save.isPending}
                      onClick={() => move(i, -1)}
                    >
                      <Icon name="ChevronUp" />
                    </IconButton>
                    <IconButton
                      variant="secondary"
                      size="s"
                      aria-label={`${r.title} 아래로`}
                      disabled={i === ids.length - 1 || save.isPending}
                      onClick={() => move(i, 1)}
                    >
                      <Icon name="ChevronDown" />
                    </IconButton>
                  </div>
                )
              },
            },
            {
              key: 'remove',
              header: <span className="sr-only">빼기</span>,
              width: 'w-20',
              render: (r) => (
                <Button
                  variant="secondary"
                  size="s"
                  aria-label={`${r.title} 빼기`}
                  disabled={save.isPending}
                  onClick={() => save.mutate(ids.filter((id) => id !== r.raceId))}
                >
                  빼기
                </Button>
              ),
            },
          ]}
        />
      )}
      <Modal open={adding} onOpenChange={setAdding} title="배너에 올릴 대회">
        {now && (
          <AddBody
            now={now}
            chosen={ids}
            saving={save.isPending}
            onAdd={(raceId) => save.mutate([...ids, raceId])}
          />
        )}
      </Modal>
    </ConsolePage>
  )
}

function AddBody({
  now,
  chosen,
  saving,
  onAdd,
}: {
  now: Date
  chosen: number[]
  saving: boolean
  onAdd: (raceId: number) => void
}) {
  const races = useQuery(adminRacesQueryOptions())
  const [text, setText] = useState('')
  const keyword = text.trim()
  const full = chosen.length >= MAX_BANNERS
  const candidates = (races.data ?? [])
    .flatMap((race) => toBannerCandidate(race, now) ?? [])
    .filter((c) => c.title.includes(keyword))

  return (
    <div className="flex flex-col gap-5">
      <TextField
        type="search"
        aria-label="대회 이름"
        placeholder="대회명으로 찾기"
        leading={<Icon name="Search" />}
        compact
        value={text}
        onChange={(e) => setText(e.target.value)}
      />
      {races.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : races.isError ? (
        <ConsoleMessage
          kind="error"
          onRetry={() => void races.refetch()}
          retrying={races.isFetching}
        >
          대회 목록을 불러오지 못했어요
        </ConsoleMessage>
      ) : candidates.length === 0 ? (
        <ConsoleMessage kind="empty">
          {keyword ? `「${keyword}」로 찾은 대회가 없어요` : '배너에 올릴 수 있는 대회가 없어요'}
        </ConsoleMessage>
      ) : (
        <ul className="flex flex-col">
          {candidates.map((c) => {
            const added = chosen.includes(c.raceId)
            return (
              <li
                key={c.raceId}
                className="flex items-center gap-4 border-b border-border-default py-3"
              >
                <div className="flex min-w-0 flex-1 flex-col gap-1">
                  <p className="truncate text-body1-medium">{c.title}</p>
                  <p className="text-caption-regular text-text-tertiary">{c.meta}</p>
                </div>
                <Button
                  variant="secondary"
                  size="s"
                  aria-label={`${c.title} ${added ? '추가됨' : '추가'}`}
                  disabled={added || full || saving}
                  onClick={() => onAdd(c.raceId)}
                >
                  {added ? '추가됨' : '추가'}
                </Button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

// 홈에 안 보이는 대회는 줄 글자를 흐리게 한다
function Dim({
  row,
  className,
  children,
}: {
  row: HomeBannerRow
  className?: string
  children: React.ReactNode
}) {
  return <span className={cn(className, !row.visible && 'text-text-tertiary')}>{children}</span>
}

/** 배너 목록 전체를 저장하고, 서버가 돌려준 목록으로 화면을 바꾼다. 실패하면 서버 문구를 알리고 목록을 새로 받는다 */
function useSaveBanners() {
  const queryClient = useQueryClient()
  const toast = useToast()
  return useMutation({
    mutationFn: async (raceIds: number[]) =>
      unwrapData(await productApi.replaceAdminHomeBanners({ raceIds })),
    onSuccess: (list) => {
      queryClient.setQueryData(productApi.getFindAdminHomeBannersQueryKey(), list)
    },
    onError: (error) => {
      toast({
        message:
          (error instanceof ApiError && error.serverMessage) || '잠시 후 다시 시도해 주세요.',
        tone: 'error',
      })
      // 그사이 지워지거나 취소된 대회가 있으면 옛 목록으로 다시 보내도 같은 오류가 나므로 새로 받는다
      void queryClient.invalidateQueries({ queryKey: productApi.getFindAdminHomeBannersQueryKey() })
    },
  })
}
