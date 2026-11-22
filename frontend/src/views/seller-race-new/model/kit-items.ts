import type { KitOptionInput } from './race-form'

/** 키트 옵션 추가 창에서 고르는 품목 (옵션 이름) */
export const KIT_ITEMS = ['티셔츠', '메달', '양말', '텀블러'] as const
export type KitItem = (typeof KIT_ITEMS)[number]

/** 옵션 값을 칩으로 고르는 품목. 메달은 각인 체크, 텀블러는 값이 없다 */
export const KIT_VALUE_CHOICES = {
  티셔츠: ['S', 'M', 'L', 'XL', 'XXL'],
  양말: ['남자', '여자'],
} as const

/** 품목을 고른 직후의 선택. 티셔츠는 다섯 개 모두, 나머지는 아무것도 고르지 않은 상태 */
export function defaultKitValues(item: KitItem): string[] {
  return item === '티셔츠' ? [...KIT_VALUE_CHOICES.티셔츠] : []
}

/**
 * 품목 → 서버 키트 옵션(이름 + 값 + 준비 수량). 서버는 옵션 값이 비면 막으므로 값이 없는 품목도 값을 하나 붙인다.
 * 준비 수량을 비우면 맞춤 제작(서버 stock null)이다. 숫자를 쓰면 고른 모든 줄에 같게 넣는다.
 * - 티셔츠 · 양말: 고른 값마다 한 줄
 * - 메달: 각인 여부를 값(「각인」·「각인 없음」)으로 보낸다
 * - 텀블러: 값 「포함」 한 줄
 */
export function kitOptionsFor(
  item: KitItem,
  {
    values = [],
    engraved = false,
    stock = '',
  }: { values?: string[]; engraved?: boolean; stock?: string } = {},
): KitOptionInput[] {
  const option = (value: string): KitOptionInput => ({ name: item, value, stock })
  if (item === '메달') return [option(engraved ? '각인' : '각인 없음')]
  if (item === '텀블러') return [option('포함')]
  return values.map(option)
}

/** 옵션 이름이 어느 품목인지 (직접 쓴 이름이면 undefined) */
export function kitItemOf(name: string): KitItem | undefined {
  return KIT_ITEMS.find((item) => item === name.trim())
}
