#!/usr/bin/env node
// 밤샘 브라우저 점검. 실제 브라우저로 사이트를 계속 돌면서 아래를 모은다.
//   - 눌러도 반응 없는 버튼·링크, 다른 요소에 가려져 눌리지 않는 버튼
//   - 깜빡임: 잠깐 나타났다 사라지는 요소, 화면이 비었다 다시 채워짐, 레이아웃 흔들림(CLS)
//   - 열리지 않는 페이지, JS 오류, 하이드레이션 불일치, 4xx·5xx 응답
// 같은 페이지를 밤새 여러 번 열어서 "몇 번 중 몇 번"으로 간헐적인 문제를 센다.
//
// 실행 (frontend/에서): caffeinate -dimsu node scripts/night-check.mjs
// 계정: frontend/.env.night (git 제외)에 NIGHT_USER_EMAIL / NIGHT_USER_PASSWORD,
//   NIGHT_SELLER_*, NIGHT_ADMIN_* 를 적는다. 없는 역할은 건너뛰고 비회원으로만 돈다.
// 그 밖: NIGHT_URL(기본 운영 주소), NIGHT_UNTIL=07:00, NIGHT_OUT, QUICK=1(한 바퀴 맛보기)
//
// 데이터 보호: 로그인 뒤에는 GET이 아닌 요청과 외부 사이트 이동을 브라우저에서 모두 막는다.
// 그래서 결제·삭제 버튼을 눌러도 서버 데이터는 바뀌지 않는다. 위험한 글자의 버튼은 아예 누르지 않는다.
import fs from 'node:fs'
import path from 'node:path'
import { chromium, devices } from '@playwright/test'

try {
  process.loadEnvFile('.env.night')
} catch {}

const BASE = (process.env.NIGHT_URL ?? 'https://crewrun.duckdns.org').replace(/\/$/, '')
const OUT = path.resolve(process.env.NIGHT_OUT ?? '../docs/study/night-check')
const QUICK = process.env.QUICK === '1'
const UNTIL = (() => {
  const [h, m] = (process.env.NIGHT_UNTIL ?? '07:00').split(':').map(Number)
  const d = new Date()
  d.setHours(h, m, 0, 0)
  if (d <= new Date()) d.setDate(d.getDate() + 1)
  return d.getTime()
})()
const MAX_CLICKS = QUICK ? 3 : 15
// 누르지 않는 버튼. 글자에 이 말이 들어가면 건너뛴다 (요청 차단과 별개로 한 번 더 막는다)
const SKIP =
  '로그아웃|탈퇴|삭제|결제|충전|환급|환불|취소|신청|제출|저장|등록|보내기|승인|반려|수락|거절|나가기|차단|신고|확정|구매|예매|초대|해제'

fs.mkdirSync(path.join(OUT, 'shots'), { recursive: true })
const LOG = path.join(OUT, 'run.log')
const log = (...a) => {
  const s = `[${new Date().toLocaleTimeString('ko-KR', { hour12: false })}] ${a.join(' ')}`
  console.log(s)
  fs.appendFileSync(LOG, s + '\n')
}
const late = () => !QUICK && Date.now() >= UNTIL

