# DeltaCalc 3.2 Trust and Fit

Task ID: `DELTACALC-3.2-TRUST-FIT`

Status: `IN PROGRESS` - release candidate built; visual runtime matrix blocked

## Objective

Ship a release-candidate update after the current Modrinth 3.1.1 release that
makes DeltaCalc materially safer and clearer across Minecraft window sizes and
GUI scales, improves the calculator's interaction and visual hierarchy, and
adds regression coverage for the layout and state bugs found in the audit.

This is a focused stabilization and user-experience release. It is not a
rewrite and does not add new battle mechanics.

## Definition of done

- Existing main-checkout edits and historical Claude worktrees remain intact.
- Persisted HUD geometry is migrated or reconciled whenever the scaled viewport
  changes; no panel can become permanently unreachable.
- Drag, resize, tooltip, and popup bounds never construct invalid clamp ranges,
  including when a saved widget is larger than the viewport.
- The calculator reflows or truncates measured content without overlap at the
  supported scaled-viewport matrix and font-scale limits.
- Calculator state communicates revealed, inferred, manual, unsupported,
  waiting, and warning states instead of presenting every number as equally
  authoritative.
- Override navigation, reset, collapse, drag, resize, and layout-reset actions
  have visible and accurate affordances; documentation matches behavior.
- Snapshot invalidation covers every field that can change a calculation.
- Unknown form resolution cannot abort the HUD render path and produces bounded,
  actionable diagnostics.
- Pure regression tests cover geometry reconciliation, tiny-screen drag/resize,
  responsive calculator layout, configuration migration, and snapshot
  invalidation; Gradle no longer reports `test NO-SOURCE`.
- A clean build produces a release-candidate jar. No GitHub or Modrinth release
  is published without a separate explicit instruction.
- Runtime evidence records the tested Minecraft window sizes, GUI scales,
  screenshots, and any unresolved compatibility limitation.

## Scope boundaries

- Preserve damage formulas unless a failing characterization test proves a
  regression inside this milestone's touched state/invalidation path.
- Do not clean, delete, reset, or reuse the old Claude worktrees.
- Do not refactor unrelated data-generation tools or the separate
  `CobblemonExtendedBattleUI` and `Delta team building` repositories.
- Do not add a new UI framework. Keep the Fabric/Minecraft immediate-mode
  renderer and extract only the pure layout/state pieces needed for safety and
  testing.
- Treat the user's stated Modrinth 3.1.1 as the release baseline. Reconcile the
  repository's older internal `mod_version` only after identifying the least
  surprising next-version mapping.

## Dependency graph and execution status

1. `DONE` - Preserve state and create isolated worktree
   - Worktree: `D:\Projects\Cb delta\DeltaCalc-worktrees\3.2-trust-fit`
   - Branch: `codex/3.2-trust-fit`
   - Baseline: `0b1131e`

2. `DONE` - Baseline and implementation contract
   - Confirm clean worktree, build inputs, current public/repository version
     relationship, and Antigravity model availability.
   - Record fresh pre-change test/build output.
   - Prompt Antigravity Gemini 3.8 Flash High with exact file ownership,
     verification requirements, and no-release boundary.
   - Depends on step 1.

3. `DONE` - Responsive geometry foundation and regression tests
   - Add pure viewport/rectangle reconciliation and safe range helpers (`DONE`).
   - Add tests before integrating them into live panels (`DONE`).
   - Integrate calculator bounds safety into drag/resize/render path (`DONE`).
   - Integrate information panel and battle log bounds safety and layout-reset support (`DONE`).
   - Tooltips/popups and team panel integration completed with safe runtime width resolution, intent-preserving fallback placement, and bounds-aware drag math (`DONE`).
   - Depends on step 2.

