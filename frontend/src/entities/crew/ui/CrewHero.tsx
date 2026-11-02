import type { ReactNode } from 'react'

import { Avatar, AvatarGroup } from '@/shared/ui'

export type CrewHeroProps = {
  name: string
  /** 이름 아래 한 줄 (크루원 12명 · 나는 크루장) */
  summary: ReactNode
  /** 크루원 얼굴 묶음. 없으면 그 줄을 빼고, action만 있으면 오른쪽에 둔다. */
  people?: { name?: string }[]
  /** 얼굴 묶음 오른쪽 버튼 (초대 링크 만들기) */
  action?: ReactNode
}

// 크루 홈(M09) · 초대 받기(M10) 맨 위의 남색 크루 띠. 바로 다음에는 구분 띠를 넣지 않는다.
export function CrewHero({ name, summary, people, action }: CrewHeroProps) {
  return (
    <section className="flex flex-col gap-4 bg-bg-hero px-4 py-6 text-text-on-hero">
      <div className="flex items-center gap-3">
        <Avatar size="xl" name={name} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <h2 className="truncate text-heading-xl">{name}</h2>
          <p className="text-body-s text-text-on-hero-muted">{summary}</p>
        </div>
      </div>
      {(people?.length || action) && (
        <div className="flex items-center justify-between gap-3">
          {people?.length ? <AvatarGroup people={people} /> : <span />}
          {action}
        </div>
      )}
    </section>
  )
}
