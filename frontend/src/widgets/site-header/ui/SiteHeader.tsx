import Link from 'next/link'

export function SiteHeader() {
  return (
    <header className="border-b border-neutral-200">
      <nav className="mx-auto flex w-full max-w-5xl items-center gap-4 px-4 py-3 text-sm">
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
