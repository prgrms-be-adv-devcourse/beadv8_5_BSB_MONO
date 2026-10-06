// PR과 Linear 이슈를 잇는다. 공식 GitHub 연동 대신 쓴다 (docs/LINEAR.md).
// - PR이 열리거나 본문이 바뀌면: 이슈에 PR 링크를 붙인다
// - PR이 머지되면: 닫는 키워드(Closes 등)로 적은 이슈를 Done으로 옮긴다

const TEAM_KEY = 'PRO'
const LINEAR_API = 'https://api.linear.app/graphql'

// 브랜치 이름이나 본문 어디에 있든 언급된 이슈 (링크용)
function findMentioned(text) {
  const re = new RegExp(`\\b${TEAM_KEY}-\\d+\\b`, 'gi')
  return [...new Set((text.match(re) ?? []).map((id) => id.toUpperCase()))]
}

// "Closes PRO-12"처럼 닫는 키워드 뒤에 온 이슈만 (Done용)
function findClosing(text) {
  const re = new RegExp(
    `\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\\s*:?\\s+(${TEAM_KEY}-\\d+)\\b`,
    'gi',
  )
  return [...new Set([...text.matchAll(re)].map((m) => m[1].toUpperCase()))]
}

async function linear(apiKey, query, variables) {
  const res = await fetch(LINEAR_API, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: apiKey },
    body: JSON.stringify({ query, variables }),
  })
  const json = await res.json()
  if (!res.ok || json.errors) {
    throw new Error(JSON.stringify(json.errors ?? json))
  }
  return json.data
}

async function attachPr(apiKey, issueId, pr) {
  // 같은 URL은 Linear가 한 번만 붙인다. 본문을 고쳐 다시 돌아도 중복되지 않는다.
  await linear(
    apiKey,
    `mutation($issueId: String!, $url: String!, $title: String) {
      attachmentLinkURL(issueId: $issueId, url: $url, title: $title) { success }
    }`,
    { issueId, url: pr.html_url, title: `PR #${pr.number}: ${pr.title}` },
  )
}

async function markDone(apiKey, issueId) {
  const data = await linear(
    apiKey,
    `query($id: String!) {
      issue(id: $id) {
        id
        state { type }
        team { states(filter: { type: { eq: "completed" } }) { nodes { id name } } }
      }
    }`,
    { id: issueId },
  )
  const { issue } = data
  if (issue.state.type === 'completed' || issue.state.type === 'canceled') {
    return 'skipped'
  }
  const states = issue.team.states.nodes
  const done = states.find((s) => s.name === 'Done') ?? states[0]
  await linear(
    apiKey,
    `mutation($id: String!, $stateId: String!) {
      issueUpdate(id: $id, input: { stateId: $stateId }) { success }
    }`,
    { id: issue.id, stateId: done.id },
  )
  return 'done'
}

module.exports = async ({ context, core }) => {
  const apiKey = process.env.LINEAR_API_KEY
  if (!apiKey) {
    core.notice('LINEAR_API_KEY 시크릿이 없어 Linear 동기화를 건너뜁니다.')
    return
  }

  const pr = context.payload.pull_request
  const text = `${pr.head.ref}\n${pr.title}\n${pr.body ?? ''}`
  const mentioned = findMentioned(text)
  if (mentioned.length === 0) {
    core.info('PR에 이슈 번호가 없습니다.')
    return
  }

  // 이슈 하나가 실패해도 나머지는 처리하고, 마지막에 실패로 알린다
  let failed = 0
  const run = async (id, fn) => {
    try {
      core.info(`${id}: ${(await fn()) ?? 'ok'}`)
    } catch (e) {
      failed += 1
      core.warning(`${id}: ${e.message}`)
    }
  }

  for (const id of mentioned) {
    await run(id, () => attachPr(apiKey, id, pr))
  }
  if (context.payload.action === 'closed' && pr.merged) {
    for (const id of findClosing(pr.body ?? '')) {
      await run(id, () => markDone(apiKey, id))
    }
  }
  if (failed > 0) {
    core.setFailed(`${failed}건 실패. 위 경고를 확인하세요.`)
  }
}

module.exports.findMentioned = findMentioned
module.exports.findClosing = findClosing
