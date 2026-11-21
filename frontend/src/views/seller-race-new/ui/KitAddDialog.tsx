'use client'

import { useState } from 'react'

import { Checkbox, Chip, Dialog } from '@/shared/ui'

import { KIT_ITEMS, kitOptionsFor, type KitItem } from '../model/kit-items'
import type { KitOptionInput } from '../model/race-form'

// 옵션 값이 없는 품목은 키트에 포함된다는 안내만 보여 준다
const OPTIONLESS: KitItem[] = ['양말', '텀블러']

export type KitAddDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 키트 표에 이미 들어 있는 품목. 같은 품목이 두 번 들어가지 않게 칩을 막는다 */
  added: KitItem[]
  onAdd: (options: KitOptionInput[]) => void
}

// 키트 추가 창. 칩을 누르면 그 품목의 옵션을 키트 표에 넣는다. 누르기 전에는 아무것도 넣지 않는다.
export function KitAddDialog({ open, onOpenChange, added, onAdd }: KitAddDialogProps) {
  const [engraved, setEngraved] = useState(false)
  // 닫으면 각인 체크를 되돌린다. 다음에 열 때 앞의 선택이 남아 메달이 각인으로 들어가지 않게 한다
  function close(next: boolean) {
    if (!next) setEngraved(false)
    onOpenChange(next)
  }
  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title="키트 추가"
      type="alert"
      confirmLabel="닫기"
      onConfirm={() => close(false)}
      body={
        <div className="flex flex-col gap-4 pt-2">
          <div className="flex flex-wrap gap-2">
            {KIT_ITEMS.map((item) => (
              <Chip
                key={item}
                selected={added.includes(item)}
                disabled={added.includes(item)}
                onClick={() => onAdd(kitOptionsFor(item, { engraved }))}
              >
                {item}
              </Chip>
            ))}
          </div>
          {!added.includes('메달') && (
            <Checkbox label="각인" checked={engraved} onCheckedChange={setEngraved} />
          )}
          {OPTIONLESS.filter((item) => added.includes(item)).map((item) => (
            <p key={item} className="flex gap-2 text-body2-regular text-text-secondary">
              <span>{item}</span>
              <span>키트에 포함됨</span>
            </p>
          ))}
        </div>
      }
    />
  )
}
