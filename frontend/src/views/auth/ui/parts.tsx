import Link from 'next/link'
import { useId, type ComponentProps, type ReactNode } from 'react'

import { cn } from '@/shared/lib'
import { FOCUS_RING, Icon, IconButton, Logo, type IconName } from '@/shared/ui'

// 로그인·찾기 화면 가운데의 큰 로고(150×44)와 제목
export function AuthIntro({ title, size = 'm' }: { title: string; size?: 's' | 'm' }) {
  return (
    <div className="flex flex-col items-center gap-1 pb-3">
      <Logo className="h-11 w-[150px]" />
      <div className="h-2" />
      <p className={size === 's' ? 'text-body1-bold' : 'text-title3'}>{title}</p>
    </div>
  )
}

// 가입 단계 제목 (왼쪽 정렬)
export function StepIntro({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-col gap-1">
      <h2 className="text-title2 whitespace-pre-line">{title}</h2>
      {description && <p className="text-body2-regular text-text-tertiary">{description}</p>}
    </div>
  )
}

// 회원 화면 본문이 짧을 때 가운데로 모으는 판 (bg-default, 옆 16 · 위아래 24, 칸 사이 20)
export function CenterBody({ children, center = true }: { children: ReactNode; center?: boolean }) {
  return (
    <main className={center ? 'flex flex-1 flex-col justify-center' : 'flex flex-1 flex-col'}>
      <div className="flex flex-col gap-5 px-4 py-6">{children}</div>
    </main>
  )
}

// 피그마 계정 찾기 탭 두 개. 화면이 둘로 나뉘어 있어 TabBar 모양의 링크로 만든다.
export function AccountTabs({ current }: { current: 'email' | 'password' }) {
  const tabs = [
    { key: 'email', href: '/find-email', label: '이메일 찾기' },
    { key: 'password', href: '/password-reset', label: '비밀번호 찾기' },
  ] as const
  return (
    <nav aria-label="계정 찾기" className="flex border-b border-border-default bg-bg-surface">
      {tabs.map((tab) => (
        <Link
          key={tab.key}
          href={tab.href}
          aria-current={tab.key === current ? 'page' : undefined}
          className={cn(
            'relative flex-1 py-3 text-center text-body1-medium text-text-tertiary',
            'aria-[current=page]:font-bold aria-[current=page]:text-text-primary',
            'after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 aria-[current=page]:after:bg-text-primary',
            FOCUS_RING,
          )}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  )
}

// 가입 단계 위쪽 줄. 뒤로 가기만 두고 제목은 본문 제목이 맡는다(시안 가입 · 이메일 · 비밀번호 · 닉네임).
// title을 주면 제목 줄(완료 화면). 뒤로 가기는 onBack이 있을 때만 그린다.
export function StepHeader({ onBack, title }: { onBack?: () => void; title?: string }) {
  return (
    <header
      className={cn(
        'sticky top-0 z-10 flex h-(--layout-header-height) shrink-0 items-center gap-1 bg-bg-surface px-1',
        title && 'border-b border-border-default px-4',
      )}
    >
      {onBack && (
        <IconButton aria-label="뒤로 가기" onClick={onBack}>
          <Icon name="ChevronLeft" />
        </IconButton>
      )}
      {title && <h1 className="text-body1-bold">{title}</h1>}
    </header>
  )
}

type LineHint = { ok: boolean; text: string }

// 가입 단계의 밑줄 입력 칸. 아래에 오류 문구(빨강)와 조건 확인 줄(맞으면 초록 체크)을 둔다.
export function LineField({
  error,
  hints,
  onClear,
  className,
  ...props
}: ComponentProps<'input'> & {
  error?: ReactNode
  hints?: LineHint[]
  /** 있으면 입력 오른쪽에 지우기 버튼을 둔다 */
  onClear?: () => void
}) {
  const messageId = useId()
  return (
    <div className="flex flex-col gap-2">
      <div
        className={cn(
          'flex items-center gap-2 border-b-2',
          error ? 'border-border-danger' : 'border-text-primary',
        )}
      >
        <input
          aria-invalid={error ? true : undefined}
          aria-describedby={messageId}
          className={cn(
            'min-w-0 flex-1 bg-transparent py-3 text-title3 outline-none placeholder:text-text-tertiary',
            className,
          )}
          {...props}
        />
        {onClear && (
          <IconButton size="s" variant="secondary" aria-label="지우기" onClick={onClear}>
            <Icon name="X" />
          </IconButton>
        )}
      </div>
      <div id={messageId} className="flex flex-col gap-1">
        {error && (
          <p role="alert" className="text-caption-regular text-text-danger">
            {error}
          </p>
        )}
        {hints?.map((hint) => (
          <p
            key={hint.text}
            className={cn(
              'flex items-center gap-1 text-caption-regular',
              hint.ok ? 'text-text-success' : 'text-text-tertiary',
            )}
          >
            <Icon name="Check" className="size-4" />
            {hint.text}
          </p>
        ))}
      </div>
    </div>
  )
}

// 끝났음을 알리는 가운데 화면: 연한 파랑 원 안의 아이콘 · 제목 · 설명 (시안 가입 완료 · 메일 보냄 · 바꿈 완료)
export function StatusScreen({
  icon = 'Check',
  title,
  description,
}: {
  icon?: IconName
  title: string
  description?: string
}) {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-4 px-6 text-center">
      <span className="flex size-16 items-center justify-center rounded-full bg-bg-brand-subtle text-text-brand">
        <Icon name={icon} className="size-6" />
      </span>
      <div className="flex flex-col gap-1">
        <h2 className="text-title3">{title}</h2>
        {description && <p className="text-body2-regular text-text-secondary">{description}</p>}
      </div>
    </main>
  )
}
