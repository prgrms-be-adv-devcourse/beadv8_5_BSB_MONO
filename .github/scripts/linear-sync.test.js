// 실행: node --test .github/scripts/linear-sync.test.js
const test = require('node:test')
const assert = require('node:assert')
const sync = require('./linear-sync.js')

const { decideMove, RULES } = sync
const progress = { id: 's-progress', name: 'In Progress' }
const review = { id: 's-review', name: 'In Review' }

function issue(type, name, states) {
  return { state: { type, name }, team: { states: { nodes: states } } }
}

test('리뷰 규칙: Backlog·Todo·In Progress는 In Review로 옮긴다', () => {
  for (const [type, name] of [['backlog', 'Backlog'], ['unstarted', 'Todo'], ['started', 'In Progress']]) {
    const r = decideMove(issue(type, name, [progress, review]), RULES.review)
    assert.deepStrictEqual(r, { target: review })
  }
})

test('리뷰 규칙: 이미 In Review나 Done이면 그대로 둔다', () => {
  assert.match(decideMove(issue('started', 'In Review', [progress, review]), RULES.review).skip, /skipped/)
  assert.match(decideMove(issue('completed', 'Done', [progress, review]), RULES.review).skip, /skipped/)
  assert.match(decideMove(issue('canceled', 'Canceled', [progress, review]), RULES.review).skip, /skipped/)
})

test('리뷰 규칙: In Review 상태가 없으면 다른 상태로 대신 옮기지 않는다', () => {
  assert.deepStrictEqual(decideMove(issue('unstarted', 'Todo', [progress]), RULES.review), { skip: 'no-state' })
})

test('진행 규칙: Backlog·Todo만 In Progress로 옮기고 In Review는 건드리지 않는다', () => {
  assert.deepStrictEqual(decideMove(issue('backlog', 'Backlog', [progress, review]), RULES.progress), { target: progress })
  assert.match(decideMove(issue('started', 'In Review', [progress, review]), RULES.progress).skip, /skipped/)
})

test('완료 규칙: In Review에서도 Done으로 옮긴다', () => {
  const done = { id: 's-done', name: 'Done' }
  assert.deepStrictEqual(decideMove(issue('started', 'In Review', [done]), RULES.done), { target: done })
})

// Linear API를 흉내 낸 fetch로 핸들러 전체를 돌린다
function setup({ draft, issues, failId, env = { LINEAR_API_KEY_ME: 'key' } }) {
  const moves = []
  const auths = []
  global.fetch = async (_url, { headers, body }) => {
    const { query, variables } = JSON.parse(body)
    auths.push([variables.issueId ?? variables.id, headers.Authorization])
    const ok = (data) => ({ ok: true, json: async () => ({ data }) })
    if (query.includes('attachmentLinkURL')) {
      return ok({ attachmentLinkURL: { success: true } })
    }
    if (query.includes('issueUpdate')) {
      moves.push([variables.id, variables.stateId])
      return ok({ issueUpdate: { success: true } })
    }
    if (variables.id === failId) {
      return { ok: false, json: async () => ({ errors: [{ message: 'boom' }] }) }
    }
    const i = issues[variables.id]
    const nodes = i.states.filter((s) => s.type === variables.type)
    return ok({ issue: { id: variables.id, state: i.state, team: { states: { nodes } } } })
  }
  for (const name of Object.keys(process.env).filter((n) => n.startsWith('LINEAR_API_KEY_'))) {
    delete process.env[name]
  }
  Object.assign(process.env, env)
  const warnings = []
  const core = { info() {}, warning: (m) => warnings.push(m), setFailed: (m) => warnings.push(`FAILED ${m}`) }
  const context = {
    payload: {
      action: 'opened',
      sender: { login: 'me' },
      pull_request: { draft, merged: false, number: 1, title: 't', html_url: 'u', body: '', user: { login: 'me' }, head: { ref: 'feat/BUK-1-a' } },
    },
  }
  return { moves, auths, warnings, core, context }
}

const withReview = [
  { id: 'p', name: 'In Progress', type: 'started' },
  { id: 'r', name: 'In Review', type: 'started' },
]
const withoutReview = [{ id: 'p', name: 'In Progress', type: 'started' }]
const todo = { type: 'unstarted', name: 'Todo' }

test('핸들러: 초안이 아닌 PR은 In Review로 옮긴다', async () => {
  const t = setup({ draft: false, issues: { 'BUK-1': { state: todo, states: withReview } } })
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'r']])
})

test('핸들러: 초안 PR은 In Progress까지만 옮긴다', async () => {
  const t = setup({ draft: true, issues: { 'BUK-1': { state: todo, states: withReview } } })
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'p']])
})

test('핸들러: In Review 상태가 없는 팀이면 옮기지 않고 경고만 남긴다', async () => {
  const t = setup({ draft: false, issues: { 'BUK-1': { state: todo, states: withoutReview } } })
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [])
  assert.ok(t.warnings.some((w) => w.includes('In Review')))
  assert.ok(!t.warnings.some((w) => w.startsWith('FAILED')))
})

