# LC07A: banquet foundation and design review

The owner authorized the next implementation milestone on 11 November. LC07 is independent of the deferred treaty and complication group reviews. New banquet UI and effect policies require review before live item/effect handlers are installed. This first slice implements the policy-independent model and strict private persistence, prepares the UI proposal, and removes the source editor's idle `Field page` hint while retaining real feedback.

## Scope and boundaries

Last Council owns the optional banquet mechanic. Reuse Core's bounded atomic file store and existing editor presentation. Do not introduce a generic Core poisoning framework, add a thirst system, change political eligibility, or connect Quiet Below's stations to this mechanic. Keep the source brief, existing casts, played worlds and unrelated acoustics work unchanged.

The foundation represents individually identified drinks, poison doses and antidotes. A private ledger records issuance and consumption so that an exact repeated operation is idempotent and a changed operation cannot reuse its identifier. Contamination records stay server-side. No contamination marker is proposed for client-visible item data; ordinary and contaminated drinks must have identical normal appearance and tooltips.

Immutable illness state tracks remaining onset and duration in supplied active ticks. The caller explicitly identifies whose clocks may advance. This foundation chooses no offline/death/session policy, applies no Minecraft effects and makes no claim of inventory atomicity. The live adapter must check actor/participation, possession, session state and item-use completion, and coordinate player inventory saves with ledger changes through a recoverable journal before these transitions can be used for real items. A plain ledger save cannot establish those guarantees.

The strict schema-1 store retains bounded tokens and operation receipts without evicting replay protection. It uses canonical UUIDs, exact field sets/types, strict UTF-8/duplicate-key parsing, observed-byte conflict detection and atomic publication. Invalid or externally changed data must remain untouched. New-state publication in memory follows a successful save, never precedes it. Live progress must not enter reusable scenario exports.

## Design and policy review

Dedicated UI designer proposes one **Game setup -> Banquet** entry, a focused settings popup and an explicit online-recipient supply confirmation. Private moderation is a separate assigned-Chancellor-only view. Settings use the existing OP/Editing/non-active guards. See [the design note](LC07_UI_DESIGN.md) and [interactive mockup](../../design/last-council-banquet.html).

Pending owner choices: illness symptoms/timing, death/offline clocks, antidote/stacking. Proposed initial values are not approved defaults. Supply quantities shown by the mockup are illustrative; physical supplies will be finite inventory items rather than automatic stock. Native controls, item registration/use, inventory transaction recovery, settings/profile export and symptoms are the next slice after review.

## Verification and acceptance

Exercise exact token consumption, replay/collision rejection, CAS revisions, immutable maps, bounded capacity, delayed onset, large tick jumps, expiration, caller-selected clocks and cure transitions. Strict persistence tests cover fresh/restart round trips, malformed fields/types/UTF-8, duplicate JSON keys, cross-record consistency, external edits and failed publication. Run the complete Last Council unit suite and client compilation; inspect the actual diff. Check browser workflow and normal/compact mockup geometry before requesting owner review. No new multiplayer acceptance, native banquet gameplay or inventory crash-safety is claimed by this slice.