4. `DONE` - Calculator trust and interaction pass
   - Gate 4A (`DONE`): Calculator trust/layout foundations (Sol review corrected):
     - Introduced minimal structured `CalcMoveOutcome` enum without speculative aliases.
     - Hardened `outcomeFor` using `Long` arithmetic to prevent integer overflow across arbitrary `Int` inputs.
     - Fixed status move handling in `DamageEngine` to assign `STATUS` outcome with `koLabel = "Status"` and `supported = true`, eliminating false unsupported classification.
     - Required explicit `outcome` on `DamageEstimate` and `CalcMoveRow`; made `isStatus` a derived read-only body property.
     - Built user-facing `warningTexts` on `CalcRenderModel`, distinct from debug prose, de-duplicated in stable order.
     - Simplified pure `CalcMoveRowLayout` to return offsets relative to content origin (no `startX`, no pretend move-name input) guaranteeing `0 <= every x/right <= availableWidth`.
     - Added focused regression tests in `CalcMoveRowLayoutTest` and `CalcTrustFoundationTest` including extreme `Int.MIN_VALUE`/`Int.MAX_VALUE` edge cases.
   - Gate 4B (`DONE`): Visual renderer pass (`DamageCalcPanel` consumption, safe truncation/reflow, interaction affordances) (Sol review corrected):
     - Replaced fixed `x+84`/`x+144` columns with `CalcMoveRowLayout`, deriving tiers from content width and eliminating whole-screen-size tiering.
     - Replaced substring parsing of `koText` with structured `CalcMoveOutcome` mapping (`CalcPresentation`).
     - Distinct solid red for `GUARANTEED_OHKO`, amber label + alternating hatched risk fill for `LIKELY_OHKO`, and suppressed bars for `STATUS` and `UNSUPPORTED`.
     - Added 1px left confidence rail for MEDIUM/LOW and error-like unsupported state.
     - Header allocator clamped in `[0, availableWidth]` preventing overlaps down to sub-minimal widths (16-24px), prioritizing warning chip over turn/title, and safely truncating chip text.
     - Defined explicit `CalcContentViability` threshold for header (16x18) and content (120x60), rendering safe header-only presentation when cramped without mutating persisted state.
     - Viewport reset: cleared all scroll, content height, viewport bounds, and hit regions when `viewportHeight <= 0` or sub-minimal, preventing `onScroll` from consuming stale geometry.
     - Measured `CalcCompactMatchupLayout` dropping words before percentages/arrow, with all fixed summary lines safely truncated to safe cell width.
     - Unified pill padding/total width helper across layout allocation and drawing; amplified body pressed feedback for `ArmedOverride(NEXT)`.
     - Cleaned localization keys into single `deltacalc` namespace, simplified `tr()` without redundant format wrappers, adopted visible reset icon (`↺`), and removed redundant `*` suffix on manual override values.
     - Hardened `CalcHitBounds` with overflow-safe 64-bit Long arithmetic.
     - Replaced debug `[+]`/`[-]` with chevrons (`▾`/`▸`), and made resize handle hover state visible with active accent color.
     - Added explicit override controls (previous, next/body, reset) with separate half-open hit bounds, eliminating the phantom spread target.
     - Fixed viewport bottom clamp, guarded non-positive scissor dimensions, and activated scrollbar thumb hover color.
     - Updated `README.md` controls documentation.
     - Verified with pure focused tests in `CalcPresentationTest` (including 16-65px header tests, boundary overflow tests, viability tests, matchup tests) and extended matrix tests in `CalcMoveRowLayoutTest`.
   - Depends on step 3.