// 페이지 안에서 돈다. 변화(mutation)·깜빡임·레이아웃 흔들림을 window.__nc에 센다
function probe() {
  const nc = (window.__nc = { mut: 0, cls: 0, shifts: [], flashes: [], blank: 0 })
  const desc = (el) => {
    const cls =
      typeof el.className === 'string' ? el.className.trim().split(/\s+/).slice(0, 2).join('.') : ''
    const t = (el.innerText || el.getAttribute('aria-label') || '')
      .trim()
      .replace(/\s+/g, ' ')
      .slice(0, 30)
    return `${el.tagName.toLowerCase()}${cls ? '.' + cls : ''}${t ? ` "${t}"` : ''}`
  }
  const born = new WeakMap()
  const painted = new WeakMap()
  new MutationObserver((list) => {
    const now = performance.now()
    const added = new Set()
    const gone = []
    for (const m of list) {
      nc.mut++
      for (const n of m.addedNodes) {
        if (n.nodeType !== 1) continue
        born.set(n, now)
        added.add(desc(n))
        // 실제로 화면에 그려진 크기 있는 요소만 깜빡임 후보로 본다
        requestAnimationFrame(() => {
          if (!n.isConnected) return
          const r = n.getBoundingClientRect()
          // animate-delayed-show로 0.3초 숨긴 요소(투명)는 화면에 그려진 게 아니라서 뺀다
          if (
            r.width >= 24 &&
            r.height >= 16 &&
            n.checkVisibility({ opacityProperty: true, visibilityProperty: true })
          )
            painted.set(n, desc(n))
        })
      }
      for (const n of m.removedNodes) {
        if (n.nodeType !== 1 || !painted.has(n)) continue
        const ms = now - born.get(n)
        if (ms < 400) gone.push({ el: painted.get(n), ms: Math.round(ms) })
      }
    }
    // 같은 순간에 같은 설명의 요소가 새로 붙었으면 사라진 게 아니라 바뀐 것이라 깜빡임으로 세지 않는다
    for (const g of gone) if (!added.has(g.el)) nc.flashes.push(g)
  }).observe(document, { childList: true, subtree: true, attributes: true, characterData: true })
  let prev = 0
  setInterval(() => {
    const len = document.body ? document.body.innerText.length : 0
    if (prev > 50 && len < 5) nc.blank++
    prev = len
  }, 100)
  try {
    new PerformanceObserver((l) => {
      for (const e of l.getEntries()) {
        if (e.hadRecentInput) continue
        nc.cls += e.value
        if (e.value > 0.05)
          nc.shifts.push({
            v: +e.value.toFixed(3),
            el: (e.sources || [])
              .map((s) => (s.node && s.node.nodeType === 1 ? desc(s.node) : ''))
              .filter(Boolean)
              .slice(0, 2)
              .join(' / '),
          })
      }
    }).observe({ type: 'layout-shift', buffered: true })
  } catch {}
}

// src/app에서 [id] 없는 주소를 모은다. (그룹) 폴더는 주소에서 빠진다
function staticRoutes() {
  const out = []
  const walk = (dir, url) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (!e.isDirectory()) {
        if (e.name === 'page.tsx') out.push(url || '/')
        continue
      }
      if (/^[[_@]/.test(e.name)) continue
      walk(path.join(dir, e.name), e.name.startsWith('(') ? url : `${url}/${e.name}`)
    }
  }
  walk('src/app', '')
  return out
}
// /races/12 와 /races/34 는 같은 화면으로 묶는다
const tpl = (p) => p.replace(/\/\d+(?=\/|$)/g, '/:id').replace(/\/[0-9a-f-]{16,}(?=\/|$)/gi, '/:id')

const ROLE_PREFIX = {
  guest: (p) => !/^\/(me|seller|admin|tickets|notifications|cart|checkout)(\/|$)/.test(p),
  user: (p) => !/^\/(seller|admin|login|signup|find-email|password-reset)(\/|$)/.test(p),
  seller: (p) => /^\/seller/.test(p),
  admin: (p) => /^\/admin/.test(p),
}

// 발견 기록: 같은 종류·역할·화면·내용은 한 줄로 묶고 몇 번 나왔는지 센다
const found = new Map()
const visits = new Map()
function rec(type, role, page, detail, shot) {
  const key = [type, role, page, detail].join('|')
  const f = found.get(key) ?? {
    type,
    role,
    page,
    detail,
    count: 0,
    first: new Date().toISOString(),
    shot: null,
  }
  f.count++
  f.last = new Date().toISOString()
  f.shot ??= shot ?? null
  found.set(key, f)
}
const shotName = (s) => s.replace(/[^\w가-힣-]+/g, '_').slice(0, 80) + '.png'

