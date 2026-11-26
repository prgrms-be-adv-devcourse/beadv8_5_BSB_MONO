// 이슈를 GitHub 프로젝트(칸반)에 올리고 칸·영역·우선순위·주를 맞춘다. 칸 규칙 원본: docs/decisions/github-conventions.md 「칸반」.
// 칸은 이슈의 상태·라벨·담당자·연결된 PR로만 정해진다. 사람이 카드를 옮겨도 다음 이벤트에서 이 규칙대로 다시 덮어쓴다.
// PR은 프로젝트에 넣지 않고, PR이 연결된 이슈의 칸만 움직인다.
const { PROJECT_NUMBER } = require('./week-milestone.js')

const AREAS = [
  'member', 'product', 'order', 'queue', 'cash', 'payout', 'board', 'inquiry', 'notification',
  'file', 'ai', 'gateway', 'common', 'design', 'infra', 'ci', 'e2e', 'loadtest',
]
const PRIORITIES = ['긴급', '높음', '보통']
const WEEK_MILESTONE = /^\d+\/\d+ 주$/

// 칸을 정한다. 위에서부터 먼저 맞는 규칙이 이긴다.
// issue: { state, labels: [이름], assignees: [로그인] }, openPrs: 이 이슈에 연결된 열린 PR [{ draft }]
function decideStatus(issue, openPrs) {
  if (issue.state === 'closed') {
    return issue.labels.includes('취소') ? { archive: true } : { status: '완료' }
  }
  if (openPrs.some((pr) => !pr.draft)) {
    return { status: '리뷰' }
  }
  if (issue.assignees.length > 0 || openPrs.length > 0) {
    return { status: '진행' }
  }
  if (issue.labels.some((l) => l === '팩토리 구현' || l === '구현 대기')) {
    return { status: '준비' }
  }
  return { status: '백로그' }
}

// 이슈 양식 본문에서 `### 제목` 아래 첫 줄 값. 비었거나 「_No response_」면 빈 문자열.
function parseSection(body, heading) {
  const lines = (body ?? '').split(/\r?\n/)
  const at = lines.findIndex((l) => l.trim() === `### ${heading}`)
  if (at < 0) {
    return ''
  }
  const value = lines.slice(at + 1).find((l) => l.trim() !== '') ?? ''
  return value.trim().startsWith('###') || value.trim() === '_No response_' ? '' : value.trim()
}

