import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

import { renderTokensCss, stalePending } from './render.mjs'

const figma = JSON.parse(readFileSync('design-tokens/figma-variables.json', 'utf8'))
const css = renderTokensCss(figma)

describe('renderTokensCss', () => {
  it('커밋된 tokens.css는 피그마 변수 JSON으로 다시 만든 결과와 같다 (손으로 고치지 않았다)', () => {
    expect(readFileSync('src/app/tokens.css', 'utf8')).toBe(css)
  })

  it('원색 · 의미 색 · 그림자 · 글자를 피그마 이름 그대로 옮긴다', () => {
    expect(css).toContain('  --color-brand-500: #2e62f5;')
    expect(css).toContain('  --color-bg-surface: var(--color-neutral-0);')
    expect(css).toContain('    --color-bg-surface: var(--color-navy-900);')
    expect(css).toContain('  --shadow-sm: 0 1px 2px 0 #14213d0f, 0 1px 3px 0 #14213d14;')
    expect(css).toContain('  --text-title1--letter-spacing: -0.025em;')
  })

  it('화면 폭에 따라 바뀌는 값은 앞 단계와 다를 때만 적는다', () => {
    const tablet = css.slice(
      css.indexOf('@media (width >= 48rem)'),
      css.indexOf('@media (width >= 80rem)'),
    )
    expect(tablet).toContain('--text-title1: 32px;')
    // Title3은 Mobile과 Tablet이 20px로 같다
    expect(tablet).not.toContain('--text-title3:')
  })

  it('글자 변수는 그 변수를 쓰는 텍스트 스타일 이름으로 옮긴다', () => {
    // typography/heading-s를 Body1/Bold가 쓴다
    expect(css).toContain('      --text-body1-bold: 18px;')
    expect(css).not.toContain('--text-heading-s')
  })

  it('데스크톱에서도 제목 · 본문 크기 순서가 뒤집히지 않는다 (PENDING)', () => {
    const desktop = css.slice(css.indexOf('@media (width >= 80rem)'), css.indexOf('.app-mobile'))
    expect(desktop).not.toContain('--text-title2: 18px;')
    expect(desktop).not.toContain('--text-body1-regular: 14px;')
    expect(desktop).toContain('--text-body1-regular: 16px;')
  })

  it('글자 변수 하나를 두 스타일이 쓰면 만들지 않고 멈춘다', () => {
    const shared = structuredClone(figma)
    const medium = shared.textStyles.find((s: { name: string }) => s.name === 'Body1/Medium')
    medium.bound = { fontSize: 'typography/heading-s/size' }
    expect(() => renderTokensCss(shared)).toThrow('typography/heading-s/size')
  })

  it('보관 텍스트 스타일과 그 글자 변수는 옮기지 않는다', () => {
    expect(css).not.toContain('--text-eyebrow')
    expect(css).not.toContain('display-s')
    expect(css).not.toContain('label-tag')
  })

  it('단계가 숫자가 아닌 원색도 이름 그대로 옮긴다', () => {
    expect(css).toContain('  --color-chip-success-bg: #e5f5ea;')
    expect(css).toContain('  --color-bg-badge-success: var(--color-chip-success-bg);')
    expect(css).not.toContain('NaN')
  })

  it('max-width 9999는 제한 없음(none)으로 쓴다', () => {
    expect(css).toContain('    --layout-form-max-width: none;')
  })
})

describe('stalePending', () => {
  it('지금 피그마 값은 PENDING 표와 같아 경고가 없다', () => {
    expect(stalePending(figma)).toEqual([])
  })

  it('피그마 값이 바뀌거나 웹 값과 같아지거나 없어지면 알려 준다', () => {
    const pending = {
      color: {
        'color/text/info': { Light: { web: 'violet/700', figma: 'navy/700' } },
        'color/bg/segment': { Dark: { web: 'navy/900', figma: 'navy/700' } },
      },
      layout: { 'layout/app-max-width': { web: 640, figma: 480 } },
      spacing: { 'spacing/0-5': { web: 2, figma: 8 } },
      typography: { 'typography/heading-l/size': { Desktop: { web: 24, figma: 20 } } },
      skipTextStyles: ['Label/L Strong'],
    }

    expect(stalePending(figma, pending)).toEqual([
      'color/text/info (Light): 피그마 값이 웹 값(violet/700)과 같아져 PENDING이 필요 없습니다',
      'color/bg/segment (Dark): 피그마 값이 navy/700에서 navy/800로 바뀌었습니다',
      'layout/app-max-width: 피그마 값이 480에서 600로 바뀌었습니다',
      'spacing/0-5: 피그마 값이 8에서 4로 바뀌었습니다',
      'typography/heading-l/size (Desktop): 피그마 값이 20에서 18로 바뀌었습니다',
      '텍스트 스타일 Label/L Strong: 피그마에 없습니다',
    ])
  })
})