async function newContext(browser, role, mobile) {
  const ctx = await browser.newContext(
    mobile
      ? { ...devices['iPhone 13'], baseURL: BASE }
      : { viewport: { width: 1280, height: 800 }, baseURL: BASE },
  )
  await ctx.addInitScript(probe)
  const st = { writes: 0, reqs: 0 }
  if (role !== 'guest') {
    const key = role.toUpperCase()
    const email = process.env[`NIGHT_${key}_EMAIL`]
    const pw = process.env[`NIGHT_${key}_PASSWORD`]
    if (!email || !pw) {
      await ctx.close()
      return null
    }
    const page = await ctx.newPage()
    try {
      await page.goto('/login')
      await page.getByLabel('이메일').fill(email)
      await page.getByLabel('비밀번호', { exact: true }).fill(pw)
      await page.getByRole('button', { name: '로그인', exact: true }).click()
      await page.waitForURL((u) => !u.pathname.startsWith('/login'), { timeout: 30_000 })
    } catch (e) {
      rec('로그인 실패', role, '/login', e.message.split('\n')[0])
      await ctx.close()
      return null
    }
    await page.close()
  }
  // 여기부터 데이터를 바꾸는 요청과 외부 이동을 막는다. 막힌 요청도 "버튼이 반응했다"로 센다
  await ctx.route('**/*', (route) => {
    const r = route.request()
    st.reqs++
    const u = new URL(r.url())
    let main = false
    try {
      main = r.isNavigationRequest() && r.frame() === r.frame().page().mainFrame()
    } catch {}
    if (main && u.origin !== BASE) return route.abort()
    if (!['GET', 'HEAD', 'OPTIONS'].includes(r.method()) && !u.pathname.includes('/auth/reissue')) {
      st.writes++
      return route.abort()
    }
    return route.continue()
  })
  ctx.__st = st
  return ctx
}

// watching: 깜빡임을 재는 방문(watch)일 때만 응답 오류를 센다. 누르기용 재방문까지 세면 횟수가 방문 수보다 커진다
async function openPage(ctx, role, url, view, watching = false) {
  const page = await ctx.newPage()
  const errs = []
  // 리소스 실패는 응답 쪽에서 따로 센다. ERR_FAILED는 이 스크립트가 막은 요청이라 버린다
  page.on(
    'console',
    (m) =>
      m.type() === 'error' &&
      !m.text().startsWith('Failed to load resource') &&
      errs.push(m.text().replace(/\s+/g, ' ').slice(0, 160)),
  )
  page.on('pageerror', (e) => errs.push('JS 오류: ' + e.message.split('\n')[0].slice(0, 160)))
  page.on('response', (r) => {
    const s = r.status()
    const u = new URL(r.url())
    if (watching && u.origin === BASE && s >= 400 && s !== 401)
      rec(
        `응답 ${s >= 500 ? '5xx' : '4xx'}`,
        role,
        tpl(url),
        `${s} ${r.request().method()} ${tpl(u.pathname)}${view}`,
      )
  })
  try {
    await page.goto(url, { waitUntil: 'load', timeout: 45_000 })
  } catch (e) {
    rec('열리지 않음', role, tpl(url), e.message.split('\n')[0] + view)
    await page.close()
    return null
  }
  return { page, errs }
}

// 한 번 열고 3초 지켜본 뒤 깜빡임·오류를 적는다
async function watch(ctx, role, url, view) {
  const vk = `${role}|${tpl(url)}`
  visits.set(vk, (visits.get(vk) ?? 0) + 1)
  const o = await openPage(ctx, role, url, view, true)
  if (!o) return []
  const { page, errs } = o
  const t = tpl(new URL(page.url()).pathname)
  await page.waitForTimeout(3000)
  const nc = await page.evaluate(() => window.__nc).catch(() => null)
  if (nc) {
    const digits = (s) => s.replace(/\d+/g, 'N')
    for (const f of nc.flashes) rec('잠깐 나타났다 사라짐', role, tpl(url), digits(f.el) + view)
    if (nc.blank) rec('화면이 비었다 다시 채워짐', role, tpl(url), `→ ${t}${view}`)
    if (nc.cls > 0.1)
      rec(
        '레이아웃 흔들림',
        role,
        tpl(url),
        (nc.shifts.map((s) => digits(s.el)).filter(Boolean)[0] ?? '(요소 모름)') + view,
      )
  }
  for (const e of errs)
    rec(
      /hydrat/i.test(e) ? '하이드레이션 불일치' : 'JS·콘솔 오류',
      role,
      tpl(url),
      e.replace(/\d+/g, 'N') + view,
    )
  const links = await page.$$eval('a[href]', (as) => as.map((a) => a.href)).catch(() => [])
  await page.close()
  return links.filter((h) => h.startsWith(BASE)).map((h) => new URL(h).pathname)
}

