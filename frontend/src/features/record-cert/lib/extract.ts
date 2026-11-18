/**
 * 기록증 뽑기 규칙. 시제품(ocr-bench/extract.py, box 모드)을 그대로 옮기고, 같은 값이 여러 번 나오면 하나로 센다(11/17, 서버 판정과 같은 기준). OCR 줄 목록에서 넷타임 · 대회일 · 종목 · 배번 · 대회명을 고른다.
 * 정규식은 Python 원문 그대로 적고 py()가 Python re 의미로 바꾼다(\s · \d · $ · . · re.I의 차이).
 * 내부 pick* 함수는 Python과 같은 튜플 모양을 돌려주고, extractFields가 화면용 객체로 바꾼다.
 */

export type OcrLine = { text: string; score: number; box: number[][] }
export type Rect = [number, number, number, number]
/** quad: 줄 상자 네 점(원본 픽셀). 서버 확인이 이 자리를 다시 읽는다 */
export type Picked<T> = {
  value: T
  lineIndex: number
  text: string
  box: Rect
  quad: number[][]
  score: number
} | null
export type Fields = {
  /** 넷타임 표시 단어 줄. 값 줄에 표시 단어가 있으면 같은 줄, 없으면 값에 가장 가까운 표시 단어 줄 */
  netLabel: { text: string; quad: number[][] } | null
  netTime: Picked<number[]>
  raceDate: Picked<string>
  course: Picked<string>
  bib: Picked<string>
  raceName: Picked<string>
}

// Python str 정규식의 \s(= str.isspace). JS \s와 달리 \x1c-\x1f · \x85를 넣고 ﻿를 뺀다
const SPACE_NO_NL = String.raw`\t\x0b-\r\x1c-\x20\x85\xa0  -     　`
const SPACE = String.raw`\n` + SPACE_NO_NL

/**
 * Python re 원문 → JS RegExp. \d는 유니코드 숫자, \s는 Python 공백, $는 '끝 또는 마지막 줄바꿈 앞', .은 \n만 뺀다.
 * re.I일 때 Python은 'i'를 'İ' · 'ı'와도 맞추므로 그만큼 넣는다(K · ſ는 JS u 플래그도 같다).
 * shortcut: \p{Nd}는 Node의 유니코드 버전을 따른다(Python 3.12는 15.0). 새로 추가된 숫자 문자만 다르다.
 */
function py(src: string, flags = ''): RegExp {
  const ci = flags.includes('i')
  let out = ''
  let inClass = false
  for (let i = 0; i < src.length; i++) {
    const c = src[i]
    if (c === '\\') {
      const n = src[++i]
      if (n === 'd') out += String.raw`\p{Nd}`
      else if (n === 's') out += inClass ? SPACE : `[${SPACE}]`
      else out += '\\' + n
    } else if (inClass) {
      if (c === ']') {
        inClass = false
        if (ci && /a-z/i.test(out.slice(out.lastIndexOf('[')))) out += String.raw`İı`
      }
      out += c
    } else if (c === '[') {
      inClass = true
      out += c
    } else if (c === '$') out += String.raw`(?=\n?$)`
    else if (c === '.') out += String.raw`[^\n]`
    else if (ci && c === 'i') out += String.raw`[iİı]`
    else out += c
  }
  return new RegExp(out, flags + 'u')
}

const ND = /\p{Nd}/u

/** Python int() · float()처럼 유니코드 숫자도 읽는다. 숫자 블록은 0~9가 연달아 있으므로 블록 안 위치로 값을 구한다. */
function num(s: string): number {
  return Number(
    s.replace(/[^\x00-\x7f]/gu, (ch) => {
      if (!ND.test(ch)) return ch
      const cp = ch.codePointAt(0)!
      let k = 0
      while (ND.test(String.fromCodePoint(cp - k - 1))) k++
      return String(k % 10)
    }),
  )
}

/** Python 문자열 비교(코드 포인트 순). JS '<'는 UTF-16 단위라 보조 평면 글자에서 다르다. */
function pyCmp(a: string, b: string): number {
  const x = [...a]
  const y = [...b]
  for (let i = 0; i < Math.min(x.length, y.length); i++) {
    const d = x[i].codePointAt(0)! - y[i].codePointAt(0)!
    if (d) return d
  }
  return x.length - y.length
}

