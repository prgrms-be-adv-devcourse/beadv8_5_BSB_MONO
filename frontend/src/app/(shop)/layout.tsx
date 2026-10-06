import { SiteHeader } from '@/widgets/site-header'

export default function ShopLayout({ children }: LayoutProps<'/'>) {
  return (
    <>
      <SiteHeader />
      {children}
    </>
  )
}