// 페이지 안에서 누를 후보를 찾고 data-nc 번호를 붙인다. 같은 글자의 같은 태그는 한 번만
function markClickables(skip) {
  const re = new RegExp(skip)
  const sel =
    'button, a[href], [role=button], [role=tab], [role=link], [role=checkbox], [role=switch], [role=menuitem], summary, input[type=checkbox], input[type=radio]'
  const out = []
  // 모달이 열려 있으면 뒤 배경은 일부러 막힌 것이라 모달 안만 누른다
  const dialogs = [
    ...document.querySelectorAll(
      '[role=dialog], [role=alertdialog], [aria-modal=true], dialog[open]',
    ),
  ].filter((d) => d.getBoundingClientRect().width > 0)
  const root = dialogs.at(-1) ?? document
  for (const el of root.querySelectorAll(sel)) {
    const r = el.getBoundingClientRect()
    if (r.width < 4 || r.height < 4) continue
    const cs = getComputedStyle(el)
    if (
      cs.visibility === 'hidden' ||
      cs.display === 'none' ||
      el.disabled ||
      el.getAttribute('aria-disabled') === 'true'
    )
      continue
    if (el.tagName === 'A' && !el.href.startsWith(location.origin)) continue
    // 지금 주소와 같은 곳(해시만 다른 경우 포함)으로 가는 링크는 눌러도 달라지는 게 없다
    if (el.tagName === 'A' && el.href.split('#')[0] === location.href.split('#')[0]) continue
    const text = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '')
      .trim()
      .replace(/\s+/g, ' ')
      .slice(0, 40)
    if (re.test(text)) continue
    const key = `${el.tagName.toLowerCase()}|${text}`
    if (out.includes(key)) continue
    el.setAttribute('data-nc', String(out.length))
    out.push(key)
  }
  return out
}

async function clickAll(ctx, role, url, view) {
  const first = await openPage(ctx, role, url, view)
  if (!first) return
  await first.page.waitForTimeout(1500)
  const keys = (await first.page.evaluate(markClickables, SKIP).catch(() => [])).slice(
    0,
    MAX_CLICKS,
  )
  await first.page.close()
  for (const key of keys) {
    if (late()) return
    const o = await openPage(ctx, role, url, view)
    if (!o) return
    const { page, errs } = o
    try {
      await page.waitForTimeout(1500)
      const idx = (await page.evaluate(markClickables, SKIP)).indexOf(key)
      if (idx < 0) continue // 이번에는 안 보이는 버튼. 간헐적으로 사라지는 건 깜빡임 쪽에서 잡힌다
      const text = key.split('|')[1] || `(${key.split('|')[0]} 글자 없음)`
      const st = ctx.__st
      const before = {
        url: page.url(),
        mut: await page.evaluate(() => window.__nc.mut),
        reqs: st.reqs,
        pages: ctx.pages().length,
        errs: errs.length,
      }
      try {
        await page.locator(`[data-nc="${idx}"]`).click({ timeout: 4000 })
      } catch (e) {
        const shot = shotName(`${role}${tpl(url)}_${text}`)
        await page.screenshot({ path: path.join(OUT, 'shots', shot) }).catch(() => {})
        rec(
          /intercepts pointer events/.test(e.message)
            ? '눌리지 않음(다른 요소에 가려짐)'
            : '눌리지 않음',
          role,
          tpl(url),
          text + view,
          shot,
        )
        continue
      }
      await page.waitForTimeout(1500)
      const after = {
        url: page.url(),
        mut: await page.evaluate(() => window.__nc.mut).catch(() => Infinity),
        reqs: st.reqs,
        pages: ctx.pages().length,
      }
      if (
        after.url === before.url &&
        after.mut === before.mut &&
        after.reqs === before.reqs &&
        after.pages === before.pages
      ) {
        const shot = shotName(`${role}${tpl(url)}_${text}`)
        await page.screenshot({ path: path.join(OUT, 'shots', shot) }).catch(() => {})
        rec('눌러도 반응 없음', role, tpl(url), text + view, shot)
      }
      for (const e of errs.slice(before.errs))
        rec('누른 뒤 JS 오류', role, tpl(url), `"${text}" → ${e.replace(/\d+/g, 'N')}${view}`)
    } finally {
      for (const p of ctx.pages()) await p.close().catch(() => {})
    }
  }
}