const STRIP = new RegExp(`^[${SPACE}]+|[${SPACE}]+$`, 'gu')
export const pyStrip = (s: string) => s.replace(STRIP, '')
const pyLen = (s: string) => [...s].length

/** Python '{:g}' (유효숫자 6자리). shortcut: 정확히 반올림 경계인 값은 JS가 올림, Python이 짝수 쪽이라 다를 수 있다(7자리 이상 거리에서만). */
function fmtG(n: number): string {
  if (n === 0) return '0'
  const [mant, e] = n.toExponential(5).split('e')
  const exp = Number(e)
  if (exp < -4 || exp >= 6) {
    return `${mant.replace(/\.?0+$/, '')}e${exp < 0 ? '-' : '+'}${String(Math.abs(exp)).padStart(2, '0')}`
  }
  const s = n.toFixed(5 - exp)
  return s.includes('.') ? s.replace(/\.?0+$/, '') : s
}

// ---- evaluate.py ----

const NORM_SPACE = new RegExp(`[${SPACE_NO_NL}]+`, 'gu')
export function norm(s: string): string {
  return s.replace(NORM_SPACE, '').toLowerCase()
}

const GENERIC = new Set(['대회', '마라톤', '국제', '전국'])
const YEAR4 = py(String.raw`^\d{4}`)
const ROUND = py(String.raw`^(?:제\d+회|第\d+回)`)
const ROUND_HEAD = py(String.raw`^(제\d+회|第\d+回)`)
const NAME_TAIL = py(String.raw`(마라톤대회|마라톤|대회|축제|マラソン大会)$`)
const SPLIT = new RegExp(`[${SPACE}]+`, 'u')
const full = (re: RegExp, t: string) => re.exec(t)?.[0].length === t.length // re.fullmatch

export function nameTokens(label: string): string[] | null {
  label = pyStrip(label)
  if (!label) return null
  const toks: string[] = []
  for (let t of label.split(SPLIT).filter(Boolean)) {
    if (full(YEAR4, t) || full(ROUND, t) || GENERIC.has(t)) continue
    t = t.replace(ROUND_HEAD, '').replace(NAME_TAIL, '')
    if (pyLen(t) >= 2) toks.push(t.toLowerCase())
  }
  return toks.length ? toks : [norm(label)]
}

// ---- extract.py ----

const TIME_RE = py(
  String.raw`(?<!\d)(?<!\d[:.])(\d{1,2})\s*([:;.])\s*(\d{2})\s*([:;.])\s*(\d{2})(?!\d)|(?<!\d)(\d{1,2})\s*시간\s*(\d{1,2})\s*분\s*(\d{1,2})\s*초?|(?<!\d)(\d{1,2})\s*분\s*(\d{1,2})\s*초`,
  'g',
)
const MMSS_RE = py(String.raw`(?<!\d)(?<!\d[:.])(\d{1,2}):(\d{2})(?![\d:])`, 'g')
const DATE_RE = py(String.raw`(20\d{2}|19\d{2})\s*[./\-년]\s*(\d{1,2})\s*[./\-월]\s*(\d{1,2})`, 'g')
export const NET_ANCHOR = py(
  String.raw`넷\s*타임|net\s*time|nettime|순\s*기록|실\s*기록|칩\s*타임|chip|완주\s*기록|^기\s*록\s*[:：]?\s*(?=\d|$)|record`,
  'i',
)
export const GUN_WORD = py(
  String.raw`건\s*타임|gun|총\s*기록|구간|split|lap|section|페이스|pace|/km|평균|출발|도착|오전|오후|start|finish`,
  'i',
)
const SEC_PER_KM = [150, 900]
const DATE_ANCHOR = py(String.raw`일\s*시|일\s*자|날\s*짜|대회\s*일|date`, 'i')
const COURSE_ANCHOR = py(
  String.raw`종\s*목|부\s*문|코\s*스|거\s*리|course|event|category|distance`,
  'i',
)

export function timesIn(text: string): number[][] {
  const out: number[][] = []
  for (const m of text.matchAll(TIME_RE)) {
    if (m[1] !== undefined) {
      const seps = m[2] + m[4]
      if (!seps.includes(':') && !seps.includes(';')) continue // '25.10.25' 같은 날짜 조각은 시간이 아니다
      out.push([num(m[1]), num(m[3]), num(m[5])])
    } else if (m[6] !== undefined) {
      out.push([num(m[6]), num(m[7]), num(m[8])])
    } else {
      out.push([0, num(m[9]), num(m[10])])
    }
  }
  if (!out.length) for (const m of text.matchAll(MMSS_RE)) out.push([0, num(m[1]), num(m[2])])
  return unique(out.filter((t) => t[1] < 60 && t[t.length - 1] < 60))
}

