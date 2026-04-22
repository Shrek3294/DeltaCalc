package com.cobblemonextendedbattleui

import net.minecraft.text.Text
import java.util.LinkedHashSet

object BattleMoveSupport {
    fun isIvyCudgel(moveIdOrName: String?): Boolean = normalizeToken(moveIdOrName).endsWith("ivycudgel")

    fun resolveDisplayName(rawName: String?): String {
        val trimmed = rawName?.trim().orEmpty()
        if (trimmed.isBlank()) return ""

        if (trimmed.startsWith("cobblemon.move.")) {
            val translated = Text.translatable(trimmed).string
            if (!translated.startsWith("cobblemon.move.")) {
                return translated
            }
            return prettifyToken(trimmed.substringAfterLast('.'))
        }

        return trimmed
    }

    fun resolveMoveId(rawName: String?): String {
        val trimmed = rawName?.trim().orEmpty()
        if (trimmed.isBlank()) return ""

        val baseToken = when {
            trimmed.startsWith("cobblemon.move.") -> trimmed.substringAfterLast('.')
            ':' in trimmed -> trimmed.substringAfterLast(':')
            else -> trimmed
        }
        return normalizeToken(baseToken)
    }

    fun moveLookupCandidates(rawName: String?): List<String> {
        val trimmed = rawName?.trim().orEmpty()
        if (trimmed.isBlank()) return emptyList()

        val candidates = LinkedHashSet<String>()
        val displayName = resolveDisplayName(trimmed)
        val suffix = trimmed.substringAfterLast('.')

        listOf(
            trimmed,
            suffix,
            displayName,
            resolveMoveId(trimmed),
            resolveMoveId(suffix),
            resolveMoveId(displayName)
        ).forEach { candidate ->
            if (candidate.isNotBlank()) {
                candidates += candidate
            }
        }

        return candidates.toList()
    }

    fun resolveIvyCudgelTypeName(
        moveIdOrName: String?,
        speciesId: String? = null,
        formName: String? = null,
        heldItemName: String? = null,
        heldItemId: String? = null,
        pokemonName: String? = null
    ): String? {
        if (!isIvyCudgel(moveIdOrName)) return null

        val normalizedContext = normalizeToken(
            listOfNotNull(speciesId, formName, heldItemName, heldItemId, pokemonName).joinToString(" ")
        )

        return when {
            "wellspring" in normalizedContext -> "water"
            "cornerstone" in normalizedContext -> "rock"
            "hearthflame" in normalizedContext || "stormpeak" in normalizedContext -> "fire"
            "ogerpon" in normalizedContext -> "grass"
            else -> null
        }
    }

    private fun prettifyToken(rawToken: String): String {
        return rawToken
            .split('-', '_')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { it.uppercase() }
            }
    }

    private fun normalizeToken(value: String?): String {
        return value.orEmpty().lowercase()
            .replace("cobblemonmove", "")
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("'", "")
            .replace(".", "")
            .replace(":", "")
    }
}
