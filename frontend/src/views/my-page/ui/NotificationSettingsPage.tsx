'use client'

import { AppHeader } from '@/widgets/app-header'
import { Switch } from '@/shared/ui'

// 피그마 v2 · 알림 설정(1459:15818). 계정 설정의 「알림 설정」 줄로 들어온다.
// TODO(api): 알림 설정 API가 아직 없다. 지금은 마이에 있던 때처럼 시안 값을 보여 준다.
export function NotificationSettingsPage() {
  return (
    <>
      <AppHeader type="sub" title="알림 설정" backHref="/me/settings" />
      <main className="flex flex-1 flex-col pb-6">
        <section className="flex flex-col px-4 pt-6">
          <h2 className="pb-1 text-caption-regular text-text-brand">알림 종류</h2>
          <SwitchRow label="대회 소식" defaultChecked />
          <SwitchRow
            label="결제 · 예치금"
            description="결제 알림은 끌 수 없어요"
            defaultChecked
            disabled
          />
          <SwitchRow label="크루 소식" defaultChecked />
        </section>
        <section className="flex flex-col px-4 pt-6">
          <h2 className="pb-1 text-caption-regular text-text-brand">광고</h2>
          <SwitchRow label="마케팅 정보 받기" defaultChecked />
          <SwitchRow label="밤 9시~아침 8시 광고 받기" />
        </section>
      </main>
    </>
  )
}

function SwitchRow({
  label,
  description,
  defaultChecked,
  disabled,
}: {
  label: string
  description?: string
  defaultChecked?: boolean
  disabled?: boolean
}) {
  return (
    <div className="flex items-center justify-between gap-4 border-b border-border-default py-4 last:border-b-0">
      <div className="flex flex-col gap-0.5">
        <span className="text-body1-medium">{label}</span>
        {description && (
          <span className="text-caption-regular text-text-secondary">{description}</span>
        )}
      </div>
      <Switch aria-label={label} defaultChecked={defaultChecked} disabled={disabled} />
    </div>
  )
}
