---
name: problem-definition
description: Investigate a user-provided topic or question, separate symptoms from structural causes, identify affected people and the reality-goal gap, classify surface responses as R-codes, and define a kingpin problem as a KP-code. Use when the user wants to frame a problem before planning a product or solution, stress-test an existing problem statement, or explore solution principles from other industries after the problem is defined.
---

# Problem Definition

## Purpose

Turn a vague topic into a concise problem brief that a user can review and correct. Investigate the situation instead of making the user fill out a nine-question form. Treat the kingpin as a grounded hypothesis until the user confirms it.

Use Korean unless the user requests another language.

## Core concepts

### P-code: Problem

Use P-codes for observed symptoms, goals, constraints, and cause candidates. Use KP-codes for kingpin problem candidates.

```text
P1: observed symptom
P2: user or business goal
P3: structural constraint
P4: cause candidate
KP-1: kingpin problem candidate
```

The kingpin is the smallest structural problem that explains multiple symptoms and can be acted on or tested. It is a hypothesis, not a dramatic-sounding root cause.

For user-facing labels, write the first candidate as `KP-1 ⋅ 킹핀 후보 (핵심 문제)`. For additional candidates, use `KP-2 ⋅ 킹핀 후보`, `KP-3 ⋅ 킹핀 후보`, and so on.

### R-code: Response

Use R-codes for surface responses, workarounds, mitigations, or common proposals that address symptoms without directly changing the kingpin.

```text
R1: surface response
R2: workaround
R3: temporary mitigation
```

R-codes are useful diagnostic evidence. Explain what each response reduces, what it leaves intact, and whether it merely shifts effort or cost. Do not call an R-code an S-code.

### S-code: Solution

Use S-codes only for solutions that directly address the kingpin problem.

```text
S-원리: underlying change in conditions
S-구조: mechanism or system pattern
S1: concrete solution
```

An S-code must change the relevant structure, reduce multiple symptoms where appropriate, avoid simply moving burden to another person or step, and be observable or testable.

## Workflow

### 1. Receive a topic

Accept a question, situation, product idea, complaint, interview excerpt, or rough problem statement. Do not require the user to supply the nine investigation fields.

### 2. Investigate the problem

Infer and organize, as evidence permits:

- what is happening
- who is affected
- current reality
- desired goal state
- the gap between them
- current responses
- what those responses solve and leave behind
- candidate central problems
- evidence versus hypothesis

Use supplied context first. Read relevant local material when the user points to it. Use external research only when the user asks for it or when reference discovery is requested; label source-backed facts and inferences separately.

### 3. Ask only high-leverage questions

Ask a question only when its answer could materially change the kingpin, affected user, scope, or recommendation. Ask one at a time when possible, and explain why it matters.

If the topic is sufficiently clear, present a tentative brief instead of interrogating the user. Let the user correct the interpretation.

### 4. Build P- and R-codes

Group repetitive symptoms under candidate causes. Select a tentative `KP-1` using these tests:

- explains multiple symptoms
- accounts for the reality-goal gap
- is more structural than a visible symptom
- can be influenced or tested within scope
- does not merely rename the desired solution

Record common surface responses as R-codes. Explain their useful effect and their remaining gap.

### 5. Brief the user

Return a compressed, readable brief before exposing the full map. Use plain language, remove repetition, and distinguish confirmed facts from inferences.

Default order:

1. Core conclusion
2. Current situation
3. Affected user
4. Reality-goal gap
5. Current responses and remaining gap
6. Kingpin candidate
7. Evidence status
8. One or two high-leverage questions

Provide the detailed P/R map only when useful or requested.

### 6. Explore S-codes only when appropriate

If the user asks for solutions, or the brief has a confirmed enough kingpin, derive an S-code through:

```text
KP-1
→ structural condition to change
→ S-원리
→ S-구조
→ S-code candidates
```

Do not generate S-codes from symptoms alone. If a candidate only treats a symptom, classify it as an R-code.

## Cross-category reference discovery

Support both user-provided references and agent-discovered references.

### User-provided references

Analyze supplied brands, influencers, media, products, services, or operating models. Extract the mechanism rather than copying content or surface style.

### Discovered references

After defining the kingpin, search other industries or categories when useful. Consider adjacent and distant examples: retail, games, education, logistics, healthcare, media, memberships, queues, scheduling, pricing, or physical operations.

For every reference, record:

```text
reference
industry/category
observed structure
operating principle
connection to KP-1
what to transform or discard
candidate S-원리 / S-구조
application risks
```

Do not return a list of famous examples without explaining the transferable mechanism. Do not present literal copying as innovation; describe it as principle extraction and structural transfer.

## Output format

### Default problem brief

```markdown
# 문제정의 브리핑

## 입력된 주제

## 핵심 결론

## 현재 상황

## 영향을 받는 사용자

## 현실과 목표의 괴리

## 현재 대응과 남은 문제

## 킹핀 후보

## 판단 상태

## 확인이 필요한 질문
```

### Expanded analysis

Add these sections only when they help:

```markdown
## P코드

## R코드

## 킹핀 선정 근거

## 문제 정의문

## 문제 범위와 제외 범위

## 검증해야 할 가정

## 외부 레퍼런스

## 솔루션 후보

## 해결안별 위험과 제약

## 다음 단계
```

Use this sentence pattern for the final problem definition when applicable:

> `[대상 사용자]`는 `[특정 상황]`에서 `[목표]`를 달성하려 하지만, `[구조적 장애]` 때문에 `[반복되는 손실/현상]`을 겪는다.

## Quality gate

Before finalizing, verify:

- The user can understand the conclusion without reading the raw investigation.
- Symptoms, constraints, causes, R-codes, and S-codes are distinct.
- The kingpin explains multiple observations without overstretching scope.
- Facts, user statements, inferences, and unverified hypotheses are labeled.
- R-codes are not mislabeled as S-codes.
- S-codes, if included, directly address the kingpin.
- The brief names what remains unknown.
- The output does not force every problem into a product or feature.

## Calibration example: book-to-action service

Given a rough topic such as “book recommendation services are common, but few help readers act on what they read,” produce a brief that converges toward:

```text
KP-1 ⋅ 킹핀 후보 (핵심 문제): The reader lacks a persistent process that connects a book's general principles to a concrete personal problem, runs a small action experiment, and adjusts the next action from the result.

R1: book summaries
R2: one-off ChatGPT or Claude prompts
R3: notes, reminders, or habit trackers

S-원리: convert reading into a closed-loop action experiment
S-구조: problem definition → principle mapping → small experiment → result log → next-action adjustment
S1: a reading-based problem-solving coach
```

For an MVP, narrow the target to employees applying self-development books to work or career problems. Keep fiction, poetry, essays, and other genres as later variants with reflection, creation, discussion, or perspective outputs rather than forcing every book into a productivity plan.
