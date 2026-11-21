'use client'

import { cn, copyText } from '@/shared/lib'
import { BottomSheet, FOCUS_RING, Icon, useToast } from '@/shared/ui'

export type RaceShareSheetProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 공유할 대회 상세 주소 (https://… 전체) */
  url: string
}

// 피그마 「대회 공유 시트」: 링크 복사 하나. 대회 상세가 쓴다.
export function RaceShareSheet({ open, onOpenChange, url }: RaceShareSheetProps) {
  const toast = useToast()

  async function copyLink() {
    if (await copyText(url)) toast({ message: '링크를 복사했어요', tone: 'success' })
    else toast({ message: '복사하지 못했어요. 잠시 뒤 다시 시도해 주세요.', tone: 'error' })
  }

  return (
    <BottomSheet open={open} onOpenChange={onOpenChange} title="대회 공유하기">
      <div className="flex justify-center px-2 py-4">
        <button
          type="button"
          onClick={() => void copyLink()}
          className={cn('flex flex-col items-center gap-2 rounded-md', FOCUS_RING)}
        >
          <span className="flex size-13 items-center justify-center rounded-full border border-border-default bg-bg-surface">
            <Icon name="Copy" className="size-5.5" />
          </span>
          <span className="text-caption-regular text-text-secondary">링크 복사</span>
        </button>
      </div>
    </BottomSheet>
  )
}
