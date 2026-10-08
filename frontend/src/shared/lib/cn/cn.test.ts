import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

import { cn, TEXT_STYLES } from './cn'

describe('cn', () => {
  it('글자 스타일과 글자 색을 함께 남긴다', () => {
    expect(cn('text-heading-xl', 'text-text-primary')).toBe('text-heading-xl text-text-primary')
  })

  it('같은 종류끼리는 뒤의 클래스만 남긴다', () => {
    expect(cn('text-body-m text-text-primary', 'text-heading-xl text-text-danger')).toBe(
      'text-heading-xl text-text-danger',
    )
    expect(cn('bg-bg-surface shadow-sm rounded-md', 'bg-bg-brand shadow-lg rounded-xl')).toBe(
      'bg-bg-brand shadow-lg rounded-xl',
    )
  })

  it('조건부 클래스를 받는다', () => {
    expect(cn('text-text-primary', { 'text-text-disabled': true }, false, undefined)).toBe(
      'text-text-disabled',
    )
  })

  it('TEXT_STYLES가 globals.css의 --text-* 토큰과 같다', () => {
    const css = readFileSync(resolve(__dirname, '../../../app/globals.css'), 'utf8')
    // --text-heading-xl--line-height 같은 하위 속성과 --text-*: initial은 빼고 이름만 모은다
    const tokens = new Set(
      [...css.matchAll(/--text-([a-z0-9]+(?:-[a-z0-9]+)*):/g)].map((m) => m[1]),
    )

    expect([...tokens].sort()).toEqual([...TEXT_STYLES].sort())
  })
})
