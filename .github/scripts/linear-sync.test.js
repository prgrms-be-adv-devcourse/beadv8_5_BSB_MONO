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
function setup({ draft, issues, failId }) {
  const moves = []
  global.fetch = async (_url, { body }) => {
    const { query, variables } = JSON.parse(body)
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
  process.env.LINEAR_API_KEY_ME = 'key'
  const warnings = []
  const core = { info() {}, warning: (m) => warnings.push(m), setFailed: (m) => warnings.push(`FAILED ${m}`) }
  const context = {
    payload: {
      action: 'opened',
      sender: { login: 'me' },
      pull_request: { draft, merged: false, number: 1, title: 't', html_url: 'u', body: '', user: { login: 'me' }, head: { ref: 'feat/BUK-1-a' } },
    },
  }
  return { moves, warnings, core, context }
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

test('핸들러: In Review 상태가 없는 팀이면 In Progress로 옮긴다', async () => {
  const t = setup({ draft: false, issues: { 'BUK-1': { state: todo, states: withoutReview } } })
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [['BUK-1', 'p']])
})

test('핸들러: 다른 팀 키(PRO-1)는 이슈로 보지 않는다', async () => {
  const t = setup({ draft: false, issues: {} })
  t.context.payload.pull_request.head.ref = 'feat/PRO-1-a'
  await sync({ context: t.context, core: t.core })
  assert.deepStrictEqual(t.moves, [])
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
