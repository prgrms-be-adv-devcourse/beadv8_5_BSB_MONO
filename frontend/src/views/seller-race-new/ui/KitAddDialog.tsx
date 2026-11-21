'use client'

import { useState } from 'react'

import { Checkbox, Chip, Dialog, RequiredMark, TextField } from '@/shared/ui'

import {
  defaultKitValues,
  KIT_ITEMS,
  KIT_VALUE_CHOICES,
  kitOptionsFor,
  type KitItem,
} from '../model/kit-items'
import type { KitOptionInput } from '../model/race-form'
import { groupedDigits } from './grouped-digits'

const ERRORS = {
  item: '옵션 이름을 골라 주세요.',
  values: '옵션 값을 하나 이상 골라 주세요.',
}

export type KitAddDialogProps = {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 키트 표에 이미 들어 있는 품목. 같은 품목이 두 번 들어가지 않게 칩을 막는다 */
  added: KitItem[]
  onAdd: (options: KitOptionInput[]) => void
}

// 키트 옵션 추가 창. 옵션 이름 칩을 고르면 그 품목의 옵션 값 영역이 나오고, 추가하기로 고른 값마다 한 줄을 키트 표에 넣는다.
export function KitAddDialog({ open, onOpenChange, added, onAdd }: KitAddDialogProps) {
  const [item, setItem] = useState<KitItem | null>(null)
  const [values, setValues] = useState<string[]>([])
  const [engraved, setEngraved] = useState(false)
  const [stock, setStock] = useState('')
  const [error, setError] = useState<string>()

  // 닫으면 고르던 것을 모두 되돌린다. 다음에 열 때 앞의 선택이 남지 않게 한다
  function close(next: boolean) {
    if (!next) {
      setItem(null)
      setValues([])
      setEngraved(false)
      setStock('')
      setError(undefined)
    }
    onOpenChange(next)
  }

  function pick(next: KitItem) {
    setItem(next)
    setValues(defaultKitValues(next))
    setError(undefined)
  }

  function toggle(value: string) {
    setValues((prev) => (prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value]))
    setError(undefined)
  }

  function confirm() {
    if (!item) return setError(ERRORS.item)
    const choices = item === '티셔츠' || item === '양말' ? values : null
    if (choices && choices.length === 0) return setError(ERRORS.values)
    // 쉼표는 보기용이라 지운다. 비우면 맞춤 제작(서버 stock null)이다
    const digits = stock.replaceAll(',', '')
    // 칩 순서(S, M, L…)대로 넣는다
    const ordered = choices
      ? (KIT_VALUE_CHOICES[item as keyof typeof KIT_VALUE_CHOICES] as readonly string[]).filter(
          (v) => values.includes(v),
        )
      : []
    onAdd(kitOptionsFor(item, { values: ordered, engraved, stock: digits }))
    close(false)
  }

  const valueChoices = item === '티셔츠' || item === '양말' ? KIT_VALUE_CHOICES[item] : null

  return (
    <Dialog
      open={open}
      onOpenChange={close}
      title="키트 옵션 추가"
      confirmLabel="추가하기"
      cancelLabel="닫기"
      onConfirm={confirm}
      body={
        <div className="flex flex-col gap-4 pt-2">
          <div role="group" aria-labelledby="kit-item-label" className="flex flex-col gap-2">
            <span id="kit-item-label" className="text-body1-medium">
              옵션 이름
              <RequiredMark />
            </span>
            <div className="flex flex-wrap gap-2">
              {KIT_ITEMS.map((name) => (
                <Chip
                  key={name}
                  selected={item === name || added.includes(name)}
                  disabled={added.includes(name)}
                  onClick={() => pick(name)}
                >
                  {name}
                </Chip>
              ))}
            </div>
          </div>
          {item && (
            <div role="group" aria-labelledby="kit-value-label" className="flex flex-col gap-2">
              <span id="kit-value-label" className="text-body1-medium">
                옵션 값
                <RequiredMark />
              </span>
              {valueChoices && (
                <div className="flex flex-wrap gap-2">
                  {valueChoices.map((value) => (
                    <Chip
                      key={value}
                      selected={values.includes(value)}
                      onClick={() => toggle(value)}
                    >
                      {value}
                    </Chip>
                  ))}
                </div>
              )}
              {item === '메달' && (
                <Checkbox label="각인" checked={engraved} onCheckedChange={setEngraved} />
              )}
              {item === '텀블러' && (
                <p className="text-body2-regular text-text-secondary">키트에 포함됨</p>
              )}
            </div>
          )}
          {item && (
            <TextField
              label="준비 수량"
              inputMode="numeric"
              placeholder="맞춤 제작"
              filter={groupedDigits}
              trailing="개"
              value={stock}
              onChange={(event) => setStock(event.target.value)}
            />
          )}
          {error && <p className="text-caption-regular text-text-danger">{error}</p>}
        </div>
      }
    />
  )
}
