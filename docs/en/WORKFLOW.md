# Workflow — coordinating enoTone development through GitHub

> **Translated from:** `docs/WORKFLOW.md` at `main` `92348b9f`, 2026-11-23. Russian is canonical.

## 1. Sources of truth

| Question | Where the answer lives |
|---|---|
| Order of work — what to do next | **[QUEUE](QUEUE.md)** — the single source; overrides any other list |
| Stage sequence | `docs/ROADMAP.md`, `docs/DESIGN_PLAN.md` (Russian) |
| A concrete task, its scope and dependencies | **The body of one GitHub Issue**, by stable ID |
| Live state and priority | **GitHub Issue labels**: `state:*` and `P0`…`P3` |
| Verifiable result | Linked PR, exact HEAD, CI, independent review |
| Acceptance | Automated checks only ([ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md)) |
| Fast entry into the code | `docs/MODULE_MAP.md` and explicit links in the Issue |
| Project snapshot | `docs/STATUS.md` (not a per-task status registry) |

Chat history, comments, STATUS and ROADMAP do not substitute for the Issue card. If sources contradict each other, the agent reports the conflict and does not widen the task by guessing. The full task and approved additions live **in the Issue body** or in `docs/tasks/<ID>.md` with an exact link from the Issue.

## 2. Roles

- **Owner.** Approves direction, order, priorities, designs, significant ADRs, scope changes, and gives the command to merge. Acceptance does not require a separate confirmation: it follows from green CI and passed review.
- **Dispatcher.** Read-only selection of the next available Issue. Does not start, assign or change anything without the owner's command. Does not run a general source audit when answering "what next?".
- **Executor.** One pinned Issue, canonical branch, draft PR, self-correcting its own CI errors within scope.
- **Reviewer.** Independent analysis of Issue, diff, exact HEAD, tests and regressions. Publishes findings as a PR comment or review when explicitly assigned. Does not commit into someone else's branch without separate instruction. Does not override approved decisions: describes the deviation and hands it to the owner.
- **Architect.** Proposes ADRs, task composition and technical boundaries. Does not change priorities approved by the owner.

## 3. Labels and states

For **open** tasks at most one state label is allowed: `state:ready`, `state:in-progress`, `state:review` or `state:blocked`. No state label means "not ready" (backlog). Completion is determined by a closed Issue with reason completed and a link to the result; a separate `state:done` is not needed. Remove the current state label when closing.

Priority is exactly one label: `P0`, `P1`, `P2`, `P3`. Assigned and changed by the owner. State/Priority fields in the Issue body are **not** maintained; PR, STATUS and ROADMAP do not duplicate operational status.

`state:ready` is allowed only if the owner has approved the stage, criteria are formulated, all mandatory information is available in GitHub, all `Depends on` are complete and there is no open PR for the same work. There is no `state:device-test` label: acceptance is by automated checks, so after merge the Issue is closed and a defect found on device becomes a separate Issue.

## 4. Dispatcher: "what is the next task?"

Read-only. Answers from [QUEUE](QUEUE.md), open Issues, their labels and dependencies. Reports the next available Issue with a link, its priority and what blocks it if blocked. Does not read chat history to reconstruct decisions and does not audit sources.

## 5. Branch and capture

The branch is the canonical name from the Issue. Capture is atomic: the branch is created through `POST /git/refs` against the current `main` commit, so two executors cannot silently take the same work. If the ref already exists, the capture conflicts — stop and report to the owner.

Order: read the Issue → create the branch through the API → record `Executor: <model/session>` in the Issue → comment with the capture fact → switch `state:ready` to `state:in-progress`. If the capture conflicts, stop and report even if the branch was already created; never delete someone else's branch.

## 6. Merge

- Merge only on an **explicit owner command for a specific PR**, with green CI on the **exact HEAD** and completed review.
- Method: **squash**.
- **Branches are deleted after merge.** Squash creates a new commit, so the branch's own history is lost anyway. Order: merge → tag `archive/<branch-name>` on the tip → delete the remote branch. The tag preserves the objects; restore with `git checkout -b <name> archive/<name>`.
- A **branch without a merged PR is not deleted** without review: first check whether it contains anything not in `main` (`tools/branch_report.py`), then the owner decides. Rejected research is deleted with a `CHANGELOG.md` entry.
- Mass branch cleanup is a separate task with a report: how many deleted per category, which tags created, what remains in question.

## 7. Pull requests

- Conventional Commits, small PRs, `main` is never changed directly.
- Every PR lists the affected [SCENARIOS](../SCENARIOS.md) IDs and updates their "last result" to "accepted by automated checks" with the PR and CI run after merge.
- Every PR updates the [REQUIREMENTS](../REQUIREMENTS.md) rows it touches: status and proof. A requirement cannot be silently skipped, deleted or considered met without evidence. Cancelling or changing a requirement is an owner decision (ADR or message date); the row is marked ⛔.
- Changing a check inside an existing test within a fix PR requires justification in the PR description.

## 8. Documentation discipline

- `docs/STATUS.md`, `docs/KNOWN_ISSUES.md`, `docs/TESTING.md` describe **current** state only: replace outdated content, do not append below. STATUS is at most two pages.
- Chronology goes to `CHANGELOG.md` (one entry per PR); detailed journals, when needed, to `docs/archive/`.
- At the end of a PR update: STATUS, CHANGELOG, KNOWN_ISSUES (remove fixed items), ADR index, and when relevant CURRENT_DECISIONS, ROADMAP, OPEN_QUESTIONS.
- Documents, reports and commits are in Russian; code identifiers are English. Comments explain "why".
- Only depersonalised numbers and error codes go into Git — no screenshots with track names.

## 9. Architectural decisions

Every architectural decision is an ADR following the template: context; **at least two options** with pros and cons; decision; rationale; consequences; how to verify. An accepted ADR is replaced only by a new ADR, and the index `docs/DECISIONS/README.md` must be updated (status of both old and new).

## 10. Complexity thresholds

A new module, an external runtime dependency or a user-facing setting requires an ADR with an owner decision. The detekt baseline may not grow. These thresholds exist because each of them adds permanent maintenance cost.

## See also

- [QUEUE](QUEUE.md) — what to do next
- [TESTING](TESTING.md) — how work is verified
- Russian canonical: [`docs/WORKFLOW.md`](../WORKFLOW.md)
