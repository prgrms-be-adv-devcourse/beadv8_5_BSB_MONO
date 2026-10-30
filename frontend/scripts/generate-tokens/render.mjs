// 피그마 변수 JSON(design-tokens/figma-variables.json)으로 웹 토큰 CSS(src/app/tokens.css)를 만든다.
// 이름은 피그마 이름의 '/'를 '-'로 바꿔 그대로 쓴다. (color/bg/surface → --color-bg-surface → bg-bg-surface)

/**
 * 피그마와 값이 아직 다른 토큰. 화면이 바뀌지 않도록 지금 웹 값(web)을 유지한다.
 * figma는 이 표를 만들 때의 피그마 값이다. 피그마가 이 값에서 바뀌거나 웹 값과 같아지면
 * `pnpm tokens`가 경고한다(stalePending). 피그마를 웹 값으로 고치거나 웹을 피그마 값으로 바꾸기로 정하면 여기서 지운다.
 */
export const PENDING = {
  /** 의미 색 Light · Dark 모드에서 피그마 원색 대신 쓸 원색. 예: 'color/text/info': { Dark: { web: 'violet/300', figma: 'navy/700' } } */
  color: {},
  /** 레이아웃 값(px). 예: 'layout/app-max-width': { web: 600, figma: 480 } (피그마 값은 모든 모드에서 같을 때) */
  layout: {},
  /** 아직 웹에 없는 텍스트 스타일 */
  skipTextStyles: ['Label/L Strong'],
}

/** PENDING 중 다시 확인해야 할 것. 피그마 값이 표의 figma와 달라졌거나, 피그마 스타일이 없어졌을 때 */
export function stalePending(figma, pending = PENDING) {
  const warnings = []
  const check = (label, actual, { web, figma: recorded }) => {
    if (actual === undefined) warnings.push(`${label}: 피그마에 없습니다`)
    else if (actual === web)
      warnings.push(`${label}: 피그마 값이 웹 값(${web})과 같아져 PENDING이 필요 없습니다`)
    else if (actual !== recorded)
      warnings.push(`${label}: 피그마 값이 ${recorded}에서 ${actual}로 바뀌었습니다`)
  }
  const colors = figma.collections.Color.variables
  for (const [name, modes] of Object.entries(pending.color)) {
    for (const [mode, entry] of Object.entries(modes)) {
      check(`${name} (${mode})`, colors[name]?.values[mode]?.alias, entry)
    }
  }
  const layout = figma.collections.Layout.variables
  for (const [name, entry] of Object.entries(pending.layout)) {
    const values = new Set(Object.values(layout[name]?.values ?? {}))
    const actual = values.size === 1 ? [...values][0] : layout[name] && [...values].join('·')
    check(name, actual, entry)
  }
  const styles = new Set(figma.textStyles.map((s) => s.name))
  for (const name of pending.skipTextStyles) {
    if (!styles.has(name)) warnings.push(`텍스트 스타일 ${name}: 피그마에 없습니다`)
  }
  return warnings
}

// 피그마에만 필요한 값이라 옮기지 않는다
const SKIP_LAYOUT = ['layout/frame-width']
// 600 기둥(.app-mobile) 안에서 Mobile 값으로 되돌리지 않는 값. 열 수는 기둥 안에서 쓰지 않는다
const APP_MOBILE_SKIP = ['layout/columns']
// max-width 값이 이 숫자면 제한이 없다는 뜻이다
const NO_LIMIT = 9999

const kebab = (name) => name.replaceAll('/', '-').replaceAll(' ', '-').toLowerCase()
const px = (n) => (n === 0 ? '0' : `${n}px`)
const rem = (n) => `${n / 16}rem`
const em = (percent) => (percent === 0 ? '0' : `${percent / 100}em`)
const comment = (text, indent) => (text ? [`${indent}/* ${text} */`] : [])

function colorRef(alias) {
  return `var(--color-${kebab(alias)})`
}

// 원색은 색 이름이 처음 나온 순서로 묶고, 같은 색 안에서는 단계 순으로 정렬한다
function primitives(vars) {
  const families = []
  const byFamily = {}
  for (const [name, v] of Object.entries(vars)) {
    const [family, step] = name.split('/')
    if (!byFamily[family]) {
      byFamily[family] = []
      families.push(family)
    }
    byFamily[family].push({ step: Number(step), value: v.values.Value })
  }
  return families.map((family) =>
    byFamily[family]
      .sort((a, b) => a.step - b.step)
      .map(({ step, value }) => `  --color-${family}-${step}: ${value};`),
  )
}

