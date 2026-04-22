package com.cobblemonextendedbattleui.calc

import com.cobblemonextendedbattleui.PanelConfig
import com.cobblemonextendedbattleui.TeamIndicatorUI
import com.cobblemonextendedbattleui.compat.delta.DeltaBattlePlatformAdapter
import com.cobblemonextendedbattleui.tracking.BattleStateFacade
import kotlin.math.roundToInt

object CalcComputationService {
    private val platformAdapter = DeltaBattlePlatformAdapter
    private val battleDatabase = BattleDatabase.loadDefault()
    private val invalidationCoordinator = CalcInvalidationCoordinator()
    private val inferenceService = OpponentInferenceService()
    private val damageEngine: DamageEngine = BestEffortDamageEngine
    private var currentModel: CalcRenderModel? = null

    fun currentModel(): CalcRenderModel? {
        val truth = BattleStateFacade.capture(platformAdapter) ?: run {
            currentModel = null
            return null
        }

        val snapshot = CalcBattleSnapshotFactory.fromTruth(truth, battleDatabase)
        if (invalidationCoordinator.update(snapshot) || currentModel == null) {
            val inferredSet = inferenceService.infer(snapshot, battleDatabase)
            val reasons = invalidationCoordinator.consumeReasons()
            val damageResult = damageEngine.compute(snapshot, inferredSet)
            val reasonText = if (reasons.isEmpty()) {
                "Snapshot stable"
            } else {
                "Recomputed: " + reasons.joinToString(", ") { it.name.lowercase().replace('_', ' ') }
            }
            val warningText = damageResult.warnings.takeIf { it.isNotEmpty() }?.joinToString(" | ")
            val trackedFallbackMoves = TeamIndicatorUI.getTrackedRevealedMoves(
                displayName = snapshot.opponentActive?.displayName,
                speciesName = snapshot.opponentActive?.speciesId,
                formName = snapshot.opponentActive?.formName
            )
            val debugText = buildString {
                append("Calc rev: ")
                append(snapshot.opponentActive?.revealedMoves?.joinToString(", ").orEmpty().ifBlank { "none" })
                append(" | Tracked rev: ")
                append(trackedFallbackMoves.joinToString(", ").ifBlank { "none" })
                append(" | Rendered: ")
                append(damageResult.opponentMoves.joinToString(", ") { it.moveName })
            }
            currentModel = CalcRenderModel(
                snapshot = snapshot,
                opponentSet = inferredSet,
                yourMoves = damageResult.yourMoves.map(::toRow),
                opponentMoves = damageResult.opponentMoves.map(::toRow),
                statusText = listOfNotNull(reasonText, warningText).joinToString(" | "),
                debugText = debugText
            )
            currentModel?.let { DebugDumper.onComputation(it, damageResult) }
        }
        return currentModel
    }

    private fun toRow(estimate: DamageEstimate): CalcMoveRow {
        val damageText = when {
            !estimate.supported -> estimate.warnings.firstOrNull()?.lowercase() ?: "unsupported"
            estimate.minPercent != null && estimate.maxPercent != null -> buildString {
                append(formatPercent(estimate.minPercent))
                append(" - ")
                append(formatPercent(estimate.maxPercent))
                append('%')
                if (PanelConfig.showMultiHitCount && estimate.maxHits > 1) {
                    if (estimate.minHits == estimate.maxHits) {
                        append(" ×").append(estimate.minHits)
                    } else {
                        append(" ×").append(estimate.minHits).append('-').append(estimate.maxHits)
                    }
                }
                if (PanelConfig.showCritDamage && estimate.critMinPercent != null && estimate.critMaxPercent != null) {
                    append(" / crit ")
                    append(formatPercent(estimate.critMinPercent))
                    append('-')
                    append(formatPercent(estimate.critMaxPercent))
                    append('%')
                }
            }
            else -> "best-effort"
        }
        return CalcMoveRow(
            moveName = estimate.moveName,
            damageText = damageText,
            koText = estimate.koLabel,
            emphasized = estimate.emphasized
        )
    }

    private fun formatPercent(value: Double): String {
        val roundedTenth = (value * 10.0).roundToInt() / 10.0
        return if (roundedTenth == roundedTenth.toInt().toDouble()) {
            roundedTenth.toInt().toString()
        } else {
            roundedTenth.toString()
        }
    }
}
