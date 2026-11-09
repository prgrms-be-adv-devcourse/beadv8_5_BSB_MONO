import { AppHeader } from '@/widgets/app-header'

import { TERMS, TERMS_EFFECTIVE_DATE, TERMS_VERSION, type TermsSlug } from '../model/terms'

// 피그마 V2 · M04-T 약관 전문. 조회 전용이다. 동의는 가입 3단계의 약관 동의 시트에서만 받는다.
export function TermsPage({ slug }: { slug: TermsSlug }) {
  const terms = TERMS[slug]
  return (
    <>
      <AppHeader type="sub" title={terms.title} />
      <main className="flex flex-1 flex-col gap-5 px-4 py-6">
        <div className="flex flex-col gap-1">
          <h2 className="text-title2">{terms.heading}</h2>
          <p className="text-caption-regular text-text-tertiary">
            시행일 {TERMS_EFFECTIVE_DATE} · 버전 {TERMS_VERSION}
          </p>
        </div>
        {terms.articles.map(([title, body]) => (
          <section key={title} className="flex flex-col gap-1.5">
            <h3 className="text-body1-bold">{title}</h3>
            <p className="text-body2-regular text-text-secondary">{body}</p>
          </section>
        ))}
      </main>
    </>
  )
}
