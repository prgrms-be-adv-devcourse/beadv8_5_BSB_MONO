'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { useState } from 'react'

import { AppHeader } from '@/widgets/app-header'
import { useAuthStatus } from '@/features/auth'
import { canInvite, CREW_ROLE_LABEL, CREW_ROLE_TONE, CrewHero } from '@/entities/crew'
import { ApiError, memberApi, unwrapData } from '@/shared/api'
import { Avatar, Badge, Button, buttonClassName, Divider, EmptyState, Skeleton } from '@/shared/ui'

import { CrewBand, CrewLoadError } from './CrewBand'

/** 크루원 목록에서 처음 보여 주는 수. 나머지는 「크루원 N명 더 보기」로 펼친다. */
const VISIBLE_MEMBERS = 5

// 피그마 시안v4 · M09 크루 홈 (크루장 · 크루원 · 크루원이 아님).
// 크루 정보와 크루원은 크루 홈 API(GET /api/v1/member/crews/{crewId})로 불러온다. 크루원이 아니면 403 CREW_NOT_MEMBER다.
// TODO(api): 파티 API 계약이 아직 없어 진행 중인 파티는 늘 「없음」으로 보인다.
export function CrewHomePage({ crewId }: { crewId: number }) {
  const crew = memberApi.useFindCrew(crewId, {
    query: {
      select: unwrapData,
      enabled: useAuthStatus() === 'authenticated',
      // 크루원이 아님(403) · 없는 크루(404)는 다시 불러도 같다
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
    },
  })

  return (
    <>
      <AppHeader type="sub" title="크루 홈" backHref="/crews" />
      <main className="flex flex-1 flex-col">
        {crew.isPending ? (
          <CrewHomeSkeleton />
        ) : crew.isError ? (
          <CrewHomeError error={crew.error} onRetry={() => crew.refetch()} />
        ) : (
          <>
            <CrewHero
              name={crew.data.name}
              summary={`크루원 ${crew.data.memberCount}명 · 나는 ${CREW_ROLE_LABEL[crew.data.myRole]}`}
              people={crew.data.members.map((member) => ({ name: member.nickname }))}
              action={
                canInvite(crew.data.myRole) && (
                  <Link
                    href={`/crews/${crewId}/invites` as Route}
                    className={buttonClassName({ variant: 'secondary', size: 's' })}
                  >
                    초대 링크 만들기
                  </Link>
                )
              }
            />
            <CrewBand>
              <h2 className="text-heading-m">진행 중인 파티</h2>
              <EmptyState
                title="진행 중인 파티가 없어요"
                description="대회 상세에서 「크루로 신청」을 누르면 파티를 만들 수 있어요."
                action={
                  <Link
                    href="/races"
                    className={buttonClassName({ variant: 'secondary', size: 'm' })}
                  >
                    대회 둘러보기
                  </Link>
                }
              />
            </CrewBand>
            <Divider type="section" />
            <CrewMembers count={crew.data.memberCount} members={crew.data.members} />
          </>
        )}
      </main>
    </>
  )
}

function CrewMembers({
  count,
  members,
}: {
  count: number
  members: { memberId: number; nickname: string; role: keyof typeof CREW_ROLE_LABEL }[]
}) {
  const [expanded, setExpanded] = useState(false)
  const shown = expanded ? members : members.slice(0, VISIBLE_MEMBERS)
  const hidden = members.length - shown.length
  return (
    <CrewBand>
      <h2 className="text-heading-m">크루원 {count}명</h2>
      {/* 크루에서는 닉네임만 보여 준다(정책 1장 「회원가입」 3번) */}
      <ul className="flex flex-col gap-3">
        {shown.map((member) => (
          <li key={member.memberId} className="flex items-center gap-3">
            <Avatar size="m" name={member.nickname} />
            <span className="min-w-0 flex-1 truncate text-body-m">{member.nickname}</span>
            <Badge tone={CREW_ROLE_TONE[member.role]}>{CREW_ROLE_LABEL[member.role]}</Badge>
          </li>
        ))}
      </ul>
      {hidden > 0 && (
        <Button variant="ghost" size="m" onClick={() => setExpanded(true)}>
          크루원 {hidden}명 더 보기
        </Button>
      )}
    </CrewBand>
  )
}

// 시안에 없는 상태: 크루 홈 로딩. 내 크루 목록(M09-0) 로딩처럼 회색 줄로 자리만 잡는다.
function CrewHomeSkeleton() {
  return (
    <>
      <section className="flex items-center gap-3 bg-bg-hero px-4 py-6">
        <span role="status" className="sr-only">
          크루를 불러오는 중
        </span>
        <Skeleton shape="circle" className="size-14" />
        <Skeleton className="flex-1" />
      </section>
      <CrewBand>
        <Skeleton className="w-full" />
        <Skeleton className="w-2/3" />
      </CrewBand>
    </>
  )
}

// 크루원이 아님(403) · 없는 크루(404, 없앤 크루 포함)는 다시 불러도 같아서 내 크루 목록으로 보낸다.
// 「없는 크루」는 시안에 없는 상태라 크루원이 아님과 같은 모양을 쓴다.
const CREW_HOME_FAILURE: Record<string, { title: string; description: string }> = {
  CREW_NOT_MEMBER: {
    title: '크루원만 볼 수 있어요',
    description: '크루장이나 운영진에게 초대 링크를 받아 먼저 가입해 주세요.',
  },
  CREW_NOT_FOUND: {
    title: '없는 크루예요',
    description: '주소가 잘못됐거나 크루장이 크루를 없앴어요.',
  },
}

function CrewHomeError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  const failure =
    error instanceof ApiError && error.code ? CREW_HOME_FAILURE[error.code] : undefined
  if (!failure) return <CrewLoadError onRetry={onRetry} />
  return (
    <EmptyState
      className="pt-26"
      title={failure.title}
      description={failure.description}
      action={
        <Link href="/crews" className={buttonClassName({ variant: 'secondary', size: 'm' })}>
          내 크루로 가기
        </Link>
      }
    />
  )
}