test('핸들러: 다른 팀 키(PRO-1)는 이슈로 보지 않는다', async () => {
  assert.deepStrictEqual(sync.findMentioned('feat/PRO-1-a'), [])
})

test('핸들러: 본문에 Refs로만 적은 이슈는 옮기지 않는다', async () => {
  const t = setup({ draft: false, issues: { 'BUK-1': { state: todo, states: withReview }, 'BUK-9': { state: todo, states: withReview } } })
  t.context.payload.pull_request.body = 'Refs BUK-9'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'r']])
})

test('핸들러: 초안 PR도 본문에 Refs로만 적은 이슈는 옮기지 않는다', async () => {
  const t = setup({ draft: true, issues: { 'BUK-1': { state: todo, states: withReview }, 'BUK-9': { state: todo, states: withReview } } })
  t.context.payload.pull_request.body = 'Refs BUK-9'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'p']])
})

test('핸들러: 본문 수정(edited)은 이미 진행 중인 이슈를 In Review로 되돌리지 않는다', async () => {
  const t = setup({ draft: false, issues: { 'BUK-1': { state: { type: 'started', name: 'In Progress' }, states: withReview } } })
  t.context.payload.action = 'edited'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [])
})

test('핸들러: 머지되면 Done으로 옮기고, 머지 없이 닫으면 옮기지 않는다', async () => {
  const done = { id: 'd', name: 'Done', type: 'completed' }
  const issues = { 'BUK-1': { state: { type: 'started', name: 'In Review' }, states: [done] } }
  const merged = setup({ draft: false, issues })
  merged.context.payload.action = 'closed'
  merged.context.payload.pull_request.merged = true
  await sync({ context: merged.context, core: merged.core })
  assert.deepStrictEqual(merged.moves, [['BUK-1', 'd']])

  const closed = setup({ draft: false, issues })
  closed.context.payload.action = 'closed'
  await sync({ context: closed.context, core: closed.core })
  assert.deepStrictEqual(closed.moves, [])
})

test('핸들러: 한 이슈가 실패해도 나머지는 옮기고 마지막에 실패로 알린다', async () => {
  const t = setup({
    draft: false,
    failId: 'BUK-1',
    issues: { 'BUK-2': { state: todo, states: withReview } },
  })
  t.context.payload.pull_request.title = 'BUK-2'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-2', 'r']])
  assert.ok(t.warnings.some((w) => w.startsWith('FAILED')))
})

// 워크스페이스 두 개(BUK 기존, CRU 새로) — 이슈 키 앞부분으로 API 키를 고른다
const failed = (t) => t.warnings.filter((w) => w.startsWith('FAILED'))
const authsOf = (t, id) => [...new Set(t.auths.filter(([i]) => i === id).map(([, a]) => a))]

test('이슈 찾기: BUK와 CRU를 둘 다 찾고, PRO는 여전히 찾지 않는다', () => {
  assert.deepStrictEqual(sync.findMentioned('feat/CRU-3-a BUK-1 cru-4 PRO-5'), ['CRU-3', 'BUK-1', 'CRU-4'])
  assert.deepStrictEqual(sync.findClosing('Closes CRU-7\nFixes BUK-2\nRefs CRU-8'), ['CRU-7', 'BUK-2'])
})

test('시크릿 이름: BUK은 지금 이름, CRU는 CRU_가 붙은 이름', () => {
  assert.strictEqual(sync.keyName('Dawit-lee', 'BUK'), 'LINEAR_API_KEY_DAWIT_LEE')
  assert.strictEqual(sync.keyName('Dawit-lee', 'CRU'), 'LINEAR_API_KEY_CRU_DAWIT_LEE')
})

test('핸들러: CRU 이슈는 CRU 키로 처리한다', async () => {
  const t = setup({
    draft: false,
    issues: { 'CRU-1': { state: todo, states: withReview } },
    env: { LINEAR_API_KEY_ME: 'buk-key', LINEAR_API_KEY_CRU_ME: 'cru-key' },
  })
  t.context.payload.pull_request.head.ref = 'feat/CRU-1-a'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['CRU-1', 'r']])
  assert.deepStrictEqual(authsOf(t, 'CRU-1'), ['cru-key'])
})

test('핸들러: 한 PR에 BUK·CRU가 섞이면 각자 자기 키로 처리한다', async () => {
  const t = setup({
    draft: false,
    issues: { 'BUK-1': { state: todo, states: withReview }, 'CRU-2': { state: todo, states: withReview } },
    env: { LINEAR_API_KEY_ME: 'buk-key', LINEAR_API_KEY_CRU_ME: 'cru-key' },
  })
  t.context.payload.pull_request.body = 'Closes CRU-2'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'r'], ['CRU-2', 'r']])
  assert.deepStrictEqual(authsOf(t, 'BUK-1'), ['buk-key'])
  assert.deepStrictEqual(authsOf(t, 'CRU-2'), ['cru-key'])
})

