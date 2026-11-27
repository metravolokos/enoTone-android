# Work queue — the single order of work

> **Translated from:** `docs/QUEUE.md` at `main` `ffd7ab29`, 2026-11-27. Russian is canonical.
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
| **P3** | [#299](https://github.com/metravolokos/enoTone-android/issues/299) | P-3 — user sorting and filters: needs an ADR |
| **P3** | [#302](https://github.com/metravolokos/enoTone-android/issues/302) | Q3 — queue up to 10 000 and Shuffle with selected/current track first; **in progress** |

**Why first.** Both follow from the owner decisions of 2026-11-27. `#273` and `#274` are closed: the foreground service is accepted as normal and `OPEN_NOW_PLAYING` gained `singleTop` ([CURRENT_DECISIONS](CURRENT_DECISIONS.md)). `#299` needs an ADR before any work. `#300` was closed as replaced: instead of explaining the 100-track limit, the owner chose the new queue contract in #302.

## Wave 2 — product audit

Defects in user journeys: dead ends, dead buttons, wrong expectations.

**Every defect in this wave is closed:** P-4 (#263) — PR #283, P-5 (#264) — PR #284, P-6 (#265) — PR #285 and #287. `#266` remains open as the deferred remainder.

| Priority | Issue | What |
|---|---|---|
| **P3** | [#266](https://github.com/metravolokos/enoTone-android/issues/266) | Audit remainder: playlists (P-4), offline (P-5), global search (P-7), diagnostics export (P-9) — **deferred by the owner on 2026-11-27** |

**Why no work here.** The owner triaged the list on 2026-11-27: P-3 was taken into [#299](https://github.com/metravolokos/enoTone-android/issues/299), P-10 was replaced by the new queue contract [#302](https://github.com/metravolokos/enoTone-android/issues/302), the other four are deferred. Deferred is not cancelled — each needs an ADR or design, so returning to one starts with a new owner decision, not with planning.

## Wave 3 — code audit

Technical defects: races, atomicity, cleanup ordering.

| Priority | Issue | What |
|---|---|---|
| **P3** | [#296](https://github.com/metravolokos/enoTone-android/issues/296) | Eager artist-detail plans — needs a measurement |

**Closed in this wave:** #254 — PR #291; #255 — PR #294; #257 (premature `DONE` + library revision) — PR #295; #258 (bind reconnect, double reload, docs) — PR #297; #292 (legacy artwork copy) — PR #298; #256 (fail-closed sign-out, legacy artwork, risk class R) — PR #288 and #289.

**Why last.** These are invisible defects: the user does not notice until they hit data loss. `#254`–`#258` and #292 are closed. #296 remains — it awaits an FPS measurement. #266 was triaged by the owner: P-3 (#299) and the new queue contract (#302) were taken, the rest deferred.

**Note:** backoff only covers the bind-timeout branch. `onServiceDisconnected` and `onBindingDied` still reconnect immediately, so the overlap between `#258` and `#273` remains partial: whether the FGS binding is normal stays a separate owner decision.

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
