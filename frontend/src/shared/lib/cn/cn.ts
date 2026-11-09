import { clsx, type ClassValue } from 'clsx'
import { extendTailwindMerge } from 'tailwind-merge'

// tokens.css의 --text-* 이름. tailwind-merge는 모르는 text-* 를 색으로 여겨서,
// 알려 주지 않으면 cn('text-title1 text-text-primary')에서 text-title1이 지워진다.
// tokens.css와 목록이 같은지는 cn.test.ts가 확인한다.
export const TEXT_STYLES = [
  'number-l',
  'number-m',
  'number-s',
  'title1',
  'title2',
  'title3',
  'body1-bold',
  'body1-medium',
  'body1-regular',
  'body2-bold',
  'body2-medium',
  'body2-regular',
  'caption-medium',
  'caption-regular',
]

const twMerge = extendTailwindMerge({
  extend: { theme: { text: TEXT_STYLES } },
})

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}
