'use client'

import type { Route } from 'next'
import Link from 'next/link'
import { useState, type ReactNode } from 'react'

import { CREW_ROLE_LABEL } from '@/entities/crew'
import type { memberModel } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, Button, buttonClassName } from '@/shared/ui'

/** 크루원 목록에서 처음 보여 주는 수. 나머지는 「크루원 N명 더 보기」로 펼친다. */
const VISIBLE_MEMBERS = 5

type Member = memberModel.CrewMemberDto

// 크루 홈(M09)과 크루 관리(M28)의 크루원 목록. 크루에서는 닉네임만 보여 준다(정책 1장 「회원가입」 3번).
export function CrewMemberList({
  members,
  moreHref,
  showAll,
  labelId,
  action,
}: {
  members: Member[]
  /** 있으면 「더 보기」가 펼치지 않고 이 화면(크루원 목록)으로 간다. */
  moreHref?: Route
  /** 처음부터 모두 보여 준다(크루원 목록 화면). */
  showAll?: boolean
  /** 닉네임의 id. 줄 버튼이 aria-describedby로 이 이름을 가리킨다. */
  labelId?: (member: Member) => string
  /** 줄 오른쪽 끝에 붙는 버튼 (크루 관리의 「관리」) */
  action?: (member: Member) => ReactNode
}) {
  const [expanded, setExpanded] = useState(false)
  const shown = expanded || showAll ? members : members.slice(0, VISIBLE_MEMBERS)
  const hidden = members.length - shown.length
  return (
    <>
      {/* 크루 관리(action 있음)는 역할이 닉네임 바로 옆, 크루 홈은 줄 오른쪽 끝에 온다.
          긴 닉네임은 말줄임으로 자르고 전체 이름은 title로 보여 준다. */}
      <ul className={cn('flex flex-col', action ? 'gap-6' : 'gap-3')}>
        {shown.map((member) => {
          const name = (
            <span
              id={labelId?.(member)}
              title={member.nickname}
              className="min-w-0 truncate text-body1-medium"
            >
              {member.nickname}
            </span>
          )
          const role = (
            <span className="shrink-0 text-caption-regular text-text-secondary">
              {CREW_ROLE_LABEL[member.role]}
            </span>
          )
          return (
            <li
              key={member.memberId}
              className={cn('flex items-center gap-3', action && 'min-h-11')}
            >
              <Avatar size="m" name={member.nickname} />
              {action ? (
                <>
                  <div className="flex min-w-0 flex-1 items-center gap-3">
                    {name}
                    {role}
                  </div>
                  <div>{action(member)}</div>
                </>
              ) : (
                <>
                  <div className="flex min-w-0 flex-1">{name}</div>
                  {role}
                </>
              )}
            </li>
          )
        })}
      </ul>
      {hidden > 0 &&
        (moreHref ? (
          <Link href={moreHref} className={buttonClassName({ variant: 'ghost', size: 'm' })}>
            크루원 {hidden}명 더 보기
          </Link>
        ) : (
          <Button variant="ghost" size="m" onClick={() => setExpanded(true)}>
            크루원 {hidden}명 더 보기
          </Button>
        ))}
    </>
  )
}
