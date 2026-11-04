# Quiet Below final defaults and remaining code

3 November 2026. Branch `feat/quiet-below-final-defaults`, based on the accepted dev66 work and export-wording correction. Source: organizer-supplied `docs/A Quite Below.docx` (SHA256 `7e15` prefix; full original digest recorded in `../reports/quiet-below-final-document.json`). Preserve the original document bytes. The organizer requests remaining code first, then final default content.

## Ordered work

1. Core automatic Event-history maintenance: retain the existing guarded archive operation and immutable evidence formats. Default automatic scheduling waits for 60 consecutive seconds of non-active play with zero online players, then attempts at most once per five minutes. Pending operations/holds always prevent execution. Config is bounded, strict and optional; failure disables automation with an actionable error while preserving manual cleanup. No background thread may inspect or mutate server authority.
2. Bundled audio: audit all eight supplied Freesound sources, acquire publicly available previews or supplied originals without account access, convert locally to Minecraft-compatible Ogg Vorbis if necessary, retain attribution and hashes, register stable IDs on both sides and validate packaged files. Core supplies reusable asset validation/catalog contracts; Quiet Below owns the clips, labels and scenario IDs. No runtime streaming from Freesound/SoundCloud, external URLs in Event sound fields, or runtime JAR rewriting.
3. Extend only the Event contracts needed by the final content: first-session/relative timers, source actor audience and per-player first offering, first powered stations/first Warden, repeated 5,000-block growth milestones. Retain claim-before-effects, source journals, exact retry, disabled/unbound portable templates and pause semantics. Design and review any additional controls before UI implementation; do not approximate unsupported semantics with misleading one-shot defaults.
4. Package the supplied six roles, nine books, nine Event templates and four loot tables as the scenario starting point. Keep map coordinates/placement notes separate from reusable content. Preserve the organizer's prose and meanings; ask before resolving conflicts with reviewed mechanics. Existing initialized worlds/configurations win; new defaults must not reset the live review or custom profile.

## Organizer decisions

- Replace Journal II's timber reference with planks, per the organizer. Do not infer a blanket protection rule for all wood/buildings from prose.
- Station proximity interference is a lore claim; stations remain independent. Forge/Altar must not be collectible as placeable items in Survival.
- Use editable independent percentages for Ancient City loot, rather than exact vanilla rolls. Derive a documented approximation from Minecraft 1.21.10's actual Ancient City table, excluding both book entries whose loot functions enchant them.

Unspecified authors, ordinary loot quantities/chances and first-placement bookkeeping use documented conservative defaults. Location instructions remain organizer-facing map setup, not authored growth routes.

## Verification and delivery

Use pure policy/config/archive tests, focused Core/campaign tests, strict content codec round trips and asset/hash/license/build checks. Serialize Gradle/native clients sharing class directories. Native checks must cover automatic cleanup eligibility/idempotency, actual custom-sound client decoding/private preview, Event audience/repetition/restart semantics, fresh final defaults and existing custom-profile emptiness. Audible volume/feel requires organizer review. Independent code review precedes final acceptance.

Use scoped commits and push the branch; no main merge or Modrinth publication. A saving stop and verified backup precede any review deployment. Preserve existing review worlds, config, discoveries and receipts; prepare a fresh final-defaults review world rather than overwriting the organizer's accepted setup. Record exact checks, failures, hashes and outstanding gates in the handoff.
