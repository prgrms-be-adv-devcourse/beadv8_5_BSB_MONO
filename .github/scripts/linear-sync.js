// PR과 Linear 이슈를 잇는다. 공식 GitHub 연동이 동작하지 않아 대신 쓴다 (docs/LINEAR.md).
// - PR이 열리거나 바뀌면: 언급된 이슈에 PR 링크를 붙이고, 초안이 아닌 PR이면 브랜치·제목·닫는 키워드의 이슈를 In Review로 옮긴다
//   (Backlog·Todo·In Progress 이슈만. 본문 수정 때는 Backlog·Todo만. 이미 In Review·Done인 이슈와 Refs로만 적은 이슈는 그대로. 팀에 In Review 상태가 없으면 경고만 남기고 옮기지 않는다)
//   초안 PR은 Backlog·Todo 이슈를 In Progress까지만 옮긴다
// - PR이 머지되면: 브랜치 이름·제목에 있거나 닫는 키워드(Closes 등)로 적은 이슈를 Done으로 옮긴다
//   본문에 Refs BUK-12로만 적은 이슈는 링크만 붙고 Done이 되지 않는다
// - Linear에 바꾼 사람이 제대로 남도록, 이벤트를 일으킨 사람(머지한 사람 등)의 개인 키를 쓴다.
//   그 사람 키가 없으면 PR 작성자 키를 쓴다

// 최종 프로젝트의 새 워크스페이스 팀 키. 세미 때 쓰던 PRO 이슈는 이전 워크스페이스에 있어 새 API 키로 찾을 수 없다
const TEAM_KEY = 'BUK'
const LINEAR_API = 'https://api.linear.app/graphql'

// 브랜치 이름, 제목, 본문 어디에 있든 언급된 이슈 (링크용)
function findMentioned(text) {
  const re = new RegExp(`\\b${TEAM_KEY}-\\d+\\b`, 'gi')
  return [...new Set((text.match(re) ?? []).map((id) => id.toUpperCase()))]
}

// "Closes BUK-12"처럼 닫는 키워드 뒤에 온 이슈만
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
  // 같은 URL은 Linear가 한 번만 붙이고, 두 번째부터는 "already been linked" 오류를 낸다.
  // PR 본문을 고치거나 머지할 때마다 다시 돌기 때문에 이 오류는 실패로 치지 않는다.
  try {
    await linear(
      apiKey,
      `mutation($issueId: String!, $url: String!, $title: String) {
        attachmentLinkURL(issueId: $issueId, url: $url, title: $title) { success }
      }`,
      { issueId, url: pr.html_url, title: `PR #${pr.number} ${pr.title}` },
    )
  } catch (e) {
    if (isAlreadyLinked(e)) {
      return 'already linked'
    }
    if (isNotFound(e)) {
      return 'not found, skipped'
    }
    throw e
  }
}

function isAlreadyLinked(error) {
  return /already been linked/i.test(error.message)
}

// 이슈 번호가 이 워크스페이스에 없을 때(오타, 이전 워크스페이스의 이슈 등)
function isNotFound(error) {
  return /not found/i.test(error.message)
}

// 이슈를 어느 상태에서 어느 상태로 옮길지. In Review와 In Progress는 둘 다 started 종류라 이름으로 구분한다.
// fallback이 false이면 이름이 같은 상태가 없을 때 다른 상태로 대신 옮기지 않는다
const RULES = {
  progress: { from: ['backlog', 'unstarted'], type: 'started', name: 'In Progress', fallback: true },
  review: { from: ['backlog', 'unstarted', 'started'], type: 'started', name: 'In Review', fallback: false },
  done: { from: ['backlog', 'unstarted', 'started'], type: 'completed', name: 'Done', fallback: true },
}
const NO_STATE = 'no-state'

// { skip } 또는 { target }
function decideMove(issue, rule) {
  if (!rule.from.includes(issue.state.type) || issue.state.name === rule.name) {
    return { skip: `skipped (${issue.state.name})` }
  }
  const states = issue.team.states.nodes
  const target = states.find((s) => s.name === rule.name) ?? (rule.fallback ? states[0] : undefined)
  return target ? { target } : { skip: NO_STATE }
}

// rule에 맞는 이슈만 옮기고, 옮긴 상태 이름이나 건너뛴 이유를 돌려준다
async function moveState(apiKey, issueId, rule) {
  let data
  try {
    data = await linear(
      apiKey,
      `query($id: String!, $type: String!) {
        issue(id: $id) {
          id
          state { type name }
          team { states(filter: { type: { eq: $type } }) { nodes { id name } } }
        }
      }`,
      { id: issueId, type: rule.type },
    )
  } catch (e) {
    // 없는 이슈 번호는 attachPr처럼 실패로 세지 않고 건너뛴다
    if (isNotFound(e)) {
      return 'not found, skipped'
    }
    throw e
  }
  const { issue } = data
  if (!issue) {
    return 'not found, skipped'
  }
  const { skip, target } = decideMove(issue, rule)
  if (skip) {
    return skip
  }
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
      const result = (await fn()) ?? 'ok'
      // 오타가 아니라 키가 BUK 워크스페이스 이슈를 못 보는 경우도 같은 응답이라, 실행 요약에 보이게 경고로 남긴다
      if (result === NO_STATE) {
        core.warning(`${id}: 팀에 ${RULES.review.name} 상태가 없어 옮기지 않았습니다. Linear 팀 설정에서 추가하세요.`)
      } else if (result.startsWith('not found')) {
        core.warning(`${id}: ${result}. 번호가 맞다면 ${picked.name} 키가 ${TEAM_KEY} 워크스페이스 것인지 확인하세요.`)
      } else {
        core.info(`${id}: ${result}`)
      }
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
        await run(id, () => moveState(apiKey, id, RULES.done))
      }
    }
  } else {
    // 본문 수정 때는 In Progress로 되돌려 둔 이슈를 다시 In Review로 끌어오지 않는다
    const reviewRule =
      context.payload.action === 'edited' ? { ...RULES.review, from: RULES.progress.from } : RULES.review
    // Refs로만 적은 이슈는 링크만 붙이고, 브랜치·제목·닫는 키워드로 가리킨 이슈만 상태를 옮긴다
    const owned = pr.draft ? mentioned : findToComplete(pr)
    for (const id of owned) {
      await run(id, () => (pr.draft ? moveState(apiKey, id, RULES.progress) : moveState(apiKey, id, reviewRule)))
    }
  }
  if (failed > 0) {
    core.setFailed(`${failed}건 실패. 위 경고를 확인하세요.`)
  }
}

module.exports.decideMove = decideMove
module.exports.RULES = RULES
module.exports.findMentioned = findMentioned
module.exports.findClosing = findClosing
module.exports.findToComplete = findToComplete
module.exports.keyName = keyName
module.exports.pickApiKey = pickApiKey
module.exports.isAlreadyLinked = isAlreadyLinked
module.exports.isNotFound = isNotFound