function semanticColors(vars, mode) {
  const lines = []
  for (const [name, v] of Object.entries(vars)) {
    const pending = PENDING.color[name]?.[mode]?.web
    const light = PENDING.color[name]?.Light?.web ?? v.values.Light.alias
    const value = pending ?? v.values[mode].alias
    // Dark 모드는 Light와 다른 값만 적는다
    if (mode === 'Dark' && value === light) continue
    const indent = mode === 'Dark' ? '    ' : '  '
    if (mode === 'Light') lines.push(...comment(v.description, indent))
    lines.push(`${indent}--${kebab(name)}: ${colorRef(value)};`)
  }
  return lines
}

function spacing(vars) {
  // 피그마 spacing/N = N × 4px이면 Tailwind 기본 배수(0.25rem = 4px)로 p-4처럼 피그마 번호를 그대로 쓴다
  for (const [name, v] of Object.entries(vars)) {
    const step = Number(name.split('/')[1].replace('-', '.'))
    if (v.values.Value !== step * 4)
      throw new Error(`${name}이 4px 배수가 아닙니다: ${v.values.Value}`)
  }
  return ['  --spacing: 0.25rem;']
}

function radius(vars) {
  // radius/none · radius/full은 Tailwind 기본 rounded-none · rounded-full을 쓴다
  return Object.entries(vars)
    .filter(([name]) => !['radius/none', 'radius/full'].includes(name))
    .map(([name, v]) => `  --${kebab(name)}: ${px(v.values.Value)};`)
}

function shadows(effectStyles) {
  return effectStyles.map((style) => {
    const value = style.shadows
      .map((s) => `${px(s.x)} ${px(s.y)} ${px(s.blur)} ${px(s.spread)} ${s.color}`)
      .join(', ')
    return `  --${kebab(style.name)}: ${value};`
  })
}

function textStyles(styles) {
  return styles
    .filter((s) => !PENDING.skipTextStyles.includes(s.name))
    .flatMap((s) => {
      const key = `--text-${kebab(s.name)}`
      return [
        `  ${key}: ${px(s.fontSize)};`,
        `  ${key}--line-height: ${px(s.lineHeight)};`,
        `  ${key}--letter-spacing: ${em(s.letterSpacingPercent)};`,
        `  ${key}--font-weight: ${s.fontWeight};`,
      ]
    })
}

function layoutValue(name, value) {
  const pending = PENDING.layout[name]?.web
  if (pending !== undefined) return px(pending)
  if (name === 'layout/columns') return String(value)
  if (name.endsWith('max-width') && value === NO_LIMIT) return 'none'
  return px(value)
}

/** 화면 폭에 따라 바뀌는 값. typography/heading-xl/size → --text-heading-xl */
function responsiveName(name) {
  if (name.startsWith('typography/')) {
    const [, style, prop] = name.split('/')
    return prop === 'size' ? `--text-${style}` : `--text-${style}--${prop}`
  }
  return `--${kebab(name)}`
}

function responsiveValue(name, value) {
  return name.startsWith('layout/') ? layoutValue(name, value) : px(value)
}

/** mode 값 중 앞 mode와 다른 것만. 앞 mode가 없으면 전부 */
function modeLines(entries, mode, previous, indent) {
  return entries
    .filter(([, v]) => previous === undefined || v.values[mode] !== v.values[previous])
    .map(
      ([name, v]) => `${indent}${responsiveName(name)}: ${responsiveValue(name, v.values[mode])};`,
    )
}

