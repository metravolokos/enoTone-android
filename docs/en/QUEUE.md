# Work queue — the single order of work

> **Translated from:** `docs/QUEUE.md` at `main` `8e170001`, 2026-11-26. Russian is canonical.
> **Single source of ordering.** If an Issue, ROADMAP or chat contradicts this file, this file wins.

## How to read it

Work proceeds in **waves**. Within a wave tasks are independent by file and can run in parallel; between waves the order is strict.

| Label | Meaning |
|---|---|
| **P0** | Blocks the user: a visible defect, cannot work |
| **P1** | Must be resolved before the next stage |
| **P2** | Important but not blocking |
| **P3** | Can be deferred without product impact |
| **device** | Found by on-device diagnostics, needs an owner decision |
| **R** | Risk class R: two independent reviews, fault-injection tests |

## Wave 1 — on-device diagnostics findings

First, because these are what the user sees and trips over.

| Priority | Issue | What | Status |
|---|---|---|---|
| **P2** | [#273](https://github.com/metravolokos/enoTone-android/issues/273) | `device` — playback service never releases its binding: FGS norm or tech debt? | awaiting owner decision |
| **P3** | [#274](https://github.com/metravolokos/enoTone-android/issues/274) | `device` — `OPEN_NOW_PLAYING` contract requires flags | awaiting owner decision |

**Why first.** These came from a real device and are directly visible. `#273` and `#274` are questions, not work: they wait for the owner and do not block anything.

## Wave 2 — product audit

Defects in user journeys: dead ends, dead buttons, wrong expectations.

**Every defect in this wave is closed:** PA-4 (#263) — PR #283, PA-5 (#264) — PR #284, PA-6 (#265) — PR #285 and #287. Only the question `#266` remains open in this wave.

| Priority | Issue | What |
|---|---|---|
| **P3** | [#266](https://github.com/metravolokos/enoTone-android/issues/266) | Sorting, playlists, offline, search, diagnostics — **needs owner decision** |

**Why no work here.** These are scope questions, not defects: until the owner decides what belongs in the product, there is nothing to plan.

## Wave 3 — code audit

Technical defects: races, atomicity, cleanup ordering.

| Priority | Issue | What |
|---|---|---|
| **P3** | [#257](https://github.com/metravolokos/enoTone-android/issues/257) | Premature `DONE` + library revision |
| **P3** | [#258](https://github.com/metravolokos/enoTone-android/issues/258) | Improvements: bind reconnect, double reload, docs |
| **P3** | [#292](https://github.com/metravolokos/enoTone-android/issues/292) | Legacy copy resurrects an invalidated cover |

**Closed in this wave:** #254 (queue restore budget) — PR #291; #255 (prefetch cancellation race) — PR #294; #256 (fail-closed sign-out, legacy artwork, risk class R) — PR #288 and #289.

**Why last.** These are invisible defects: the user does not notice until they hit data loss. `#254`, #255 and #256 are closed; the next by number is `#257`.

**Note:** `#258` partially overlaps `#273`. If the owner decides the service not releasing its binding is normal, part of `#258` (bind reconnect) becomes protection against a theoretical scenario.

## Ordering rules

1. **A wave does not start until the previous one is closed** — except tasks awaiting an owner decision: those never block.
2. **Within a wave**, order by `P` label, then by Issue number (older first).
3. **Risk class R** (label on the Issue) requires two independent reviews and tests that simulate process death.
4. **A task without a priority label** counts as `P3` until first triage.
5. **A new finding** gets its wave by nature: visible on device → 1, UX defect → 2, technical → 3. If it does not fit — ask the owner, do not guess.

## When a task is closed

Per [ADR-0104](https://github.com/metravolokos/enoTone-android/blob/main/docs/DECISIONS/ADR-0104-auto-acceptance.md):
- PR merged into `main`;
- CI on the exact merged HEAD is green;
- independent review passed;
- Issue closed with a link to the PR and the CI run.

## Where things live

| Question | Document |
|---|---|
| What to do now | **this file** |
| Project state | [STATUS](STATUS.md) |
| Known defects | [KNOWN_ISSUES](KNOWN_ISSUES.md) |
| Development history and lessons | [DEVLOG](../DEVLOG.md) (Russian) |
| Stages | `docs/ROADMAP.md` (Russian) |
| Process | [WORKFLOW](WORKFLOW.md) |
