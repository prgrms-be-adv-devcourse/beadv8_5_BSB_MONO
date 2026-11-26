// 주 단위 마일스톤(「M/D 주」)과 프로젝트의 「주」 반복 주기를 다룬다.
// 한 주는 한국 시간 월요일에 시작해 일요일에 끝난다. 제목의 날짜는 그 주의 월요일이다.
const DAY = 86400000
const PROJECT_NUMBER = 95

const iso = (d) => d.toISOString().slice(0, 10)

// now(Date)가 속한 한국 시간 기준 주
function weekOf(now) {
  const k = new Date(now.getTime() + 9 * 3600000)
  const monday = new Date(Date.UTC(k.getUTCFullYear(), k.getUTCMonth(), k.getUTCDate() - ((k.getUTCDay() + 6) % 7)))
  const sunday = new Date(monday.getTime() + 6 * DAY)
  return {
    title: `${monday.getUTCMonth() + 1}/${monday.getUTCDate()} 주`,
    start: iso(monday),
    // 12:00Z로 보내야 GitHub가 마감일을 일요일로 저장한다(00:00Z는 하루 앞당겨 저장된다)
    dueOn: `${iso(sunday)}T12:00:00Z`,
  }
}

const weekAfter = (now, n) => weekOf(new Date(now.getTime() + n * 7 * DAY))

// 같은 제목의 마일스톤이 없으면 만든다. 다른 실행이 먼저 만들었으면(422) 다시 찾는다.
async function ensureMilestone(github, repo, week) {
  const find = async () =>
    (await github.paginate(github.rest.issues.listMilestones, { ...repo, state: 'all', per_page: 100 })).find(
      (m) => m.title === week.title,
    )
  const found = await find()
  if (found) {
    return found
  }
  try {
    return (await github.rest.issues.createMilestone({ ...repo, title: week.title, due_on: week.dueOn })).data
  } catch (e) {
    if (e.status === 422) {
      return find()
    }
    throw e
  }
}

// 담당자가 지정된 이슈에 마일스톤이 없으면 이번 주 마일스톤을 붙인다 (GITHUB_TOKEN으로 된다).
async function attachThisWeek({ github, context, core }) {
  const issue = context.payload.issue
  const { data: fresh } = await github.rest.issues.get({ ...context.repo, issue_number: issue.number })
  if (fresh.milestone) {
    core.info(`이미 마일스톤이 있습니다: ${fresh.milestone.title}`)
    return
  }
  const m = await ensureMilestone(github, context.repo, weekOf(new Date()))
  await github.rest.issues.update({ ...context.repo, issue_number: issue.number, milestone: m.number })
  core.info(`마일스톤 '${m.title}'을 붙였습니다.`)
}

// 이번 주와 다음 주 마일스톤을 만든다.
async function ensureMilestones({ github, context, core }) {
  for (const week of [weekOf(new Date()), weekAfter(new Date(), 1)]) {
    const m = await ensureMilestone(github, context.repo, week)
    core.info(`마일스톤 '${m.title}' 확인`)
  }
}

// 프로젝트 「주」 필드에 이번 주와 다음 주 주기가 없으면 더한다. PROJECT_TOKEN으로 만든 github를 받는다.
// 주기를 바꾸는 mutation은 기존 주기를 모두 다시 보내야 하므로, 기존 주기는 id를 그대로 넣어 보낸다.
async function ensureIterations({ github, context, core }) {
  const { organization } = await github.graphql(
    `query($o: String!, $n: Int!) { organization(login: $o) { projectV2(number: $n) { field(name: "주") {
      ... on ProjectV2IterationField { id configuration { iterations { id title startDate duration } completedIterations { id title startDate duration } } } } } } }`,
    { o: context.repo.owner, n: PROJECT_NUMBER },
  )
  const field = organization.projectV2.field
  const all = [...field.configuration.completedIterations, ...field.configuration.iterations]
  const missing = [weekOf(new Date()), weekAfter(new Date(), 1)].filter((w) => !all.some((i) => i.title === w.title))
  if (missing.length === 0) {
    core.info('주 필드에 이번 주·다음 주 주기가 이미 있습니다.')
    return
  }
  const iterations = [
    ...all.map(({ id, title, startDate, duration }) => ({ id, title, startDate, duration })),
    ...missing.map((w) => ({ title: w.title, startDate: w.start, duration: 7 })),
  ].sort((a, b) => a.startDate.localeCompare(b.startDate))
  await github.graphql(
    `mutation($f: ID!, $c: ProjectV2IterationFieldConfigurationInput!) {
      updateProjectV2Field(input: { fieldId: $f, iterationConfiguration: $c }) { clientMutationId } }`,
    { f: field.id, c: { startDate: iterations[0].startDate, duration: 7, iterations } },
  )
  core.info(`주기를 더했습니다: ${missing.map((w) => w.title).join(', ')}`)
}

module.exports = { weekOf, weekAfter, attachThisWeek, ensureMilestones, ensureIterations, PROJECT_NUMBER }