5. `DONE` - High-confidence reliability fixes
   - Complete snapshot fingerprint invalidation:
     - Implemented deterministic length-prefixed encoding (`CalcSnapshotEncoder`) for top-level `CalcBattleSnapshot.fingerprint` and `CalcPokemonSnapshot.fingerprintPart`, eliminating delimiter-collision vulnerabilities on separators or nested content.
     - Added `teraType`, `baseStats` (6 length-prefixed stats), `actualStats` (6 length-prefixed stats), `canEvolve`, and `weightKg` (deterministic `toBits()`) to `CalcPokemonSnapshot.fingerprintPart`.
     - Added deterministic `compatNotes` list order to `CalcBattleSnapshot.fingerprint`.
     - Added `CALC_INPUT_CHANGE` generic invalidation reason in `CalcInvalidationCoordinator.update` using a fresh local transition set to ensure history-independence across unconsumed updates.
   - Guard form resolution with safe fallback and rate-limited diagnostics:
     - Created `internal SafeFormResolver`, `internal DiagnosticDecision`, and pure `internal BoundedDiagnosticGate` in `com.cobblemonextendedbattleui.pokemon`.
     - Capped failure diagnostics at 32 unique keys, emitting exactly one suppression notice after the cap with zero unbounded memory growth.
     - Caught `Exception` (preserving unhandled `Error`/fatal throwables), logging actionable warnings with context, species, candidate aspects, and exception class/message while returning null for graceful fallback.
     - Replaced all unguarded and silent `runCatching` `species.getForm(...)` calls in `DeltaBattleInfoReader` and `TeamIndicatorUI`, preserving Shaymin and explicit form preference semantics and preserving all existing responsive geometry diffs in `TeamIndicatorUI`.
   - Focused unit regression tests:
     - `CalcBattleSnapshotFingerprintTest`: asserts unchanged snapshots have identical fingerprints; tests collision resistance across pipe separators and length-prefix injection for `compatNotes`, `revealedMoves`, `typeNames`, `moveList`, `statStages`, and user strings; and verifies single-field mutation sensitivity across `teraType`, `baseStats`, `actualStats`, `canEvolve`, `weightKg`, and `compatNotes`.
     - `CalcInvalidationCoordinatorTest`: asserts first update returns true with `BATTLE_STARTED`, unchanged update returns false with empty reasons, newly covered field changes return true and emit `CALC_INPUT_CHANGE`, known field changes emit their specific reason without `CALC_INPUT_CHANGE`, and repeated unconsumed transitions preserve history-independence without spurious `CALC_INPUT_CHANGE`.
     - `SafeFormResolverTest`: tests `BoundedDiagnosticGate` duplicate keys logging once, unique keys logging through the cap of 32, single suppression notice emitted thereafter, strictly bounded seen key count, test reset hook, and `safeResolve` exception capture and `Error` propagation without initializing Cobblemon runtime.
   - Depends on step 2; may run alongside step 4 only if files do not overlap.

6. `DONE` - Sol review and correction gate
   - Final bounded release-readiness batch implemented:
     - Added one-shot HUD layout reset toggle in ModMenu / Cloth Config under dedicated Layout category (`ModMenuIntegration.kt`, localized in `en_us.json`), executing `resetHudLayout()` and `resetLayout()` only upon clicking Done while preserving all non-geometry preferences and doing nothing on Cancel.
     - Separated fresh-install panel lanes by adjusting `BattleInfoPanel.kt` unsaved default X from right-aligned to left margin (`BASE_PANEL_MARGIN`), preventing overlap with DamageCalc while preserving vertical centering and existing saved positions.
     - Set `mod_version=3.2.0` in `gradle.properties` as the next release candidate after the public Modrinth 3.1.1 baseline.
     - Hardened `UpdateChecker.isNewerVersion` and `parseVersionCore` as pure internal functions to handle `v`/`V` prefixes, suffixes such as `-deltacalc`, component padding with zero, and failing closed on invalid inputs or nonnumeric middle components. Added pure JUnit tests in `UpdateCheckerVersionTest.kt`.
     - Documented HUD layout reset and default panel positions in `README.md` and added concise plain-ASCII `3.2.0` entry to `CHANGELOG.md`.
   - Review every Gemini-authored diff for correctness, scope, Minecraft API
     assumptions, error handling, naming, and UI behavior (`DONE`).
   - Multiple completed Sol review and correction passes over the Gemini changes justify gate completion, including structured KO presentation, narrow-header bounds, override hit targets, fingerprint collision resistance, invalidation history-independence, version parser, default lane, and Cloth Config save ordering.
   - Runtime verification remains pending live client launch in step 8.
   - Depends on steps 3-5.

