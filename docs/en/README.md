# enoTone — Documentation (English)

This directory holds the **English documentation set**. The Russian set is the canonical source: it lives next to this directory (`docs/*.md`) and is always more complete.

## Why two sets

The project is developed by a Russian-speaking owner, so process documents, ADRs and decision records are written in Russian. English is provided for the parts an outside reader needs: what the app is, how it is built, how it is tested, and how the project is run.

**Rule:** when the two sets disagree, the Russian set wins. English files carry a header stating the revision they were translated from.

## Translated documents

| Document | What it covers |
|---|---|
| [ARCHITECTURE](ARCHITECTURE.md) | Module structure, responsibilities, data flow |
| [MODULE_MAP](MODULE_MAP.md) | Where each concern lives in the codebase |
| [TESTING](TESTING.md) | Build checks, test layers, acceptance model |
| [WORKFLOW](WORKFLOW.md) | GitHub process: issues, labels, branches, review, merge |
| [QUEUE](QUEUE.md) | Current order of work — what to do next |
| [STATUS](STATUS.md) | Current project state |
| [KNOWN_ISSUES](KNOWN_ISSUES.md) | Known defects and accepted limitations |

## Not translated, available in Russian

| Document | Why |
|---|---|
| `docs/DECISIONS/ADR-*.md` (104 files) | Decision records are historical and immutable; they are superseded by new ADRs, never edited. Translation would risk drift. |
| `docs/SCENARIOS.md` | 136 user scenarios with per-item acceptance evidence — internal tracking. |
| `docs/REQUIREMENTS.md` | Requirement matrix with proof links per row — internal tracking. |
| `docs/DEVLOG.md` | Development journal: session-level context for whoever continues the work. |
| `docs/archive/*` | Historical snapshots. |
| `docs/AUDIT_PROTOCOL.md`, `docs/REVIEW_REPORT_TEMPLATE.md` | Internal review process, used by the owner and agents. |
| `docs/ORIGINAL_PROMPT_AllMusic_Player.md`, `docs/USER_SPEC_RU.md` | Original owner specifications — quoted sources, not maintained documents. |

## Keeping the sets in sync

English files are translated from a specific revision, recorded in their header. They are not auto-generated: a translation that silently drifts from the Russian source is worse than no translation, because it looks authoritative.

When a Russian document changes materially, the English counterpart is updated in the same PR or marked as stale in its header.

## The app itself

The **interface is fully translated** into Russian and English: `app/src/main/res/values/strings.xml` (Russian, default) and `app/src/main/res/values-en/strings.xml` (English), 272 strings and 14 plurals each, full parity. The app follows the system language.