/** @param {any} figma figma-variables.json 내용 */
export function renderTokensCss(figma) {
  const c = figma.collections
  const layout = Object.entries(c.Layout.variables).filter(
    ([name]) => name.startsWith('layout/') && !SKIP_LAYOUT.includes(name),
  )
  const typography = Object.entries(c.Typography.variables)
  const varying = [...layout, ...typography].filter(
    ([, v]) => new Set(Object.values(v.values)).size > 1,
  )
  const tablet = c.Layout.variables['breakpoint/tablet'].values.Mobile
  const desktop = c.Layout.variables['breakpoint/desktop'].values.Mobile

  const theme = [
    '  --color-*: initial;',
    '  --text-*: initial;',
    '  --radius-*: initial;',
    '  --shadow-*: initial;',
    '  --breakpoint-*: initial;',
    '',
    '  /* 원색 (Primitives). 화면에서는 아래 의미 색을 쓰고, 원색은 의미 색이 가리키는 값으로만 쓴다. */',
    ...primitives(c.Primitives.variables).flatMap((group) => [...group, '']),
    '  /* 의미 색 (Color 컬렉션, Light 모드). Dark 모드 값은 아래 .dark에 있다. */',
    ...semanticColors(c.Color.variables, 'Light'),
    '',
    '  /* 간격 (Spacing 컬렉션). 피그마 spacing/N = N × 4px. p-4(16px), gap-0.5(2px)처럼 피그마 번호를 그대로 쓴다. */',
    ...spacing(c.Spacing.variables),
    '',
    '  /* 둥글기 (Radius 컬렉션). radius/none, radius/full은 Tailwind 기본 rounded-none, rounded-full을 쓴다. */',
    ...radius(c.Radius.variables),
    '',
    '  /* 그림자 (이펙트 스타일 Shadow/SM·MD·LG) */',
    ...shadows(figma.effectStyles),
    '',
    '  /* 화면 폭 기준 (Layout 컬렉션 breakpoint/*). tablet:, desktop: 으로 쓴다. */',
    `  --breakpoint-tablet: ${rem(tablet)};`,
    `  --breakpoint-desktop: ${rem(desktop)};`,
    '',
    '  /*',
    '   * 글자 (텍스트 스타일). text-heading-xl 하나로 크기·줄 높이·자간·굵기가 함께 들어간다.',
    '   * Display·Eyebrow는 피그마에서 Barlow Condensed라 font-display를 같이 붙인다.',
    '   * 여기 값은 모바일 기준이고, 화면 폭에 따라 바뀌는 값은 아래 @layer base에 있다.',
    '   */',
    ...textStyles(figma.textStyles),
  ]

  const base = [
    '  /* 의미 색 Dark 모드. Light와 같은 값인 토큰은 적지 않는다. */',
    '  .dark {',
    ...semanticColors(c.Color.variables, 'Dark'),
    '  }',
    '',
    '  /* 레이아웃 (Layout 컬렉션, Mobile 모드). max-w-(--layout-content-max-width)처럼 쓴다. */',
    '  :root {',
    ...modeLines(layout, 'Mobile', undefined, '    '),
    '  }',
    '',
    '  /* Typography·Layout 컬렉션의 Tablet 모드 */',
    `  @media (width >= ${rem(tablet)}) {`,
    '    :root {',
    ...modeLines(varying, 'Tablet', 'Mobile', '      '),
    '    }',
    '  }',
    '',
    '  /* Typography·Layout 컬렉션의 Desktop 모드 */',
    `  @media (width >= ${rem(desktop)}) {`,
    '    :root {',
    ...modeLines(varying, 'Desktop', 'Tablet', '      '),
    '    }',
    '  }',
    '',
    '  /* 회원 화면 600 기둥 안에서는 화면 폭과 상관없이 Typography·Layout 컬렉션의 Mobile 모드 값을 쓴다. */',
    '  .app-mobile {',
    ...modeLines(
      varying.filter(([name]) => !APP_MOBILE_SKIP.includes(name)),
      'Mobile',
      undefined,
      '    ',
    ),
    '  }',
  ]

  const pending = [
    ...Object.entries(PENDING.color).map(
      ([name, modes]) => `${name} (${Object.keys(modes).join(', ')})`,
    ),
    ...Object.keys(PENDING.layout),
    ...PENDING.skipTextStyles.map((name) => `텍스트 스타일 ${name}`),
  ]

  return [
    '/*',
    ' * 손으로 고치지 않음. `pnpm tokens`가 design-tokens/figma-variables.json으로 만든 파일이다.',
    ` * 원본: 피그마 '${figma.source.fileName}'(${figma.source.fileKey})의 변수 · 텍스트 스타일 · 이펙트 스타일.`,
    ' * 값을 바꾸려면 피그마 변수를 고치고 다시 내보낸 뒤 `pnpm tokens`를 돌린다.',
    ' * Tailwind 기본 색·글자 크기·둥글기·그림자·화면 폭 기준은 지워서 시안에 없는 값을 쓰지 못하게 한다.',
    ...(pending.length > 0
      ? [
          ' * 피그마와 값이 아직 다른 토큰은 웹 값을 유지한다(scripts/generate-tokens/render.mjs의 PENDING):',
          ...pending.map((p) => ` *   ${p}`),
        ]
      : []),
    ' */',
    '',
    '@theme {',
    ...theme,
    '}',
    '',
    '@layer base {',
    ...base,
    '}',
    '',
  ].join('\n')
}

/** FNV-1a 32비트. design-tokens/figma-export.js가 돌려주는 hash와 같은 방식이다 */
export function hashOf(figma) {
  const text = JSON.stringify(figma)
  let h = 0x811c9dc5
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 0x01000193) >>> 0
  }
  return h.toString(16)
}
