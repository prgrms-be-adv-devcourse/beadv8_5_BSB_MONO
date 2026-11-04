import type { ReactNode } from 'react'

import { ConsoleSidebar } from '@/widgets/console-sidebar'

export default function AdminLayout({ children }: { children: ReactNode }) {
  return (
    // 어드민은 Clarity가 기록을 멈추지만, 멈추지 못해도 회원 정보가 녹화되지 않게 전체를 가린다
    <div className="flex min-h-dvh flex-1" data-clarity-mask="true">
      <ConsoleSidebar role="admin" />
      {children}
    </div>
  )
}
