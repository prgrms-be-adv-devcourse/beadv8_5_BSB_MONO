import Link from 'next/link'

export function SiteHeader() {
  return (
    <header className="border-b border-border-default">
      <nav className="mx-auto flex w-full max-w-(--layout-content-max-width) items-center gap-4 px-4 py-3 text-label-m">
        <Link href="/" className="font-bold">
          running
        </Link>
        <Link href="/races">대회</Link>
        <Link href="/cart" className="ml-auto">
          장바구니
        </Link>
        <Link href="/me">마이</Link>
        <Link href="/login">로그인</Link>
      </nav>
    </header>
  )
}