// PR이 닫는 이슈 번호: 본문의 Closes/Fixes/Resolves #n, 브랜치 이름 <종류>/<n>-...
function linkedIssueNumbers(pr) {
  const nums = new Set()
  for (const m of (pr.body ?? '').matchAll(/\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s*:?\s+#(\d+)\b/gi)) {
    nums.add(Number(m[1]))
  }
  const branch = /^[a-z]+\/(\d+)-/i.exec(pr.head?.ref ?? '')
  if (branch) {
    nums.add(Number(branch[1]))
  }
  return [...nums]
}

// 프로젝트 id와 필드(이름으로 찾는다)
async function loadProject(github, owner) {
  const { organization } = await github.graphql(
    `query($o: String!, $n: Int!) { organization(login: $o) { projectV2(number: $n) { id fields(first: 50) { nodes {
      ... on ProjectV2FieldCommon { id name }
      ... on ProjectV2SingleSelectField { options { id name } }
      ... on ProjectV2IterationField { configuration { iterations { id title } completedIterations { id title } } }
    } } } } }`,
    { o: owner, n: PROJECT_NUMBER },
  )
  const p = organization.projectV2
  return { id: p.id, field: (name) => p.fields.nodes.find((f) => f.name === name) }
}

// 필드에 넣을 값을 {fieldId, value}로 만든다. 필드나 옵션을 못 찾으면 null.
function selectValue(field, name) {
  const option = field?.options?.find((o) => o.name === name)
  return option ? { fieldId: field.id, value: { singleSelectOptionId: option.id } } : null
}

function iterationValue(field, title) {
  const c = field?.configuration
  const it = c && [...c.iterations, ...c.completedIterations].find((i) => i.title === title)
  return it ? { fieldId: field.id, value: { iterationId: it.id } } : null
}

async function setFields(github, projectId, itemId, updates) {
  if (updates.length === 0) {
    return
  }
  const vars = updates.map((_, i) => `$f${i}: ID!, $v${i}: ProjectV2FieldValue!`).join(', ')
  const calls = updates
    .map((_, i) => `u${i}: updateProjectV2ItemFieldValue(input: { projectId: $p, itemId: $i, fieldId: $f${i}, value: $v${i} }) { clientMutationId }`)
    .join('\n')
  const variables = { p: projectId, i: itemId }
  updates.forEach((u, i) => {
    variables[`f${i}`] = u.fieldId
    variables[`v${i}`] = u.value
  })
  await github.graphql(`mutation($p: ID!, $i: ID!, ${vars}) { ${calls} }`, variables)
}

async function syncIssue({ github, core, repo, project, openPrs, number }) {
  const { data: issue } = await github.rest.issues.get({ ...repo, issue_number: number })
  if (issue.pull_request) {
    core.info(`#${number}은 PR이라 건너뜁니다.`)
    return
  }
  const labels = issue.labels.map((l) => (typeof l === 'string' ? l : l.name))
  const linked = openPrs.filter((pr) => linkedIssueNumbers(pr).includes(number))
  const decision = decideStatus(
    { state: issue.state, labels, assignees: issue.assignees.map((a) => a.login) },
    linked,
  )

  // 이미 프로젝트에 있으면 그 항목 id를 돌려준다
  const { addProjectV2ItemById } = await github.graphql(
    `mutation($p: ID!, $c: ID!) { addProjectV2ItemById(input: { projectId: $p, contentId: $c }) { item { id } } }`,
    { p: project.id, c: issue.node_id },
  )
  const itemId = addProjectV2ItemById.item.id

  if (decision.archive) {
    await github.graphql(
      `mutation($p: ID!, $i: ID!) { archiveProjectV2Item(input: { projectId: $p, itemId: $i }) { clientMutationId } }`,
      { p: project.id, i: itemId },
    )
    core.info(`#${number}: 취소로 닫혀 프로젝트 항목을 보관했습니다.`)
    return
  }

  const area = parseSection(issue.body, '담당 서비스')
  const priority = parseSection(issue.body, '우선순위')
  const week = issue.milestone && WEEK_MILESTONE.test(issue.milestone.title) ? issue.milestone.title : ''
  const wanted = [
    ['Status', selectValue(project.field('Status'), decision.status)],
    AREAS.includes(area) && ['영역', selectValue(project.field('영역'), area)],
    PRIORITIES.includes(priority) && ['우선순위', selectValue(project.field('우선순위'), priority)],
    week && ['주', iterationValue(project.field('주'), week)],
  ].filter(Boolean)
  const updates = []
  for (const [name, update] of wanted) {
    if (update) {
      updates.push(update)
    } else {
      core.warning(`#${number}: 프로젝트에서 '${name}' 필드나 값을 찾지 못해 건너뜁니다.`)
    }
  }
  await setFields(github, project.id, itemId, updates)
  core.info(`#${number}: 칸 '${decision.status}'${week ? `, 주 '${week}'` : ''}`)
}

module.exports = async ({ github, context, core }) => {
  const { payload, eventName, repo } = context
  const numbers = eventName === 'pull_request' ? linkedIssueNumbers(payload.pull_request) : [payload.issue.number]
  if (numbers.length === 0) {
    core.info('연결된 이슈가 없어 건너뜁니다.')
    return
  }
  const project = await loadProject(github, repo.owner)
  const openPrs = await github.paginate(github.rest.pulls.list, { ...repo, state: 'open', per_page: 100 })
  for (const number of numbers) {
    await syncIssue({ github, core, repo, project, openPrs, number })
  }
}

module.exports.decideStatus = decideStatus
module.exports.parseSection = parseSection
module.exports.linkedIssueNumbers = linkedIssueNumbers
module.exports.loadProject = loadProject
