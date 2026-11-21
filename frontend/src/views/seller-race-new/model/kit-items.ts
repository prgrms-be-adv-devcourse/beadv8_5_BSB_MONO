import type { KitOptionInput } from './race-form'

/** 키트 추가 창에서 고르는 품목 */
export const KIT_ITEMS = ['티셔츠', '메달', '양말', '텀블러'] as const
export type KitItem = (typeof KIT_ITEMS)[number]

const T_SHIRT_SIZES = ['S', 'M', 'L', 'XL', 'XXL']

/**
 * 품목 → 서버 키트 옵션(이름 + 값 + 준비 수량). 서버는 옵션 값이 비면 막으므로 옵션이 없는 품목도 값을 하나 붙인다.
 * 준비 수량은 모두 비워 맞춤 제작으로 둔다.
 * - 메달: 각인 여부를 값(「각인」·「각인 없음」)으로 보낸다
 * - 양말 · 텀블러: 값 「포함」 하나
 */
export function kitOptionsFor(
  item: KitItem,
  { engraved = false }: { engraved?: boolean } = {},
): KitOptionInput[] {
  const option = (value: string): KitOptionInput => ({ name: item, value, stock: '' })
  if (item === '티셔츠') return T_SHIRT_SIZES.map(option)
  if (item === '메달') return [option(engraved ? '각인' : '각인 없음')]
  return [option('포함')]
}

/** 옵션 이름이 어느 품목인지 (직접 쓴 이름이면 undefined) */
export function kitItemOf(name: string): KitItem | undefined {
  return KIT_ITEMS.find((item) => item === name.trim())
}