7. `DONE` - Static verification and build
   - Run focused tests (`UpdateCheckerVersionTest`, `BattleInfoPanelLayoutTest`), full tests (`test --rerun-tasks --no-daemon`), and a clean build (`clean build --no-daemon`) (`DONE`).
   - `BattleInfoPanelLayoutTest.testReconcileBoundsWithDefaults` updated to expect left margin (X=10), aligning with fresh-install separation; all 144 unit tests pass.
   - Clean build produces release candidate jars: `deltacalc.jar` and `deltacalc-3.2.0-sources.jar`.
   - Worktree diff verified against permitted boundaries (`git diff --check` clean).
   - Release blocker crash fix and verification:
     - Runtime evidence in Java 21 `runClient` identified client startup crash: `BattleOverlayMixin ... @Inject ... could not find any targets matching 'method_1753' in ...BattleOverlay. No refMap loaded.`
     - Root cause: Dev Cobblemon remapped jar exposes `public void render(DrawContext, RenderTickCounter)`, while production jar exposes `public void method_1753(class_332, class_9779)`. Dev runtime loads no refmap, causing hardcoded intermediary selector `method_1753` with `remap = false` to fail target resolution.
     - Correction: Configured dual selector `@Inject(method = {"render", "method_1753"}, at = @At("HEAD"), remap = false)` in `BattleOverlayMixin.java`, cleanly resolving targets across both namespaces while preserving `defaultRequire = 1` fail-loud behavior. Added `BattleOverlayMixinTargetTest.kt` (all 145 unit tests pass; `clean build --no-daemon` succeeds).
     - Verified production remapped jar bytecode retains both selectors and explicit `remap = false` with no invalid refmap entries.
   - Depends on step 6.

8. `BLOCKED` - Runtime viewport and interaction matrix
   - Launch the development client using the existing normal workflow (`.\gradlew.bat runClient`).
   - Java 21 rerun passed the startup gate after the dual-selector fix: Fabric loaded DeltaCalc 3.2.0, both DeltaCalc entrypoints initialized, Cobblemon assets finished loading, the sound engine started, and the owned `Minecraft* 1.21.1` window remained responsive until intentionally terminated.
   - The first Java 21 run exposed the `BattleOverlayMixin` namespace crash; the post-fix rerun did not reproduce it. The earlier Java 24 rejection was an environment mismatch and was resolved with a process-local Java 21 override.
   - Exercise representative scaled viewports and GUI/font scales, saved-layout
     migration, reset layout, drag/resize, calculator scrolling, overrides,
     collapse/expand, and unknown/waiting states.
   - `BLOCKED`: the required Windows Computer Use runtime loaded, but its native capture pipe remained unavailable after the permitted retry and kernel-reset recovery sequence. No screenshots or GUI inputs were fabricated. The dev instance also did not enter a live Delta battle, so battle-only interaction behavior remains unverified at runtime.
   - Authoritative startup evidence remains in the ignored `run/logs/latest.log`; generated logs and crash reports are intentionally excluded from source control.
   - Depends on step 7.

9. `DONE` - Release-candidate handoff
   - Final Java 21 verification: 145 tests passed, 0 failures, 0 errors; `clean build --no-daemon` succeeded; both language JSON files parsed successfully; `git diff --check` is clean.
   - Release candidate: `build/libs/deltacalc.jar` (2,340,269 bytes), SHA-256 `07E15DFBD690244D91EBCD982781966AB2BDEE89F4F235CE9B0FE5F122343705`.
   - Packaged `fabric.mod.json` reports DeltaCalc `3.2.0`; the remapped jar retains both `render` and `method_1753` overlay selectors with fail-loud injection behavior.
   - Main checkout commit and its existing `tools/database/import_delta_movesets.py` edit remain untouched; historical worktrees remain registered.
   - Screenshot matrix and live Delta-battle interactions are explicitly listed as unresolved limitations from step 8 rather than claimed as evidence.
   - Leave publishing, tagging, pushing, and Modrinth upload unperformed unless
     explicitly authorized.
   - Depends on completed startup/static gates and carries the blocked visual-matrix limitation from step 8.

## Validation commands

Run from the isolated worktree unless noted otherwise:

```powershell
git status --short --branch
.\gradlew.bat test --no-daemon
.\gradlew.bat clean build --no-daemon
git diff --check
git diff --stat
```

Runtime validation uses:

```powershell
.\gradlew.bat runClient
```

The runtime evidence must record scaled viewport dimensions, Minecraft GUI
scale, calculator font scale, observed panel bounds, screenshots, logs, and the
exact built jar hash.

## Review gates

- Gate A: pure geometry and tests are understandable without Minecraft runtime
  objects and prove the previously invalid clamp cases.
- Gate B: calculator changes preserve the established interaction model where
  reasonable while making controls and uncertainty visible.
- Gate C: no broad damage-engine rewrite or speculative architecture is added.
- Gate D: compilation is necessary but not sufficient; runtime screenshots and
  interaction checks control final acceptance.
- Gate E: no release/push/upload occurs during this task without explicit user
  authorization.
