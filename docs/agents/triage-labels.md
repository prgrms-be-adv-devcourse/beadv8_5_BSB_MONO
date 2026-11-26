# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                                                  |
| -------------------------- | -------------------- | ------------------------------------------------------------------------ |
| `needs-triage`             | `검토 대기`          | Maintainer needs to evaluate this issue                                  |
| `needs-info`               | `정보 필요`          | Waiting on reporter for more information                                 |
| `ready-for-agent`          | `팩토리 구현`        | Fully specified, ready for an AFK agent                                  |
| `ready-for-human`          | `구현 대기`          | Fully specified, but a human opens an AI session to implement it         |
| `wontfix`                  | `취소`               | Will not be actioned                                                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

An issue carries one kind label (`버그` or `기능`) and at most one of these state labels.
