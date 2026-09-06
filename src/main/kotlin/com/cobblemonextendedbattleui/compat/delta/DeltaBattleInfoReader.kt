package com.cobblemonextendedbattleui.compat.delta

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemonextendedbattleui.BattleStateTracker
import com.cobblemonextendedbattleui.pokemon.SafeFormResolver
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.lang.reflect.Method
import java.util.UUID

data class DeltaPokemonRevealData(
    val moves: List<String> = emptyList(),
    val ability: String? = null,
    val heldItem: String? = null,
    val speed: Int? = null
)

data class DeltaTeamPreviewEntry(
    val uuid: UUID,
    val displayName: String,
    val speciesIdentifier: Identifier,
    val formName: String?,
    val formTypeNames: List<String> = emptyList(),
    val formBaseStats: List<Int> = emptyList(),
    val revealedMoves: List<String> = emptyList(),
    val revealedAbility: String? = null,
    val revealedItem: String? = null,
    val speed: Int? = null,
    val isActive: Boolean = false,
    val aspects: Set<String> = emptySet()
)

data class DeltaTeamPreviewResult(
    val entries: List<DeltaTeamPreviewEntry> = emptyList(),
    val failureReason: String? = null
)

object DeltaBattleInfoReader {
    private const val REPOSITORY_CLASS = "com.symstudios.deltaclient.battle.DeltaBattleInformationRepository"

    fun activeOpponentRevealData(opponentUuid: UUID): DeltaPokemonRevealData? {
        val battle = CobblemonClient.battle ?: return null
        val playerUuid = MinecraftClient.getInstance().player?.uuid ?: return null
        val playerSide = when {
            battle.side1.actors.any { it.uuid == playerUuid } -> battle.side1
            battle.side2.actors.any { it.uuid == playerUuid } -> battle.side2
            else -> return null
        }
        val opponentSide = if (playerSide == battle.side1) battle.side2 else battle.side1
        val preview = teamPreviewForSide(
            actorUuids = opponentSide.actors.map { it.uuid }.toSet(),
            activePokemonUuids = opponentSide.activeClientBattlePokemon.mapNotNull { it.battlePokemon?.uuid }.toSet()
        )
        return preview.entries
            .firstOrNull { it.uuid == opponentUuid && it.isActive }
            ?.let { entry ->
                DeltaPokemonRevealData(
                    moves = entry.revealedMoves,
                    ability = entry.revealedAbility,
                    heldItem = entry.revealedItem,
                    speed = entry.speed
                )
            }
    }

    fun teamPreviewForSide(
        actorUuids: Set<UUID>,
        activePokemonUuids: Set<UUID>
    ): DeltaTeamPreviewResult {
        if (actorUuids.isEmpty()) {
            return DeltaTeamPreviewResult()
        }

        val actorMap = repositoryActors()
            ?: return DeltaTeamPreviewResult(failureReason = "Delta full team preview unavailable: repository not accessible")

        val actorDtos = actorUuids.mapNotNull { actorMap[it] }.flatten()
        if (actorDtos.isEmpty()) {
            return DeltaTeamPreviewResult(failureReason = "Delta full team preview unavailable: no party entries found")
        }

        val entries = mutableListOf<DeltaTeamPreviewEntry>()
        for (dto in actorDtos) {
            val entry = extractPreviewEntry(dto, activePokemonUuids)
                ?: return DeltaTeamPreviewResult(failureReason = "Delta full team preview unavailable: missing species identity")
            entries += entry
        }

        return DeltaTeamPreviewResult(entries = entries.distinctBy { it.uuid })
    }

