'use client'

import { useState, type ReactNode } from 'react'

import { CREW_ROLE_LABEL, CREW_ROLE_TONE } from '@/entities/crew'
import type { memberModel } from '@/shared/api'
import { cn } from '@/shared/lib'
import { Avatar, Badge, Button } from '@/shared/ui'

/** 크루원 목록에서 처음 보여 주는 수. 나머지는 「크루원 N명 더 보기」로 펼친다. */
const VISIBLE_MEMBERS = 5

type Member = memberModel.CrewMemberDto

// 크루 홈(M09)과 크루 관리(M28)의 크루원 목록. 크루에서는 닉네임만 보여 준다(정책 1장 「회원가입」 3번).
export function CrewMemberList({
  members,
  label = (member) => member.nickname,
  action,
}: {
  members: Member[]
  label?: (member: Member) => string
  /** 줄 오른쪽 끝에 붙는 버튼 (크루 관리의 「관리」) */
  action?: (member: Member) => ReactNode
}) {
  const [expanded, setExpanded] = useState(false)
  const shown = expanded ? members : members.slice(0, VISIBLE_MEMBERS)
  const hidden = members.length - shown.length
  return (
    <>
      <ul className="flex flex-col gap-3">
        {shown.map((member) => (
          <li key={member.memberId} className="flex items-center gap-3">
            <Avatar size="m" name={member.nickname} />
            {/* 관리 버튼이 있으면 시안 M28처럼 역할을 이름 바로 뒤에 붙여, 버튼이 없는 내 줄과 줄을 맞춘다 */}
            <span className={cn('min-w-0 truncate text-body-m', !action && 'flex-1')}>
              {label(member)}
            </span>
            <Badge tone={CREW_ROLE_TONE[member.role]}>{CREW_ROLE_LABEL[member.role]}</Badge>
            {action && <span className="ml-auto">{action(member)}</span>}
          </li>
        ))}
      </ul>
      {hidden > 0 && (
        <Button variant="ghost" size="m" onClick={() => setExpanded(true)}>
          크루원 {hidden}명 더 보기
        </Button>
      )}
    </>
  )
}
