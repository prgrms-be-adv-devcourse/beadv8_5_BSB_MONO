import type { ReactNode } from 'react'

import { ConsoleSidebar } from '@/widgets/console-sidebar'

export default function SellerLayout({ children }: { children: ReactNode }) {
  return (
    <div className="console-type flex min-h-dvh flex-1">
      <ConsoleSidebar role="seller" />
      {children}
    </div>
  )
}
