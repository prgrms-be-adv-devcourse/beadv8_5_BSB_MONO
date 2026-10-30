// 피그마 시안 파일의 변수 · 텍스트 스타일 · 이펙트 스타일을 figma-variables.json 형식으로 내보낸다.
// 읽기만 하고 피그마는 고치지 않는다.
// 실행: Figma MCP의 use_figma(fileKey bBzCk3M8SBW4sE4lo69MYg)에 이 파일 내용을 그대로 넘긴다.
// 돌려받은 json 값을 figma-variables.json에 저장하고 `pnpm tokens`를 돌린다.
// hash는 받은 json을 옮겨 적을 때 틀리지 않았는지 `pnpm tokens --check-hash <hash>`로 확인하는 값이다.

const SOURCE = { fileKey: 'bBzCk3M8SBW4sE4lo69MYg', fileName: '프론트엔드 시안' }
const WEIGHTS = {
  Regular: 400,
  Medium: 500,
  SemiBold: 600,
  'Semi Bold': 600,
  Bold: 700,
  ExtraBold: 800,
  'Extra Bold': 800,
}

const collections = await figma.variables.getLocalVariableCollectionsAsync()
const variables = await figma.variables.getLocalVariablesAsync()
const byId = Object.fromEntries(variables.map((v) => [v.id, v]))
const hex = (x) =>
  Math.round(x * 255)
    .toString(16)
    .padStart(2, '0')
const color = (c) =>
  '#' + hex(c.r) + hex(c.g) + hex(c.b) + (c.a !== undefined && c.a < 1 ? hex(c.a) : '')
const value = (v) => {
  if (v && typeof v === 'object' && v.type === 'VARIABLE_ALIAS') return { alias: byId[v.id].name }
  if (v && typeof v === 'object' && 'r' in v) return color(v)
  return v
}

const out = { source: SOURCE, collections: {}, textStyles: [], effectStyles: [] }
for (const c of collections) {
  const vars = {}
  for (const id of c.variableIds) {
    const v = byId[id]
    const values = {}
    for (const m of c.modes) values[m.name] = value(v.valuesByMode[m.modeId])
    vars[v.name] = {
      type: v.resolvedType,
      values,
      ...(v.description ? { description: v.description } : {}),
    }
  }
  out.collections[c.name] = { modes: c.modes.map((m) => m.name), variables: vars }
}
for (const s of await figma.getLocalTextStylesAsync()) {
  const bound = {}
  for (const [key, alias] of Object.entries(s.boundVariables ?? {}))
    bound[key] = byId[alias.id].name
  out.textStyles.push({
    name: s.name,
    fontFamily: s.fontName.family,
    fontWeight: s.fontName.variationSettings?.wght ?? WEIGHTS[s.fontName.style],
    fontSize: s.fontSize,
    lineHeight: s.lineHeight.unit === 'PIXELS' ? s.lineHeight.value : null,
    letterSpacingPercent: s.letterSpacing.unit === 'PERCENT' ? s.letterSpacing.value : null,
    bound,
  })
}
for (const s of await figma.getLocalEffectStylesAsync()) {
  out.effectStyles.push({
    name: s.name,
    shadows: s.effects
      .filter((e) => e.type === 'DROP_SHADOW')
      .map((e) => ({
        color: color(e.color),
        x: e.offset.x,
        y: e.offset.y,
        blur: e.radius,
        spread: e.spread ?? 0,
      })),
  })
}

// FNV-1a 32비트. 생성 스크립트도 같은 방식으로 계산한다
const text = JSON.stringify(out)
let h = 0x811c9dc5
for (let i = 0; i < text.length; i++) {
  h ^= text.charCodeAt(i)
  h = Math.imul(h, 0x01000193) >>> 0
}
return { hash: h.toString(16), json: out }
