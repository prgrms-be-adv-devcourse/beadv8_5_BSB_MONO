import type { ReactNode } from 'react'

export default function SellerLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-full flex-1 flex-col tablet:flex-row">
      <aside className="border-b border-border-default px-4 py-3 text-label-strong tablet:w-56 tablet:border-r tablet:border-b-0">
        판매자 센터
      </aside>
      <div className="flex-1">{children}</div>
    </div>
  )
}
