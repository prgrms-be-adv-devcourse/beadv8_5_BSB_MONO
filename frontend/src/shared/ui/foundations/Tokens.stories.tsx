import type { Meta, StoryObj } from '@storybook/nextjs-vite'

// globals.css의 디자인 토큰을 한눈에 본다. 툴바의 테마를 dark로 바꾸면 Dark 모드 값으로 바뀐다.
// Tailwind는 파일에 적힌 클래스 이름만 만들기 때문에 클래스를 문자열 그대로 적었다.

const BG = [
  ['bg-default', 'bg-bg-default'],
  ['bg-surface', 'bg-bg-surface'],
  ['bg-subtle', 'bg-bg-subtle'],
  ['bg-inverse', 'bg-bg-inverse'],
  ['bg-brand', 'bg-bg-brand'],
  ['bg-brand-hover', 'bg-bg-brand-hover'],
  ['bg-brand-subtle', 'bg-bg-brand-subtle'],
  ['bg-danger', 'bg-bg-danger'],
  ['bg-disabled', 'bg-bg-disabled'],
  ['bg-success-subtle', 'bg-bg-success-subtle'],
  ['bg-warning-subtle', 'bg-bg-warning-subtle'],
  ['bg-danger-subtle', 'bg-bg-danger-subtle'],
  ['bg-info-subtle', 'bg-bg-info-subtle'],
  ['bg-accent', 'bg-bg-accent'],
  ['bg-accent-subtle', 'bg-bg-accent-subtle'],
  ['bg-hero', 'bg-bg-hero'],
  ['bg-hero-raised', 'bg-bg-hero-raised'],
  ['bg-track', 'bg-bg-track'],
] as const

const TEXT = [
  ['text-primary', 'bg-text-primary'],
  ['text-secondary', 'bg-text-secondary'],
  ['text-tertiary', 'bg-text-tertiary'],
  ['text-brand', 'bg-text-brand'],
  ['text-disabled', 'bg-text-disabled'],
  ['text-success', 'bg-text-success'],
  ['text-warning', 'bg-text-warning'],
  ['text-danger', 'bg-text-danger'],
  ['text-info', 'bg-text-info'],
  ['text-accent', 'bg-text-accent'],
  ['text-on-hero-muted', 'bg-text-on-hero-muted'],
  ['text-on-hero-accent', 'bg-text-on-hero-accent'],
] as const

const BORDER = [
  ['border-default', 'bg-border-default'],
  ['border-strong', 'bg-border-strong'],
  ['border-brand', 'bg-border-brand'],
  ['border-focus', 'bg-border-focus'],
  ['border-danger', 'bg-border-danger'],
  ['border-accent', 'bg-border-accent'],
] as const

function Swatches({
  title,
  items,
}: {
  title: string
  items: readonly (readonly [string, string])[]
}) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-heading-s text-text-primary">{title}</h2>
      <div className="grid grid-cols-[repeat(auto-fill,minmax(140px,1fr))] gap-3">
        {items.map(([name, className]) => (
          <div key={name} className="flex flex-col gap-1.5">
            <div className={`h-14 rounded-md inset-ring inset-ring-border-default ${className}`} />
            <span className="text-caption text-text-secondary">{name}</span>
          </div>
        ))}
      </div>
    </section>
  )
}

const TYPE = [
  ['display-xl', 'font-display text-display-xl', '21.0975'],
  ['display-l', 'font-display text-display-l', 'SEOUL HALF'],
  ['display-m', 'font-display text-display-m', '₩45,000'],
  ['display-s', 'font-display text-display-s', '5 / 8'],
  ['eyebrow', 'font-display text-eyebrow', 'SEOUL · 2026.11.15 SUN'],
  ['heading-xl', 'text-heading-xl', '서울 하프 마라톤'],
  ['heading-l', 'text-heading-l', '크루 결제 현황'],
  ['heading-m', 'text-heading-m', '티셔츠 사이즈'],
  ['heading-s', 'text-heading-s', '하프 코스 · 21.0975km'],
  ['body-l', 'text-body-l', '크루원 전원이 결제를 마쳐야 단체 접수가 확정됩니다.'],
  ['body-m', 'text-body-m', '크루원 전원이 결제를 마쳐야 단체 접수가 확정됩니다.'],
  ['body-s', 'text-body-s', '크루원 전원이 결제를 마쳐야 단체 접수가 확정됩니다.'],
  ['caption', 'text-caption', '마감까지 3일 남았습니다'],
  ['label-l', 'text-label-l', '접수하기'],
  ['label-m', 'text-label-m', '접수하기'],
  ['label-s', 'text-label-s', '접수하기'],
  ['label-strong', 'text-label-strong', '결제 완료'],
  ['label-tag', 'text-label-tag', '마감 임박'],
] as const

const meta = {
  title: 'Foundations/Tokens',
  parameters: { layout: 'padded' },
} satisfies Meta

export default meta
type Story = StoryObj<typeof meta>

/** 화면에서는 의미 색만 쓴다. 원색(brand-500 등)은 의미 색이 가리키는 값이다. */
export const Colors: Story = {
  render: () => (
    <div className="flex flex-col gap-8">
      <Swatches title="배경 (bg-*)" items={BG} />
      <Swatches title="글자 (text-*)" items={TEXT} />
      <Swatches title="테두리 (border-*)" items={BORDER} />
    </div>
  ),
}

/** 크기·줄 높이는 화면 폭(모바일·태블릿·데스크톱)에 따라 바뀐다. 툴바 뷰포트로 확인한다. */
export const Typography: Story = {
  render: () => (
    <div className="flex flex-col divide-y divide-border-default">
      {TYPE.map(([name, className, sample]) => (
        <div key={name} className="flex items-baseline gap-6 py-3">
          <span className="w-28 shrink-0 text-caption text-text-tertiary">{name}</span>
          <span className={`min-w-0 truncate text-text-primary ${className}`}>{sample}</span>
        </div>
      ))}
    </div>
  ),
}

export const RadiusAndShadow: Story = {
  render: () => (
    <div className="flex flex-wrap gap-6">
      {[
        ['radius-sm', 'rounded-sm'],
        ['radius-md', 'rounded-md'],
        ['radius-lg', 'rounded-lg'],
        ['radius-xl', 'rounded-xl'],
      ].map(([name, className]) => (
        <div key={name} className="flex flex-col items-center gap-2">
          <div
            className={`size-20 bg-bg-brand-subtle inset-ring inset-ring-border-brand ${className}`}
          />
          <span className="text-caption text-text-secondary">{name}</span>
        </div>
      ))}
      {[
        ['shadow-sm', 'shadow-sm'],
        ['shadow-md', 'shadow-md'],
        ['shadow-lg', 'shadow-lg'],
      ].map(([name, className]) => (
        <div key={name} className="flex flex-col items-center gap-2">
          <div className={`size-20 rounded-lg bg-bg-surface ${className}`} />
          <span className="text-caption text-text-secondary">{name}</span>
        </div>
      ))}
    </div>
  ),
}
