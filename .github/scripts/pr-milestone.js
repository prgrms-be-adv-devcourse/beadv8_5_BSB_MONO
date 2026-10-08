// 머지된 PR에 GitHub 마일스톤을 붙인다 (docs/LINEAR.md "GitHub 활용").
// 머지 날짜(한국 시간) 이후로 마감이 가장 가까운 열린 마일스톤을 고른다.

// 'YYYY-MM-DD' (한국 시간)
function kstDate(iso) {
  return new Date(new Date(iso).getTime() + 9 * 60 * 60 * 1000).toISOString().slice(0, 10)
}

// GitHub는 마감일을 날짜만 저장한다(due_on이 그 날짜의 00:00Z). 그래서 시간대를 바꾸지 않고 날짜만 자른다.
function pickMilestone(milestones, mergedAt) {
  const merged = kstDate(mergedAt)
  return (
    milestones
      .filter((m) => m.due_on && m.due_on.slice(0, 10) >= merged)
      .sort((a, b) => a.due_on.localeCompare(b.due_on))[0] ?? null
  )
}

module.exports = async ({ github, context, core }) => {
  const pr = context.payload.pull_request
  if (pr.milestone) {
    core.info(`이미 마일스톤이 있습니다: ${pr.milestone.title}`)
    return
  }
  const { data: milestones } = await github.rest.issues.listMilestones({
    ...context.repo,
    state: 'open',
    per_page: 100,
  })
  const target = pickMilestone(milestones, pr.merged_at)
  if (!target) {
    core.notice('머지 날짜 이후에 마감되는 열린 마일스톤이 없어 건너뜁니다.')
    return
  }
  // PR의 마일스톤은 issues API로 바꾼다
  await github.rest.issues.update({
    ...context.repo,
    issue_number: pr.number,
    milestone: target.number,
  })
  core.info(`마일스톤 '${target.title}'을 붙였습니다.`)
}

module.exports.pickMilestone = pickMilestone
