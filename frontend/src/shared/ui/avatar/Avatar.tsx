'use client'

import { Avatar as BaseAvatar } from '@base-ui/react/avatar'

import { cn } from '@/shared/lib'

import { Icon } from '../icon/icons'

export type AvatarSize = 's' | 'm' | 'l' | 'xl'

// S24 M32 L40 XL56. 첫 글자·아이콘·상태 점 크기도 크기마다 다르다.
const SIZE: Record<AvatarSize, { box: string; initial: string; icon: string; status: string }> = {
  s: { box: 'size-6', initial: 'text-label-s', icon: 'size-3.5', status: 'size-2 ring-[1.5px]' },
  m: { box: 'size-8', initial: 'text-label-s', icon: 'size-4.5', status: 'size-2.5 ring-[1.67px]' },
  l: { box: 'size-10', initial: 'text-label-m', icon: 'size-5.5', status: 'size-3 ring-2' },
  xl: {
    box: 'size-14',
    initial: 'text-heading-m',
    icon: 'size-7.5',
    status: 'size-3.5 ring-[2.33px]',
  },
}

export type AvatarProps = {
  size?: AvatarSize
  /** 사진 주소. 없거나 불러오지 못하면 첫 글자, 그것도 없으면 사람 아이콘을 보여 준다. */
  src?: string
  /** 이름. 첫 글자와 대체 텍스트로 쓴다. */
  name?: string
  /** 결제 완료 같은 상태 점 */
  showStatus?: boolean
  className?: string
}

export function Avatar({ size = 'm', src, name, showStatus = false, className }: AvatarProps) {
  const sizes = SIZE[size]
  const initial = name?.trim().charAt(0)
  return (
    <span className={cn('relative inline-flex shrink-0', sizes.box, className)}>
      <BaseAvatar.Root
        className={cn(
          'flex size-full items-center justify-center overflow-hidden rounded-full',
          initial
            ? 'bg-bg-brand-subtle text-text-brand'
            : 'bg-bg-subtle text-text-tertiary inset-ring inset-ring-border-default',
        )}
      >
        {src && <BaseAvatar.Image src={src} alt={name ?? ''} className="size-full object-cover" />}
        <BaseAvatar.Fallback className="flex items-center justify-center">
          {initial ? (
            <span className={sizes.initial}>{initial}</span>
          ) : (
            <Icon name="User" className={sizes.icon} />
          )}
        </BaseAvatar.Fallback>
      </BaseAvatar.Root>
      {showStatus && (
        <span
          className={cn(
            'absolute right-0 bottom-0 rounded-full bg-text-success ring-bg-surface',
            sizes.status,
          )}
        />
      )}
    </span>
  )
}

export type AvatarGroupProps = {
  /** 앞에서 4명까지 보여 주고 나머지는 +N으로 줄인다. */
  people: { name?: string; src?: string }[]
  size?: 's' | 'm'
  className?: string
}

const MAX_VISIBLE = 4

// 크루원 여러 명을 겹쳐 보여 준다. 겹치는 폭은 S 6, M 8.
export function AvatarGroup({ people, size = 's', className }: AvatarGroupProps) {
  const visible = people.slice(0, MAX_VISIBLE)
  const rest = people.length - visible.length
  return (
    <div className={cn('flex', size === 's' ? '-space-x-1.5' : '-space-x-2', className)}>
      {visible.map((person, i) => (
        <Avatar
          key={i}
          size={size}
          name={person.name}
          src={person.src}
          className="rounded-full ring-2 ring-bg-surface"
        />
      ))}
      {rest > 0 && (
        <span
          className={cn(
            'flex shrink-0 items-center justify-center rounded-full bg-bg-disabled text-label-s text-text-secondary ring-2 ring-bg-surface',
            SIZE[size].box,
          )}
        >
          +{rest}
        </span>
      )}
    </div>
  )
}
