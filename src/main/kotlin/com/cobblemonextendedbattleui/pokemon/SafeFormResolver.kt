package com.cobblemonextendedbattleui.pokemon

import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Species
import com.cobblemonextendedbattleui.CobblemonExtendedBattleUI

internal enum class DiagnosticDecision {
    LOG_WARNING,
    LOG_SUPPRESSION_NOTICE,
    SILENT
}

internal class BoundedDiagnosticGate(val maxUniqueKeys: Int = 32) {
    private val loggedKeys = mutableSetOf<String>()
    private var suppressionNoticeEmitted = false

    @Synchronized
    fun recordFailure(key: String): DiagnosticDecision {
        if (key in loggedKeys) {
            return DiagnosticDecision.SILENT
        }
        if (loggedKeys.size < maxUniqueKeys) {
            loggedKeys += key
            return DiagnosticDecision.LOG_WARNING
        }
        if (!suppressionNoticeEmitted) {
            suppressionNoticeEmitted = true
            return DiagnosticDecision.LOG_SUPPRESSION_NOTICE
        }
        return DiagnosticDecision.SILENT
    }

    val seenKeyCount: Int
        @Synchronized get() = loggedKeys.size

    val isSuppressed: Boolean
        @Synchronized get() = suppressionNoticeEmitted

    @Synchronized
    fun resetForTests() {
        loggedKeys.clear()
        suppressionNoticeEmitted = false
    }
}

internal object SafeFormResolver {
    internal val gate = BoundedDiagnosticGate(maxUniqueKeys = 32)

    internal fun resetForTests() {
        gate.resetForTests()
    }

    /**
     * Safely executes [block], catching any [Exception] (not [Throwable]),
     * logging rate-limited actionable diagnostics, and returning null on failure.
     */
    fun <T> safeResolve(
        context: String,
        speciesName: String?,
        aspects: Collection<String>,
        block: () -> T?
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            handleException(context, speciesName, aspects, e)
            null
        }
    }

    fun safeGetForm(
        species: Species,
        aspects: Set<String>,
        context: String
    ): FormData? {
        val speciesName = runCatching { species.resourceIdentifier.toString() }.getOrNull() ?: species.name
        return safeResolve(
            context = context,
            speciesName = speciesName,
            aspects = aspects
        ) {
            species.getForm(aspects)
        }
    }

    fun safeGetForm(
        species: Species,
        aspect: String,
        context: String
    ): FormData? = safeGetForm(species, setOf(aspect), context)

    internal fun handleException(
        context: String,
        speciesName: String?,
        aspects: Collection<String>,
        e: Exception
    ) {
        val normalizedAspects = aspects.sorted().joinToString(",")
        val key = "$context:$speciesName:[$normalizedAspects]"
        when (gate.recordFailure(key)) {
            DiagnosticDecision.LOG_WARNING -> {
                CobblemonExtendedBattleUI.LOGGER.warn(
                    "Safe form resolution failed in context='$context' for species='$speciesName', candidate/aspects=[$normalizedAspects]: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
            DiagnosticDecision.LOG_SUPPRESSION_NOTICE -> {
                CobblemonExtendedBattleUI.LOGGER.warn(
                    "Safe form resolution warning limit reached (${gate.maxUniqueKeys} unique errors). Further form resolution warnings will be suppressed."
                )
            }
            DiagnosticDecision.SILENT -> {}
        }
    }
}
