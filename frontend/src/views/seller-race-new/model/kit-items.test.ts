import { describe, expect, it } from 'vitest'

import { KIT_ITEMS, kitItemOf, kitOptionsFor } from './kit-items'
import { RACE_FORM_DEFAULTS, raceFormSchema } from './race-form'
import { SAMPLE_COURSES } from './race-form.fixtures'

describe('kitOptionsFor 키트 품목 → 서버가 받는 옵션 (이름 + 값 + 준비 수량)', () => {
  it('고를 수 있는 품목은 티셔츠 · 메달 · 양말 · 텀블러다', () => {
    expect(KIT_ITEMS).toEqual(['티셔츠', '메달', '양말', '텀블러'])
  })

  it('티셔츠는 S · M · L · XL · XXL 다섯 줄이고 준비 수량을 비워 맞춤 제작으로 둔다', () => {
    expect(kitOptionsFor('티셔츠')).toEqual(
      ['S', 'M', 'L', 'XL', 'XXL'].map((value) => ({ name: '티셔츠', value, stock: '' })),
    )
  })

  it('메달은 각인 여부로 값이 갈린다 (서버는 옵션 값이 비면 막으므로 각인 없음도 값으로 보낸다)', () => {
    expect(kitOptionsFor('메달', { engraved: true })).toEqual([
      { name: '메달', value: '각인', stock: '' },
    ])
    expect(kitOptionsFor('메달', { engraved: false })).toEqual([
      { name: '메달', value: '각인 없음', stock: '' },
    ])
  })

  it('양말 · 텀블러는 옵션 값 없이 「포함」 한 줄이다', () => {
    expect(kitOptionsFor('양말')).toEqual([{ name: '양말', value: '포함', stock: '' }])
    expect(kitOptionsFor('텀블러')).toEqual([{ name: '텀블러', value: '포함', stock: '' }])
  })

  it('만든 옵션은 서버 규칙(이름 · 값 필수, 같은 옵션 두 번 금지)을 통과한다', () => {
    const kitOptions = KIT_ITEMS.flatMap((item) => kitOptionsFor(item, { engraved: true }))
    const result = raceFormSchema.safeParse({
      ...RACE_FORM_DEFAULTS,
      courses: SAMPLE_COURSES,
      kitOptions,
    })
    const messages = result.success
      ? []
      : result.error.issues.filter((i) => i.path[0] === 'kitOptions')
    expect(messages).toEqual([])
  })

  it('옵션 이름으로 어느 품목이 이미 들어 있는지 알아본다', () => {
    const options = [...kitOptionsFor('티셔츠'), ...kitOptionsFor('메달', { engraved: false })]
    expect(options.map((o) => kitItemOf(o.name))).toEqual([
      '티셔츠',
      '티셔츠',
      '티셔츠',
      '티셔츠',
      '티셔츠',
      '메달',
    ])
    expect(kitItemOf('신발')).toBeUndefined()
  })
})
