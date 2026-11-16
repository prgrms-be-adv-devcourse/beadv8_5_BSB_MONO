'use client'

import { cn, copyText } from '@/shared/lib'
import { BottomSheet, FOCUS_RING, Icon, useToast, type IconName } from '@/shared/ui'

export type RaceShareSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  /** 공유할 대회 상세 주소 (https://… 전체) */
  url: string
}

// 피그마 「대회 공유 시트」: 카카오톡 · 링크 복사 · 다른 앱. 대회 상세와 대회 찾기 더보기가 같이 쓴다.
// 카카오톡 · 다른 앱은 기기 공유 창(navigator.share)을 연다. 공유 창이 없으면 링크를 복사한다.
export function RaceShareSheet({ open, onOpenChange, title, url }: RaceShareSheetProps) {
  const toast = useToast()

  async function copyLink() {
    onOpenChange(false)
    if (await copyText(url)) toast({ message: '링크를 복사했어요', tone: 'success' })
    else toast({ message: '복사하지 못했어요. 잠시 뒤 다시 시도해 주세요.', tone: 'error' })
  }

  function share() {
    if (typeof navigator.share !== 'function') return void copyLink()
    onOpenChange(false)
    // 사용자가 공유 창을 닫으면 거절로 끝난다. 알릴 일이 아니다
    void navigator.share({ title, url }).catch(() => undefined)
  }

  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} title="대회 공유하기">
      <div className="flex justify-center gap-8 px-2 py-4">
        <ShareAction icon="Kakao" label="카카오톡" onClick={share} />
        <ShareAction icon="Copy" label="링크 복사" onClick={() => void copyLink()} />
        <ShareAction icon="Share" label="다른 앱" onClick={share} />
      </div>
    </BottomSheet>
  )
}

function ShareAction({
  icon,
  label,
  onClick,
}: {
  icon: IconName | 'Kakao'
  label: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn('flex flex-col items-center gap-2 rounded-md', FOCUS_RING)}
    >
      {icon === 'Kakao' ? (
        // 카카오 노란 바탕은 로그인 버튼과 같은 가이드 색이다
        <span className="flex size-13 items-center justify-center rounded-full bg-[#FEE500] text-[#191919]">
          <svg viewBox="0 0 24 24" aria-hidden className="size-5 fill-current">
            <path d="M12 3C6.5 3 2 6.6 2 11c0 2.8 1.8 5.3 4.6 6.7l-1 3.6c-.1.3.2.5.5.4l4.2-2.8c.6.1 1.1.1 1.7.1 5.5 0 10-3.6 10-8S17.5 3 12 3z" />
          </svg>
        </span>
      ) : (
        <span className="flex size-13 items-center justify-center rounded-full bg-bg-subtle">
          <Icon name={icon} className="size-5" />
        </span>
      )}
      <span className="text-caption-medium">{label}</span>
    </button>
  )
}
