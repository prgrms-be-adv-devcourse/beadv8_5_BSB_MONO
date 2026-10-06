import type { ReactNode } from 'react'

export default function SellerLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-full flex-1 flex-col md:flex-row">
      <aside className="border-b border-neutral-200 px-4 py-3 text-sm font-bold md:w-56 md:border-r md:border-b-0">
        판매자 센터
      </aside>
      <div className="flex-1">{children}</div>
    </div>
  )
}