const ORDER = [
  '눌러도 반응 없음',
  '눌리지 않음(다른 요소에 가려짐)',
  '눌리지 않음',
  '열리지 않음',
  '응답 5xx',
  '화면이 비었다 다시 채워짐',
  '하이드레이션 불일치',
  '잠깐 나타났다 사라짐',
  '레이아웃 흔들림',
  '누른 뒤 JS 오류',
  'JS·콘솔 오류',
  '응답 4xx',
  '로그인 실패',
]
function writeReport(round, started) {
  const list = [...found.values()].sort(
    (a, b) => ORDER.indexOf(a.type) - ORDER.indexOf(b.type) || b.count - a.count,
  )
  fs.writeFileSync(path.join(OUT, 'findings.json'), JSON.stringify(list, null, 2))
  const lines = [
    '# 밤샘 브라우저 점검',
    '',
    `대상: ${BASE} · 시작 ${started} · 마지막 갱신 ${new Date().toLocaleString('ko-KR')} · ${round}바퀴`,
    '',
    '「횟수/방문」은 그 화면을 연 횟수 중 문제가 보인 횟수예요. 1/12처럼 작으면 간헐적인 문제예요.',
    '',
  ]
  for (const type of ORDER) {
    const items = list.filter((f) => f.type === type)
    if (!items.length) continue
    lines.push(
      `## ${type} (${items.length})`,
      '',
      '| 역할 | 화면 | 내용 | 횟수/방문 | 스크린샷 |',
      '|---|---|---|---|---|',
    )
    for (const f of items) {
      const v = visits.get(`${f.role}|${f.page}`) ?? '?'
      lines.push(
        `| ${f.role} | ${f.page} | ${f.detail.replace(/\|/g, '\\|')} | ${f.count}/${v} | ${f.shot ? `[보기](shots/${encodeURI(f.shot)})` : ''} |`,
      )
    }
    lines.push('')
  }
  if (!list.length) lines.push('아직 찾은 문제가 없어요.')
  fs.writeFileSync(path.join(OUT, 'report.md'), lines.join('\n'))
}

async function main() {
  const started = new Date().toLocaleString('ko-KR')
  log(
    `시작: ${BASE} → ${QUICK ? '한 바퀴(QUICK)' : new Date(UNTIL).toLocaleString('ko-KR') + '까지'} · 결과 ${OUT}`,
  )
  const statics = staticRoutes()
  const samples = new Map() // 화면 → 실제 주소 몇 개 ([id] 화면은 돌다가 찾은 링크로 채운다)
  for (const p of statics) samples.set(p, [p])
  const browser = await chromium.launch()
  let round = 0
  while (!late()) {
    round++
    const mobile = round % 2 === 0
    const view = mobile ? ' [모바일]' : ''
    for (const role of ['guest', 'user', 'seller', 'admin']) {
      if (late()) break
      const ctx = await newContext(browser, role, mobile)
      if (!ctx) {
        if (round === 1) log(`${role}: 계정이 없거나 로그인 실패라 건너뜀`)
        continue
      }
      let pages = [...samples.entries()].filter(([t]) => ROLE_PREFIX[role](t))
      if (QUICK) pages = pages.slice(0, 5)
      log(`${round}바퀴 ${role}${view}: 화면 ${pages.length}개`)
      for (const [, urls] of pages) {
        for (const url of urls) {
          if (late()) break
          const links = await watch(ctx, role, url, view)
          for (const l of links) {
            const t = tpl(l)
            const s = samples.get(t) ?? []
            if (s.length < 2 && !s.includes(l)) samples.set(t, [...s, l])
          }
          await clickAll(ctx, role, url, view)
        }
        writeReport(round, started)
      }
      await ctx.close()
    }
    writeReport(round, started)
    log(`${round}바퀴 끝 · 발견 ${found.size}건`)
    if (QUICK) break
  }
  await browser.close()
  log(`끝. 보고서: ${path.join(OUT, 'report.md')}`)
}

main().catch((e) => {
  log('중단:', e.stack ?? e)
  process.exit(1)
})
