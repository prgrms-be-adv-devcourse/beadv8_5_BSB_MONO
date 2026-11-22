import { describe, expect, it } from 'vitest'

import {
  defaultKitValues,
  KIT_ITEMS,
  KIT_VALUE_CHOICES,
  kitItemOf,
  kitOptionsFor,
} from './kit-items'
import { RACE_FORM_DEFAULTS, raceFormSchema } from './race-form'
import { SAMPLE_COURSES } from './race-form.fixtures'

describe('kitOptionsFor 키트 품목 → 서버가 받는 옵션 (이름 + 값 + 준비 수량)', () => {
  it('고를 수 있는 품목은 티셔츠 · 메달 · 양말 · 텀블러다', () => {
    expect(KIT_ITEMS).toEqual(['티셔츠', '메달', '양말', '텀블러'])
  })

  it('옵션 값 칩은 티셔츠 S~XXL, 양말 남자 · 여자뿐이다 (메달 · 텀블러는 값 칩이 없다)', () => {
    expect(KIT_VALUE_CHOICES).toEqual({
      티셔츠: ['S', 'M', 'L', 'XL', 'XXL'],
      양말: ['남자', '여자'],
    })
  })

  it('처음 선택은 티셔츠 다섯 개 모두, 양말은 아무것도 없다', () => {
    expect(defaultKitValues('티셔츠')).toEqual(['S', 'M', 'L', 'XL', 'XXL'])
    expect(defaultKitValues('양말')).toEqual([])
    expect(defaultKitValues('메달')).toEqual([])
    expect(defaultKitValues('텀블러')).toEqual([])
  })

  it('고른 값마다 한 줄이 된다 (양말 남자 · 여자 두 줄)', () => {
    expect(kitOptionsFor('양말', { values: ['남자', '여자'] })).toEqual([
      { name: '양말', value: '남자', stock: '' },
      { name: '양말', value: '여자', stock: '' },
    ])
    expect(kitOptionsFor('티셔츠', { values: ['M', 'XL'] })).toEqual([
      { name: '티셔츠', value: 'M', stock: '' },
      { name: '티셔츠', value: 'XL', stock: '' },
    ])
  })

  it('준비 수량은 모든 줄에 같게 넣고, 비우면 맞춤 제작으로 비워 둔다', () => {
    expect(
      kitOptionsFor('티셔츠', { values: ['S', 'M'], stock: '100' }).map((o) => o.stock),
    ).toEqual(['100', '100'])
    expect(kitOptionsFor('티셔츠', { values: ['S'] })[0].stock).toBe('')
  })

  it('메달은 각인 여부로 값이 갈린다 (서버는 옵션 값이 비면 막으므로 각인 없음도 값으로 보낸다)', () => {
    expect(kitOptionsFor('메달', { engraved: true })).toEqual([
      { name: '메달', value: '각인', stock: '' },
    ])
    expect(kitOptionsFor('메달', { engraved: false })).toEqual([
      { name: '메달', value: '각인 없음', stock: '' },
    ])
  })

  it('텀블러는 옵션 값 없이 「포함」 한 줄이다', () => {
    expect(kitOptionsFor('텀블러')).toEqual([{ name: '텀블러', value: '포함', stock: '' }])
  })

  it('만든 옵션은 서버 규칙(이름 · 값 필수, 같은 옵션 두 번 금지)을 통과한다', () => {
    const kitOptions = [
      ...kitOptionsFor('티셔츠', { values: defaultKitValues('티셔츠'), stock: '50' }),
      ...kitOptionsFor('양말', { values: ['남자', '여자'] }),
      ...kitOptionsFor('메달', { engraved: true }),
      ...kitOptionsFor('텀블러'),
    ]
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
    expect(kitItemOf('티셔츠')).toBe('티셔츠')
    expect(kitItemOf(' 양말 ')).toBe('양말')
    expect(kitItemOf('신발')).toBeUndefined()
  })
})
