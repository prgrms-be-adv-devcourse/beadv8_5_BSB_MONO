import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

// globals.css의 --text-* 이름. tailwind-merge는 모르는 text-* 를 색으로 여겨서,
// 알려 주지 않으면 cn('text-heading-xl text-text-primary')에서 text-heading-xl이 지워진다.
// globals.css와 목록이 같은지는 cn.test.ts가 확인한다.
export const TEXT_STYLES = [
  'display-xl',
  'display-l',
  'display-m',
  'display-s',
  'eyebrow',
  'heading-xl',
  'heading-l',
  'heading-m',
  'heading-s',
  'body-l',
  'body-m',
  'body-s',
  'caption',
  'label-l',
  'label-m',
  'label-s',
  'label-strong',
  'label-tag',
]

const twMerge = extendTailwindMerge({
  extend: { theme: { text: TEXT_STYLES } },
})

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
