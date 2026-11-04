'use client'

import { useState, type ReactNode } from 'react'

import { CREW_ROLE_LABEL, CREW_ROLE_TONE } from '@/entities/crew'
import type { memberModel } from '@/shared/api'
import { Avatar, Badge, Button } from '@/shared/ui'

/** 크루원 목록에서 처음 보여 주는 수. 나머지는 「크루원 N명 더 보기」로 펼친다. */
const VISIBLE_MEMBERS = 5

type Member = memberModel.CrewMemberDto

// 크루 홈(M09)과 크루 관리(M28)의 크루원 목록. 크루에서는 닉네임만 보여 준다(정책 1장 「회원가입」 3번).
export function CrewMemberList({
  members,
  labelId,
  action,
}: {
  members: Member[]
  /** 닉네임의 id. 줄 버튼이 aria-describedby로 이 이름을 가리킨다. */
  labelId?: (member: Member) => string
  /** 줄 오른쪽 끝에 붙는 버튼 (크루 관리의 「관리」) */
  action?: (member: Member) => ReactNode
}) {
  const [expanded, setExpanded] = useState(false)
  const shown = expanded ? members : members.slice(0, VISIBLE_MEMBERS)
  const hidden = members.length - shown.length
  return (
    <>
      {/* 닉네임 칸 폭을 고정해 닉네임 길이와 상관없이 역할 뱃지가 늘 같은 자리에 온다.
          9rem은 가장 긴 한글 닉네임(8자)이 들어가는 폭이다. 더 긴 영문 닉네임은 말줄임으로 자르고 전체 이름은 title로 보여 준다. */}
      <ul className="flex flex-col gap-3">
        {shown.map((member) => (
          <li
            key={member.memberId}
            className="grid grid-cols-[auto_9rem_auto_1fr] items-center gap-3"
          >
            <Avatar size="m" name={member.nickname} />
            <span
              id={labelId?.(member)}
              title={member.nickname}
              className="min-w-0 truncate text-body-m"
            >
              {member.nickname}
            </span>
            <Badge tone={CREW_ROLE_TONE[member.role]}>{CREW_ROLE_LABEL[member.role]}</Badge>
            <div className="justify-self-end">{action?.(member)}</div>
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