/** 해석한 값이 같으면 처음 것 하나만 남긴다. '01:30:05 01:30:05'는 시간 하나다 */
function unique<T extends number[]>(values: T[]): T[] {
  const seen = new Set<string>()
  return values.filter((v) => {
    const key = v.join()
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

export function courseKm(cls: string | null): number | null {
  if (cls === 'FULL') return 42.195
  if (cls === 'HALF') return 21.0975
  if (cls && cls.endsWith('K')) return Number(cls.slice(0, -1))
  return null
}

export function plausible(t: number[], km: number | null): number[] | null {
  const [lo, hi] = km ? [km * SEC_PER_KM[0], km * SEC_PER_KM[1]] : [12 * 60, 30 * 3600]
  const secs = t[0] * 3600 + t[1] * 60 + t[2]
  if (lo <= secs && secs <= hi) return t
  if (t[0] === 0 && lo <= t[1] * 3600 + t[2] * 60 && t[1] * 3600 + t[2] * 60 <= hi)
    return [t[1], t[2]] // 초는 모르는 값
  return null
}

export const sameTime = (got: number[], want: number[]) =>
  want.every((v, k) => got[k] === v) && got.length >= want.length

function center(box: number[][]): Rect {
  const xs = box.map((p) => p[0])
  const ys = box.map((p) => p[1])
  return [Math.min(...xs), Math.min(...ys), Math.max(...xs), Math.max(...ys)]
}

function nearScore(anchor: Rect, cand: Rect): number | null {
  const [ax0, ay0, ax1, ay1] = anchor
  const [cx0, cy0, cx1, cy1] = cand
  const ah = Math.max(ay1 - ay0, 1)
  const sameRow = Math.min(ay1, cy1) - Math.max(ay0, cy0) > 0.3 * ah && cx0 >= ax0
  const below = cy0 >= ay0 && cy0 - ay1 < 4 * ah && Math.min(ax1, cx1) - Math.max(ax0, cx0) > 0
  if (sameRow) return cx0 - ax1
  if (below) return 1000 + (cy0 - ay1) + Math.abs(ax0 + ax1 - (cx0 + cx1)) / 2
  return null
}

const UNIT_PART = py(String.raw`^\s*\d{1,2}\s*(시간|분|초)\s*$`)
const UNIT_TAIL = py(String.raw`(시간|분)\s*$`)

function joinUnits(lines: OcrLine[]): OcrLine[] {
  const out: OcrLine[] = []
  for (const ln of lines) {
    const prev = out.length ? out[out.length - 1] : null
    if (prev && UNIT_PART.test(ln.text) && UNIT_TAIL.test(prev.text)) {
      const a = center(prev.box)
      const b = center(ln.box)
      if (
        Math.min(a[3], b[3]) - Math.max(a[1], b[1]) > 0.3 * Math.max(a[3] - a[1], 1) &&
        b[0] >= a[0]
      ) {
        const y0 = Math.min(a[1], b[1])
        const y1 = Math.max(a[3], b[3])
        const box = [
          [a[0], y0],
          [b[2], y0],
          [b[2], y1],
          [a[0], y1],
        ]
        out[out.length - 1] = {
          text: prev.text + ln.text,
          score: Math.min(prev.score, ln.score),
          box,
        }
        continue
      }
    }
    out.push(ln)
  }
  return out
}

/** 같은 행에서 바로 왼쪽 칸의 글자. Python max((x1, text))와 같게 x1이 같으면 글자 순으로 큰 쪽. */
function leftLabel(lines: OcrLine[], i: number): string {
  const b = center(lines[i].box)
  const h = Math.max(b[3] - b[1], 1)
  let best: [number, string] | null = null
  for (let j = 0; j < lines.length; j++) {
    const c = center(lines[j].box)
    if (j === i || !(Math.min(b[3], c[3]) - Math.max(b[1], c[1]) > 0.5 * h && c[2] <= b[0]))
      continue
    if (!best || c[2] > best[0] || (c[2] === best[0] && pyCmp(lines[j].text, best[1]) > 0))
      best = [c[2], lines[j].text]
  }
  return best ? best[1] : ''
}

const CLOCK_HEAD = py(String.raw`도\s*착|출\s*발|시\s*각|clock`, 'i')

function aboveLabel(lines: OcrLine[], i: number): string {
  const b = center(lines[i].box)
  const h = Math.max(b[3] - b[1], 1)
  let best: [number, string] | null = null
  for (let j = 0; j < lines.length; j++) {
    const c = center(lines[j].box)
    if (
      j === i ||
      !(
        c[3] <= b[1] + 0.2 * h &&
        b[1] - c[3] < 3 * h &&
        Math.min(b[2], c[2]) - Math.max(b[0], c[0]) > 0
      )
    )
      continue
    const d = b[1] - c[3]
    if (!best || d < best[0] || (d === best[0] && pyCmp(lines[j].text, best[1]) < 0))
      best = [d, lines[j].text]
  }
  return best ? best[1] : ''
}

/** (줄 번호, 시간, 상자, 확신도, H:MM:SS 꼴인가). 줄 번호는 join_units 뒤 기준 */
type NetCand = [number, number[], Rect, number, boolean]

export function pickNet(
  lines: OcrLine[],
  mode: 'box' | 'order',
  km: number | null = null,
): NetCand | null {
  lines = joinUnits(lines)
  const cands: NetCand[] = []
  lines.forEach((ln, i) => {
    if (
      GUN_WORD.test(ln.text) ||
      GUN_WORD.test(leftLabel(lines, i)) ||
      (mode === 'box' && CLOCK_HEAD.test(aboveLabel(lines, i)))
    )
      return
    const hms = [...ln.text.matchAll(TIME_RE)].some(
      (m) => m[1] === undefined || (m[2] + m[4]).includes(':'),
    )
    for (const t of timesIn(ln.text)) cands.push([i, t, center(ln.box), ln.score, hms])
  })
  if (!cands.length) return null
  if (mode === 'box') return pickNetBox(lines, cands, km)
  const anchors = lines.map((ln, i) => i).filter((i) => NET_ANCHOR.test(pyStrip(lines[i].text)))
  for (const c of cands) if (NET_ANCHOR.test(lines[c[0]].text)) return c
  if (anchors.length) {
    const after = cands.filter((c) => c[0] > anchors[0])
    if (after.length) return after[0]
  }
  return cands[0]
}

function pickNetBox(lines: OcrLine[], cands: NetCand[], km: number | null): NetCand | null {
  const ok = (c: NetCand): NetCand | null => {
    const t = plausible(c[1], km)
    return t ? [c[0], t, c[2], c[3], c[4]] : null
  }
  for (const c of cands) if (NET_ANCHOR.test(lines[c[0]].text)) return ok(c)
  const anchors = lines
    .filter((ln) => NET_ANCHOR.test(pyStrip(ln.text)))
    .map((ln) => center(ln.box))
  const near: [number, number, NetCand][] = []
  cands.forEach((c, k) => {
    for (const ab of anchors) {
      const s = nearScore(ab, c[2])
      if (s !== null) near.push([s, k, c])
    }
  })
  near.sort((a, b) => a[0] - b[0] || a[1] - b[1])
  if (near.length) {
    // 기준 단어 옆 값 중 그럴듯한 것. 없으면 다른 데서 찾지 않고 비운다(구간 기록을 대신 고르면 유리한 오추출, s-091 · s-122)
    for (const [, , c] of near) {
      const r = ok(c)
      if (r) return r
    }
    return null
  }
  // 기준 단어가 없으면 H:MM:SS 꼴을 먼저, 그중 글자가 가장 큰 시간. 'M:SS'를 'H:MM'으로 다시 읽은 값은 믿지 않는다
  let best: NetCand | null = null
  for (const c of cands) {
    const r = ok(c)
    if (!r || r[1].length !== 3) continue
    if (
      !best ||
      +r[4] > +best[4] ||
      (r[4] === best[4] && r[2][3] - r[2][1] > best[2][3] - best[2][1])
    )
      best = r
  }
  return best
}

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']
const MON = String.raw`(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?`
const EN_DATE_MDY = py(
  MON + String.raw`\s*(\d{1,2})(?:\s*-\s*\d{1,2})?\s*[,.]?\s*((?:19|20)\d{2})`,
  'gi',
)
const EN_DATE_DMY = py(
  String.raw`(?<!\d)(\d{1,2})(?:st|nd|rd|th)?\s*` + MON + String.raw`\s*,?\s*((?:19|20)\d{2})`,
  'gi',
)

type Date3 = [number, number, number]

export function datesIn(text: string): Date3[] {
  const out: Date3[] = []
  for (const m of text.matchAll(DATE_RE)) out.push([num(m[1]), num(m[2]), num(m[3])])
  // shortcut: Python은 'ſep'처럼 월 이름이 목록에 없으면 ValueError로 멈춘다. 여기서는 월 0으로 걸러진다
  for (const m of text.matchAll(EN_DATE_MDY))
    out.push([num(m[3]), MONTHS.indexOf(m[1].toLowerCase()) + 1, num(m[2])])
  for (const m of text.matchAll(EN_DATE_DMY))
    out.push([num(m[3]), MONTHS.indexOf(m[2].toLowerCase()) + 1, num(m[1])])
  return unique(out.filter(([, mo, d]) => 1 <= mo && mo <= 12 && 1 <= d && d <= 31))
}

const MONTH_DAY = py(String.raw`(?<!\d)(\d{1,2})\s*월\s*(\d{1,2})\s*일`, 'g')
const YEAR = py(String.raw`(?<!\d)((?:19|20)\d{2})(?!\d)`, 'g')
const pad = (n: number, w: number) => String(n).padStart(w, '0')

type Pick = [number, string, number]

function splitDate(lines: OcrLine[]): Pick | null {
  const years = new Set<number>()
  for (const ln of lines)
    if (!timesIn(ln.text).length) for (const m of ln.text.matchAll(YEAR)) years.add(num(m[1]))
  const mds: Date3[] = []
  lines.forEach((ln, i) => {
    for (const m of ln.text.matchAll(MONTH_DAY)) mds.push([i, num(m[1]), num(m[2])])
  })
  if (years.size !== 1 || new Set(mds.map(([, mo, d]) => `${mo},${d}`)).size !== 1) return null
  const [i, mo, d] = mds.reduce((a, b) =>
    b[0] < a[0] || (b[0] === a[0] && (b[1] < a[1] || (b[1] === a[1] && b[2] < a[2]))) ? b : a,
  )
  if (!(1 <= mo && mo <= 12 && 1 <= d && d <= 31)) return null
  return [i, `${pad([...years][0], 4)}-${pad(mo, 2)}-${pad(d, 2)}`, lines[i].score]
}

export function pickDate(lines: OcrLine[]): Pick | null {
  const cands: Pick[] = []
  lines.forEach((ln, i) => {
    for (const [y, mo, d] of datesIn(ln.text))
      cands.push([i, `${pad(y, 4)}-${pad(mo, 2)}-${pad(d, 2)}`, ln.score])
  })
  if (!cands.length) return splitDate(lines)
  const anchorIdx = lines.map((ln, i) => i).filter((i) => DATE_ANCHOR.test(lines[i].text))
  if (anchorIdx.length) {
    const after = cands.filter((c) => c[0] >= anchorIdx[0])
    if (after.length) return after[0]
  }
  return cands[0]
}

const K_START = py(String.raw`^\d+(?:\.\d+)?k`)
const K_ALL = py(String.raw`(?<![\d.])\d+(?:\.\d+)?k`, 'g')
const FULL_HALF = py(String.raw`(?<![a-z])(full|half)(?:course|marathon|(?![a-z]))`, 'g')
const FULL_RE = py(String.raw`풀|42\.195|42\.2k`)
const HALF_RE = py(String.raw`하프|21\.0975|21\.097|21\.1k`)
const K_NUM = py(String.raw`(?<![\d.])(\d+(?:\.\d+)?)k`)

export function courseClass(text: string): string | null {
  const t = norm(text)
  if (timesIn(text).length && K_START.test(t)) return null // '5km 00:23:59'는 구간 기록
  if ([...t.matchAll(K_ALL)].length > 1) return null // '5K·10K'는 안내 문구(058)
  const words = new Set([...text.toLowerCase().matchAll(FULL_HALF)].map((m) => m[1]))
  if (words.has('full') || FULL_RE.test(t)) return 'FULL'
  if (words.has('half') || HALF_RE.test(t)) return 'HALF'
  const m = K_NUM.exec(t)
  if (m) {
    const n = num(m[1])
    if (Math.abs(n - 42.195) < 0.01) return 'FULL'
    if (Math.abs(n - 21.0975) < 0.01) return 'HALF'
    return `${fmtG(n)}K`
  }
  return null
}

const KM_HEADER = py(String.raw`(거리|distance).*km|km.*(거리|distance)`, 'i')
const BARE_NUM = py(String.raw`^\s*(\d{1,3}(?:\.\d{1,3})?)\s*$`)

export function pickCourse(lines: OcrLine[]): [number, string | null, number] | null {
  let cands = lines.map((ln, i) => [i, courseClass(ln.text), ln.score] as Pick).filter((c) => c[1])
  if (!cands.length) {
    // 앱 화면: '거리(km)' 머리글 아래 · 오른쪽(또는 위, 041)에 숫자만 따로 있다
    let best: [number, [number, string | null, number]] | null = null
    for (const h of lines) {
      if (!KM_HEADER.test(h.text)) continue
      const hb = center(h.box)
      lines.forEach((ln, i) => {
        const m = BARE_NUM.exec(ln.text)
        let s = m ? nearScore(hb, center(ln.box)) : null
        if (m && s === null) {
          const cb = center(ln.box)
          if (
            cb[3] <= hb[1] &&
            hb[1] - cb[3] < 3 * (hb[3] - hb[1]) &&
            Math.min(hb[2], cb[2]) - Math.max(hb[0], cb[0]) > 0
          )
            s = 500 + hb[1] - cb[3]
        }
        if (s !== null && (best === null || s < best[0]))
          best = [s, [i, courseClass(m![1] + 'k'), ln.score]]
      })
    }
    return best ? (best as [number, [number, string | null, number]])[1] : null
  }
  // 같은 행에 시간이 있는 거리는 구간 기록표의 한 줄이다(006 · 044)
  const timeBoxes = lines.filter((ln) => timesIn(ln.text).length).map((ln) => center(ln.box))
  const inSplitRow = (c: Pick) => {
    const b = center(lines[c[0]].box)
    const h = Math.max(b[3] - b[1], 1)
    return timeBoxes.some(
      (t) => Math.min(b[3], t[3]) - Math.max(b[1], t[1]) > 0.5 * h && t[0] > b[0],
    )
  }
  const kept = cands.filter((c) => !inSplitRow(c))
  if (kept.length) cands = kept
  const anchorIdx = lines.map((ln, i) => i).filter((i) => COURSE_ANCHOR.test(lines[i].text))
  if (anchorIdx.length) {
    const after = cands.filter((c) => c[0] >= anchorIdx[0])
    if (after.length) return after[0]
  }
  // 기준 단어가 없으면 가장 자주 나온 종목(040 · 058)
  const freq = new Map<string, number>()
  for (const c of cands) freq.set(c[1], (freq.get(c[1]) ?? 0) + 1)
  const top = Math.max(...freq.values())
  return cands.find((c) => freq.get(c[1]) === top)!
}

const RACE_WORD = py(String.raw`마라톤|대회|레이스|런|트레일|축제|marathon|race|run`, 'i')
const RACE_NOT = py(String.raw`조직위|위원장|위원회|기록증|완주증|증서|조회|www|\.com|\.kr|님`, 'i')

export function pickRace(lines: OcrLine[]): Pick | null {
  // 대회명은 제목이라 글자가 가장 크다: 대회 단어가 있는 줄 중 상자 높이가 가장 큰 줄(같으면 위쪽)
  let best: [number, number, number] | null = null // (-높이, y0, i)
  lines.forEach((ln, i) => {
    if (!RACE_WORD.test(ln.text) || RACE_NOT.test(ln.text) || timesIn(ln.text).length) return
    const b = center(ln.box)
    const k: [number, number, number] = [-(b[3] - b[1]), b[1], i]
    if (!best || k[0] < best[0] || (k[0] === best[0] && k[1] < best[1])) best = k
  })
  if (!best) return null
  const i = (best as [number, number, number])[2]
  return [i, lines[i].text, lines[i].score]
}

const BIB_ANCHOR = py(String.raw`배\s*번|참\s*가\s*번\s*호|bib|race\s*no`, 'i')
const BIB_VAL = String.raw`[A-Za-z]{0,2}-?\d{3,7}[A-Za-z]?`
const BIB_INLINE = py(
  String.raw`(?:배\s*번\s*호?|참\s*가\s*번\s*호|bib|race\s*no\.?)\s*(?:\(\s*bib\s*\)|number|no\.?)?\s*[:：.=·]?\s*(` +
    BIB_VAL +
    String.raw`)(?![\d:])`,
  'i',
)
const BIB_CELL = py(String.raw`^\s*[:#]?\s*(` + BIB_VAL + String.raw`)\s*(?:[·|].*)?$`)
const TABLE_HEAD = py(String.raw`이름|순위|rank|name`, 'i')
const BIB_STRIP = py(String.raw`[\s\-]`, 'g')

export const normBib = (v: string) => v.replace(BIB_STRIP, '').toUpperCase()

/** 배번 표시('배번' · '참가번호' · 'BIB')가 있을 때만 뽑는다. */
export function pickBib(lines: OcrLine[]): Pick | null {
  for (let i = 0; i < lines.length; i++) {
    const m = BIB_INLINE.exec(lines[i].text)
    if (m) return [i, m[1], lines[i].score]
  }
  // '배번호·이름'처럼 이름 · 순위가 같이 적힌 머리글은 순위표다(007 · 040)
  const anchors = lines
    .filter((ln) => BIB_ANCHOR.test(ln.text) && !TABLE_HEAD.test(ln.text))
    .map((ln) => center(ln.box))
  const near: [number, number][] = []
  lines.forEach((ln, i) => {
    if (!BIB_CELL.test(ln.text)) return
    for (const ab of anchors) {
      const s = nearScore(ab, center(ln.box))
      if (s !== null) near.push([s, i])
    }
  })
  near.sort((a, b) => a[0] - b[0] || a[1] - b[1])
  if (near.length && near.filter(([s]) => s >= 1000).length > 1) return null // 아래 칸 후보가 여럿이면 순위표
  if (near.length) {
    const i = near[0][1]
    return [i, BIB_CELL.exec(lines[i].text)![1], lines[i].score]
  }
  return null
}

export function sameRace(got: string, label: string): boolean {
  const g = norm(got)
  return nameTokens(label)!.every((t) => g.includes(t))
}

function picked<T>(lines: OcrLine[], p: [number, T, number] | null): Picked<T> {
  if (!p) return null
  const ln = lines[p[0]]
  return {
    value: p[1],
    lineIndex: p[0],
    text: ln.text,
    box: center(ln.box),
    quad: ln.box,
    score: p[2],
  }
}

/** pickNetBox가 값을 고를 때 쓴 표시 단어 줄. lines는 joinUnits 뒤 줄 */
function netLabel(lines: OcrLine[], net: NetCand): Fields['netLabel'] {
  const own = lines[net[0]]
  if (NET_ANCHOR.test(own.text)) return { text: own.text, quad: own.box }
  let best: [number, OcrLine] | null = null
  for (const ln of lines) {
    if (!NET_ANCHOR.test(pyStrip(ln.text))) continue
    const s = nearScore(center(ln.box), net[2])
    if (s !== null && (!best || s < best[0])) best = [s, ln]
  }
  return best && { text: best[1].text, quad: best[1].box }
}

/** OCR 줄 목록 → 입력 칸 값. 넷타임의 그럴듯한 범위는 뽑은 종목 거리로 정한다(extract.py main과 같다). */
export function extractFields(lines: OcrLine[]): Fields {
  const course = pickCourse(lines)
  const net = pickNet(lines, 'box', course ? courseKm(course[1]) : null)
  const joined = joinUnits(lines)
  return {
    // 넷타임의 줄 번호 · 글자 · 상자는 '1시간' '9분' '32초'를 이은 뒤 기준이다
    netTime: net && {
      value: net[1],
      lineIndex: net[0],
      text: joined[net[0]].text,
      box: net[2],
      quad: joined[net[0]].box,
      score: net[3],
    },
    netLabel: net && netLabel(joined, net),
    raceDate: picked(lines, pickDate(lines)),
    course: picked(lines, course as [number, string, number] | null),
    bib: picked(lines, pickBib(lines)),
    raceName: picked(lines, pickRace(lines)),
  }
}