    @Suppress("UNCHECKED_CAST")
    private fun repositoryActors(): Map<UUID, List<Any>>? {
        return try {
            val repositoryClass = Class.forName(REPOSITORY_CLASS)
            val instance = repositoryClass.getField("INSTANCE").get(null)
            val actors = repositoryClass.getMethod("getActors").invoke(instance) as? Map<*, *> ?: return null
            val result = LinkedHashMap<UUID, List<Any>>()
            for (entry in actors.entries) {
                val uuid = entry.key as? UUID ?: continue
                val list = entry.value as? List<*> ?: continue
                result[uuid] = list.filterNotNull()
            }
            result
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractPreviewEntry(dto: Any, activePokemonUuids: Set<UUID>): DeltaTeamPreviewEntry? {
        val activeDto = invokeMethod(dto, "getActiveBattlePokemonDTO")
        val uuid = invokeUuid(dto, "getUuid")
            ?: activeDto?.let { invokeUuid(it, "getUuid") }
            ?: return null
        val speciesIdentifier = resolveSpeciesIdentifier(dto, activeDto) ?: return null
        val rawFormName = resolveFormName(dto, activeDto)
        val aspects = resolveAspects(dto, activeDto, rawFormName)
        val form = resolveFormData(speciesIdentifier, rawFormName, aspects)
        val formName = form?.name?.takeIf { it.isNotBlank() } ?: rawFormName?.takeIf { it.isNotBlank() }
        val displayName = resolveDisplayName(dto, activeDto, speciesIdentifier, formName)

        return DeltaTeamPreviewEntry(
            uuid = uuid,
            displayName = displayName,
            speciesIdentifier = speciesIdentifier,
            formName = formName,
            formTypeNames = listOfNotNull(form?.primaryType?.name, form?.secondaryType?.name),
            formBaseStats = listOf(
                form?.baseStats?.get(Stats.HP) ?: 0,
                form?.baseStats?.get(Stats.ATTACK) ?: 0,
                form?.baseStats?.get(Stats.DEFENCE) ?: 0,
                form?.baseStats?.get(Stats.SPECIAL_ATTACK) ?: 0,
                form?.baseStats?.get(Stats.SPECIAL_DEFENCE) ?: 0,
                form?.baseStats?.get(Stats.SPEED) ?: 0
            ),
            revealedMoves = invokeMoveNames(dto),
            revealedAbility = invokeNonQuestionString(dto, "getAbility"),
            revealedItem = invokeItemName(dto),
            speed = invokeInt(dto, "getSpeed"),
            isActive = uuid in activePokemonUuids,
            aspects = aspects
        )
    }

    private fun resolveSpeciesIdentifier(dto: Any, activeDto: Any?): Identifier? {
        val properties = activeDto?.let { invokeMethod(it, "getProperties") }
        val directCandidates = buildList {
            addAll(extractStringCandidates(dto, "getSpeciesId", "getSpecies", "getSpeciesName", "getPokemonName"))
            activeDto?.let { addAll(extractStringCandidates(it, "getSpeciesId", "getSpecies", "getSpeciesName")) }
            properties?.let { addAll(extractStringCandidates(it, "getSpecies")) }
        }

        return directCandidates
            .firstNotNullOfOrNull(::parseSpeciesIdentifier)
    }

    private fun resolveFormName(dto: Any, activeDto: Any?): String? {
        val properties = activeDto?.let { invokeMethod(it, "getProperties") }
        return buildList {
            addAll(extractStringCandidates(dto, "getFormName", "getForm"))
            activeDto?.let { addAll(extractStringCandidates(it, "getFormName", "getForm")) }
            properties?.let { addAll(extractStringCandidates(it, "getForm")) }
        }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != "?" }
    }

    private fun resolveAspects(dto: Any, activeDto: Any?, formName: String?): Set<String> {
        val directAspects = buildList {
            addAll(invokeStringCollection(dto, "getAspects"))
            addAll(invokeStringCollection(dto, "getCurrentAspects"))
            activeDto?.let {
                addAll(invokeStringCollection(it, "getAspects"))
                addAll(invokeStringCollection(it, "getCurrentAspects"))
            }
        }
            .map(::normalizeToken)
            .filter { it.isNotBlank() }
            .toCollection(linkedSetOf())

        if (directAspects.isNotEmpty()) {
            return directAspects
        }

        return formName
            ?.takeIf { it.isNotBlank() }
            ?.let(BattleStateTracker::formNameToAspects)
            ?.map(::normalizeToken)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    private fun resolveFormData(
        speciesIdentifier: Identifier,
        formName: String?,
        aspects: Set<String>
    ): FormData? {
        val species = PokemonSpecies.getByIdentifier(speciesIdentifier) ?: return null
        val namedForm = formName?.takeIf { it.isNotBlank() }
        if (namedForm != null) {
            val explicitForm = run {
                val aspectCandidates = LinkedHashSet<String>()
                aspectCandidates += BattleStateTracker.formNameToAspects(namedForm)
                aspectCandidates += normalizeToken(namedForm)
                aspectCandidates += normalizeToken(namedForm).removePrefix(normalizeToken(speciesIdentifier.path)).trim('-')

                for (candidate in aspectCandidates) {
                    if (candidate.isBlank()) continue
                    val form = SafeFormResolver.safeGetForm(species, candidate, context = "DeltaBattleInfoReader.resolveFormData.named")
                    if (form != null && (form != species.standardForm || candidate == species.standardForm.aspects.firstOrNull())) {
                        return@run form
                    }
                }
                null
            }
            return explicitForm ?: species.standardForm
        }
        if (aspects.isNotEmpty()) {
            val aspectForm = SafeFormResolver.safeGetForm(species, aspects, context = "DeltaBattleInfoReader.resolveFormData.aspects")
            if (aspectForm != null && (aspectForm != species.standardForm || aspects == species.standardForm.aspects)) {
                return aspectForm
            }
        }

        val rawFormName = formName?.takeIf { it.isNotBlank() } ?: return species.standardForm
        val aspectCandidates = LinkedHashSet<String>()
        aspectCandidates += BattleStateTracker.formNameToAspects(rawFormName)
        aspectCandidates += normalizeToken(rawFormName)
        aspectCandidates += normalizeToken(rawFormName).removePrefix(normalizeToken(speciesIdentifier.path)).trim('-')

        for (candidate in aspectCandidates) {
            if (candidate.isBlank()) continue
            val form = SafeFormResolver.safeGetForm(species, candidate, context = "DeltaBattleInfoReader.resolveFormData.raw")
            if (form != null && (form != species.standardForm || candidate == species.standardForm.aspects.firstOrNull())) {
                return form
            }
        }

        return species.standardForm
    }

    private fun resolveDisplayName(
        dto: Any,
        activeDto: Any?,
        speciesIdentifier: Identifier,
        formName: String?
    ): String {
        val display = buildList {
            addAll(extractStringCandidates(dto, "getDisplayName"))
            activeDto?.let { addAll(extractStringCandidates(it, "getDisplayName")) }
        }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it != "?" }

        if (!display.isNullOrBlank()) {
            return display
        }

        val baseLabel = speciesIdentifier.path
            .split("-", "_")
            .joinToString("-") { it.replaceFirstChar { ch -> ch.uppercase() } }
        val cleanedForm = formName
            ?.replace(baseLabel, "", ignoreCase = true)
            ?.trim()
            ?.trim('-', '_', ' ')
            .orEmpty()

        return if (cleanedForm.isBlank()) {
            baseLabel
        } else {
            "$baseLabel-${cleanedForm.split("-", "_", " ").filter { it.isNotBlank() }.joinToString("-") { token -> token.replaceFirstChar { it.uppercase() } }}"
        }
    }

    private fun invokeMoveNames(dto: Any): List<String> {
        return try {
            val moves = invokeMethod(dto, "getMoves") as? List<*> ?: return emptyList()
            moves.mapNotNull { moveDto ->
                val moveText = moveDto?.let { invokeMethod(it, "getMove") }
                valueToString(moveText)?.trim()?.takeIf { it.isNotBlank() && it != "?" }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun invokeItemName(dto: Any): String? {
        return try {
            val stack = invokeMethod(dto, "getHeldItem") as? ItemStack ?: return null
            if (stack.isEmpty) return null
            stack.name.string.takeIf { it.isNotBlank() && it != "?" }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractStringCandidates(target: Any, vararg methodNames: String): List<String> {
        return methodNames.mapNotNull { methodName ->
            valueToString(invokeMethod(target, methodName))
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun invokeStringCollection(target: Any, methodName: String): List<String> {
        return when (val result = invokeMethod(target, methodName)) {
            is Collection<*> -> result.mapNotNull(::valueToString)
            else -> emptyList()
        }
    }

    private fun valueToString(value: Any?): String? {
        return when (value) {
            null -> null
            is String -> value
            is Text -> value.string
            is Identifier -> value.toString()
            else -> {
                val identifier = invokeMethod(value, "getResourceIdentifier") as? Identifier
                if (identifier != null) {
                    identifier.toString()
                } else {
                    null
                }
            }
        }
    }

    private fun parseSpeciesIdentifier(value: String): Identifier? {
        val trimmed = value.trim()
        if (trimmed.isBlank() || trimmed == "?") return null
        return Identifier.tryParse(trimmed)
            ?: Identifier.tryParse(trimmed.lowercase())
            ?: runCatching { Identifier.of("cobblemon", normalizeToken(trimmed)) }.getOrNull()
    }

    private fun normalizeToken(value: String): String {
        return value.lowercase()
            .replace(" ", "-")
            .replace("_", "-")
    }

    private fun invokeUuid(target: Any, methodName: String): UUID? {
        return invokeMethod(target, methodName) as? UUID
    }

    private fun invokeNonQuestionString(target: Any, methodName: String): String? {
        return (invokeMethod(target, methodName) as? String)
            ?.trim()
            ?.takeUnless { it.isBlank() || it == "?" }
    }

    private fun invokeInt(target: Any, methodName: String): Int? {
        return invokeMethod(target, methodName) as? Int
    }

    private fun invokeMethod(target: Any, methodName: String): Any? {
        return try {
            val method: Method = target.javaClass.getMethod(methodName)
            method.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }
}
