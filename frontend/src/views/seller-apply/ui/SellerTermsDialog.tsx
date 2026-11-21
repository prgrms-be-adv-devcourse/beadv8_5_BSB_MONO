'use client'

import { Dialog } from '@base-ui/react/dialog'

import { Button, buttonClassName } from '@/shared/ui'

// shortcut: 판매자 이용약관 본문 원본이 레포에 없어 시안(2070:72355)처럼 조 제목과 빈 줄만 그린다. 본문이 정해지면 줄 대신 글을 넣는다
const ARTICLES = ['제1조 목적', '제2조 판매자의 의무', '제3조 정산과 지급', '제4조 대회 취소']

// 피그마 판매자 신청 · 판매자 이용약관 창(2070:72277): 폭 560, 제목 가운데, 약관 상자 높이 440, 확인 버튼 하나
// 공용 Dialog는 제목과 본문 사이가 8이라 시안(24)과 달라 base-ui 창을 직접 쓴다
export function SellerTermsDialog() {
  return (
    <Dialog.Root>
      <Dialog.Trigger className={buttonClassName({ variant: 'secondary', size: 's' })}>
        보기
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-bg-overlay/45" />
        <Dialog.Popup className="fixed top-1/2 left-1/2 z-50 flex max-h-[calc(100dvh-32px)] w-140 max-w-[calc(100vw-32px)] -translate-1/2 flex-col gap-6 rounded-lg bg-bg-surface p-6 shadow-lg outline-none">
          <Dialog.Title className="text-center text-title3 text-text-primary">
            판매자 이용약관
          </Dialog.Title>
          <div className="flex h-110 min-h-0 flex-col gap-4 overflow-y-auto rounded-sm p-6 inset-ring inset-ring-border-default">
            {ARTICLES.map((article) => (
              <section key={article} className="flex flex-col gap-2">
                <h3 className="text-body1-bold text-text-primary">{article}</h3>
                <div aria-hidden className="h-3 w-full rounded-[4px] bg-bg-muted" />
                <div aria-hidden className="h-3 w-full rounded-[4px] bg-bg-muted" />
                <div aria-hidden className="h-3 w-75 rounded-[4px] bg-bg-muted" />
              </section>
            ))}
          </div>
          <Dialog.Close render={<Button size="l" className="w-full" />}>확인했어요</Dialog.Close>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