test('핸들러: CRU 키가 없으면 CRU는 경고만 남기고 BUK는 처리한다', async () => {
  const t = setup({
    draft: false,
    issues: { 'BUK-1': { state: todo, states: withReview }, 'CRU-2': { state: todo, states: withReview } },
    env: { LINEAR_API_KEY_ME: 'buk-key' },
  })
  t.context.payload.pull_request.body = 'Closes CRU-2'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'r']])
  assert.ok(t.warnings.some((w) => w.includes('LINEAR_API_KEY_CRU_ME')))
  assert.deepStrictEqual(failed(t), [])
})

test('핸들러: BUK 키가 없으면 BUK는 경고만 남기고 CRU는 처리한다', async () => {
  const t = setup({
    draft: false,
    issues: { 'BUK-1': { state: todo, states: withReview }, 'CRU-2': { state: todo, states: withReview } },
    env: { LINEAR_API_KEY_CRU_ME: 'cru-key' },
  })
  t.context.payload.pull_request.body = 'Closes CRU-2'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['CRU-2', 'r']])
  assert.ok(t.warnings.some((w) => w.includes('LINEAR_API_KEY_ME')))
  assert.deepStrictEqual(failed(t), [])
})

test('핸들러: BUK 이슈가 Linear 오류로 실패해도 CRU는 처리하고 마지막에 실패로 알린다', async () => {
  const t = setup({
    draft: false,
    failId: 'BUK-1',
    issues: { 'CRU-2': { state: todo, states: withReview } },
    env: { LINEAR_API_KEY_ME: 'buk-key', LINEAR_API_KEY_CRU_ME: 'cru-key' },
  })
  t.context.payload.pull_request.body = 'Closes CRU-2'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['CRU-2', 'r']])
  assert.strictEqual(failed(t).length, 1)
})

test('핸들러: 머지한 사람 키가 없으면 그 워크스페이스는 PR 작성자 키를 쓴다', async () => {
  const done = { id: 'd', name: 'Done', type: 'completed' }
  const t = setup({
    draft: false,
    issues: { 'CRU-1': { state: { type: 'started', name: 'In Review' }, states: [done] } },
    env: { LINEAR_API_KEY_CRU_ME: 'author-cru', LINEAR_API_KEY_MERGER: 'merger-buk' },
  })
  t.context.payload.action = 'closed'
  t.context.payload.sender.login = 'merger'
  t.context.payload.pull_request.merged = true
  t.context.payload.pull_request.head.ref = 'feat/CRU-1-a'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['CRU-1', 'd']])
  assert.deepStrictEqual(authsOf(t, 'CRU-1'), ['author-cru'])
})

// 시크릿 이름이 워크플로·스크립트·문서에서 같아야 한다
test('계약: 워크플로가 팀원 다섯 명의 BUK·CRU 시크릿을 같은 이름으로 넘긴다', () => {
  const fs = require('node:fs')
  const path = require('node:path')
  const root = path.join(__dirname, '..', '..')
  const yml = fs.readFileSync(path.join(root, '.github/workflows/linear-sync.yml'), 'utf8')
  const guide = fs.readFileSync(path.join(root, 'docs/LINEAR-API-KEY.md'), 'utf8')
  for (const login of ['Jieunsse', 'khb3025', 'bbangHo', 'ksoheee', 'Dawit-lee']) {
    for (const team of ['BUK', 'CRU']) {
      const name = sync.keyName(login, team)
      assert.ok(yml.includes(`${name}: \${{ secrets.${name} }}`), `워크플로에 ${name} 없음`)
      assert.ok(guide.includes(name), `LINEAR-API-KEY.md에 ${name} 없음`)
    }
  }
  assert.ok(!/\$\{\{\s*toJSON\(secrets\)/.test(yml), '모든 시크릿을 넘기면 안 됨')
})

test('핸들러: 머지 때도 BUK·CRU가 섞이면 각자 자기 키로 Done이 되고, 키 없는 쪽만 건너뛴다', async () => {
  const done = { id: 'd', name: 'Done', type: 'completed' }
  const inReview = { type: 'started', name: 'In Review' }
  const issues = { 'BUK-1': { state: inReview, states: [done] }, 'CRU-2': { state: inReview, states: [done] } }
  const merge = (env) => {
    const t = setup({ draft: false, issues, env })
    t.context.payload.action = 'closed'
    t.context.payload.pull_request.merged = true
    t.context.payload.pull_request.body = 'Closes cru-2'
    return t
  }
  const both = merge({ LINEAR_API_KEY_ME: 'buk-key', LINEAR_API_KEY_CRU_ME: 'cru-key' })
  await sync({ context: both.context, core: both.core })
  assert.deepStrictEqual(both.moves, [['BUK-1', 'd'], ['CRU-2', 'd']])
  assert.deepStrictEqual(authsOf(both, 'CRU-2'), ['cru-key'])

  const onlyBuk = merge({ LINEAR_API_KEY_ME: 'buk-key' })
  await sync({ context: onlyBuk.context, core: onlyBuk.core })
  assert.deepStrictEqual(onlyBuk.moves, [['BUK-1', 'd']])
  assert.deepStrictEqual(failed(onlyBuk), [])
})
