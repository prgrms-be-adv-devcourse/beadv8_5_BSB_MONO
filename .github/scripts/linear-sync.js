// PR과 Linear 이슈를 잇는다. 공식 GitHub 연동이 동작하지 않아 대신 쓴다 (docs/LINEAR.md).
// - PR이 열리거나 바뀌면: 언급된 이슈에 PR 링크를 붙이고, 시작 전 이슈는 In Progress로 옮긴다
// - PR이 머지되면: 브랜치 이름·제목에 있거나 닫는 키워드(Closes 등)로 적은 이슈를 Done으로 옮긴다
//   본문에 Refs PRO-12로만 적은 이슈는 링크만 붙고 Done이 되지 않는다
// - Linear에 바꾼 사람이 제대로 남도록, 이벤트를 일으킨 사람(머지한 사람 등)의 개인 키를 쓴다.
//   그 사람 키가 없으면 PR 작성자 키를 쓴다

const TEAM_KEY = 'PRO'
const LINEAR_API = 'https://api.linear.app/graphql'

// 브랜치 이름, 제목, 본문 어디에 있든 언급된 이슈 (링크용)
function findMentioned(text) {
  const re = new RegExp(`\\b${TEAM_KEY}-\\d+\\b`, 'gi')
  return [...new Set((text.match(re) ?? []).map((id) => id.toUpperCase()))]
}

// "Closes PRO-12"처럼 닫는 키워드 뒤에 온 이슈만
function findClosing(text) {
  const re = new RegExp(
    `\\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\\s*:?\\s+(${TEAM_KEY}-\\d+)\\b`,
    'gi',
  )
  return [...new Set([...text.matchAll(re)].map((m) => m[1].toUpperCase()))]
}

// 머지되면 Done으로 옮길 이슈: 브랜치 이름·제목의 번호 + 본문의 닫는 키워드
function findToComplete(pr) {
  return [...new Set([...findMentioned(`${pr.head.ref}\n${pr.title}`), ...findClosing(pr.body ?? '')])]
}

// 시크릿 이름은 대문자·숫자·밑줄만 쓸 수 있어 GitHub 아이디의 '-'를 '_'로 바꾼다
function keyName(login) {
  return `LINEAR_API_KEY_${login.toUpperCase().replace(/-/g, '_')}`
}

function pickApiKey(logins, env) {
  for (const login of logins) {
    const name = keyName(login)
    if (env[name]) {
      return { apiKey: env[name], name }
    }
  }
  return null
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
    { issueId, url: pr.html_url, title: `PR #${pr.number} ${pr.title}` },
  )
}

// fromTypes 상태인 이슈만 toType 상태(이름이 toName이면 그것, 없으면 첫 번째)로 옮긴다
async function moveState(apiKey, issueId, fromTypes, toType, toName) {
  const data = await linear(
    apiKey,
    `query($id: String!, $type: String!) {
      issue(id: $id) {
        id
        state { type }
        team { states(filter: { type: { eq: $type } }) { nodes { id name } } }
      }
    }`,
    { id: issueId, type: toType },
  )
  const { issue } = data
  if (!fromTypes.includes(issue.state.type)) {
    return `skipped (${issue.state.type})`
  }
  const states = issue.team.states.nodes
  const target = states.find((s) => s.name === toName) ?? states[0]
  await linear(
    apiKey,
    `mutation($id: String!, $stateId: String!) {
      issueUpdate(id: $id, input: { stateId: $stateId }) { success }
    }`,
    { id: issue.id, stateId: target.id },
  )
  return target.name
}

module.exports = async ({ context, core }) => {
  const pr = context.payload.pull_request
  const logins = [...new Set([context.payload.sender.login, pr.user.login])]
  const picked = pickApiKey(logins, process.env)
  if (!picked) {
    core.warning(
      `${logins.map(keyName).join(', ')} 시크릿이 없어 Linear 동기화를 건너뜁니다. docs/LINEAR-API-KEY.md를 보고 등록하세요.`,
    )
    return
  }
  const { apiKey } = picked
  core.info(`${picked.name} 키로 동기화합니다.`)

  const mentioned = findMentioned(`${pr.head.ref}\n${pr.title}\n${pr.body ?? ''}`)
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
  if (context.payload.action === 'closed') {
    if (pr.merged) {
      for (const id of findToComplete(pr)) {
        await run(id, () => moveState(apiKey, id, ['backlog', 'unstarted', 'started'], 'completed', 'Done'))
      }
    }
  } else if (!pr.draft) {
    for (const id of mentioned) {
      await run(id, () => moveState(apiKey, id, ['backlog', 'unstarted'], 'started', 'In Progress'))
    }
  }
  if (failed > 0) {
    core.setFailed(`${failed}건 실패. 위 경고를 확인하세요.`)
  }
}

module.exports.findMentioned = findMentioned
module.exports.findClosing = findClosing
module.exports.findToComplete = findToComplete
module.exports.keyName = keyName
module.exports.pickApiKey = pickApiKey
