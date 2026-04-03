package com.cobblemonextendedbattleui.compat.delta

import com.cobblemon.mod.common.client.CobblemonClient
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import java.lang.reflect.Method
import java.util.UUID

data class DeltaPokemonRevealData(
    val moves: List<String> = emptyList(),
    val ability: String? = null,
    val heldItem: String? = null,
    val speed: Int? = null
)

object DeltaBattleInfoReader {
    private const val REPOSITORY_CLASS = "com.symstudios.deltaclient.battle.DeltaBattleInformationRepository"

    fun activeOpponentRevealData(): DeltaPokemonRevealData? {
        val battle = CobblemonClient.battle ?: return null
        val playerUuid = MinecraftClient.getInstance().player?.uuid ?: return null
        val playerSide = when {
            battle.side1.actors.any { it.uuid == playerUuid } -> battle.side1
            battle.side2.actors.any { it.uuid == playerUuid } -> battle.side2
            else -> return null
        }
        val opponentSide = if (playerSide == battle.side1) battle.side2 else battle.side1
        val activeOpponentUuids = opponentSide.activeClientBattlePokemon
            .mapNotNull { it.battlePokemon?.uuid }
            .toSet()
        if (activeOpponentUuids.isEmpty()) return null

        val actorMap = repositoryActors() ?: return null
        val actorDtos = opponentSide.actors
            .mapNotNull { actorMap[it.uuid] }
            .flatten()
        if (actorDtos.isEmpty()) return null

        val matchingDtos = actorDtos.filter { dto ->
            val dtoUuid = invokeUuid(dto, "getUuid")
                ?: invokeActiveBattlePokemonUuid(dto)
            dtoUuid != null && dtoUuid in activeOpponentUuids
        }
        if (matchingDtos.isEmpty()) return null

        val moveNames = linkedSetOf<String>()
        var ability: String? = null
        var heldItem: String? = null
        var speed: Int? = null

        for (dto in matchingDtos) {
            moveNames += invokeMoveNames(dto)
            if (ability.isNullOrBlank()) {
                ability = invokeString(dto, "getAbility")?.takeUnless { it == "?" }
            }
            if (heldItem.isNullOrBlank()) {
                heldItem = invokeItemName(dto)
            }
            if (speed == null) {
                speed = invokeInt(dto, "getSpeed")
            }
        }

        return DeltaPokemonRevealData(
            moves = moveNames.toList(),
            ability = ability,
            heldItem = heldItem,
            speed = speed
        )
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

    private fun invokeActiveBattlePokemonUuid(dto: Any): UUID? {
        return try {
            val activeDto = dto.javaClass.getMethod("getActiveBattlePokemonDTO").invoke(dto) ?: return null
            invokeUuid(activeDto, "getUuid")
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeMoveNames(dto: Any): List<String> {
        return try {
            val moves = dto.javaClass.getMethod("getMoves").invoke(dto) as? List<*> ?: return emptyList()
            moves.mapNotNull { moveDto ->
                val moveText = moveDto?.javaClass?.getMethod("getMove")?.invoke(moveDto) as? Text
                moveText?.string?.trim()?.takeIf { it.isNotBlank() && it != "?" }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun invokeItemName(dto: Any): String? {
        return try {
            val stack = dto.javaClass.getMethod("getHeldItem").invoke(dto) as? ItemStack ?: return null
            if (stack.isEmpty) return null
            stack.name.string.takeIf { it.isNotBlank() && it != "?" }
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeUuid(target: Any, methodName: String): UUID? {
        return invokeMethod(target, methodName) as? UUID
    }

    private fun invokeString(target: Any, methodName: String): String? {
        return (invokeMethod(target, methodName) as? String)?.trim()
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
