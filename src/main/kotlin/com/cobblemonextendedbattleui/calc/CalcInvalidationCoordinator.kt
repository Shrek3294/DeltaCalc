package com.cobblemonextendedbattleui.calc

enum class CalcInvalidationReason {
    BATTLE_STARTED,
    ACTIVE_SWITCH,
    MOVE_REVEAL,
    ITEM_REVEAL,
    ABILITY_REVEAL,
    HP_CHANGE,
    STATUS_CHANGE,
    STAT_STAGE_CHANGE,
    WEATHER_CHANGE,
    TERRAIN_CHANGE,
    SIDE_CONDITION_CHANGE,
    PLAYER_MOVE_SELECTION_CHANGE
}

class CalcInvalidationCoordinator {
    private var previousSnapshot: CalcBattleSnapshot? = null
    private val pendingReasons = linkedSetOf<CalcInvalidationReason>()

    fun update(snapshot: CalcBattleSnapshot): Boolean {
        val previous = previousSnapshot
        if (previous == null) {
            pendingReasons += CalcInvalidationReason.BATTLE_STARTED
            previousSnapshot = snapshot
            return true
        }
        if (previous.fingerprint() == snapshot.fingerprint()) {
            return false
        }

        if (previous.playerActive?.displayName != snapshot.playerActive?.displayName ||
            previous.opponentActive?.displayName != snapshot.opponentActive?.displayName
        ) pendingReasons += CalcInvalidationReason.ACTIVE_SWITCH
        if (previous.selectedMoveName != snapshot.selectedMoveName) pendingReasons += CalcInvalidationReason.PLAYER_MOVE_SELECTION_CHANGE
        if (previous.weather != snapshot.weather) pendingReasons += CalcInvalidationReason.WEATHER_CHANGE
        if (previous.terrain != snapshot.terrain) pendingReasons += CalcInvalidationReason.TERRAIN_CHANGE
        if (previous.playerSide.sideConditions != snapshot.playerSide.sideConditions ||
            previous.opponentSide.sideConditions != snapshot.opponentSide.sideConditions
        ) pendingReasons += CalcInvalidationReason.SIDE_CONDITION_CHANGE
        if (previous.playerActive?.currentHp != snapshot.playerActive?.currentHp ||
            previous.opponentActive?.currentHp != snapshot.opponentActive?.currentHp
        ) pendingReasons += CalcInvalidationReason.HP_CHANGE
        if (previous.playerActive?.status != snapshot.playerActive?.status ||
            previous.opponentActive?.status != snapshot.opponentActive?.status
        ) pendingReasons += CalcInvalidationReason.STATUS_CHANGE
        if (previous.playerActive?.statStages != snapshot.playerActive?.statStages ||
            previous.opponentActive?.statStages != snapshot.opponentActive?.statStages
        ) pendingReasons += CalcInvalidationReason.STAT_STAGE_CHANGE
        if (previous.opponentActive?.revealedMoves != snapshot.opponentActive?.revealedMoves) pendingReasons += CalcInvalidationReason.MOVE_REVEAL
        if (previous.opponentActive?.itemName != snapshot.opponentActive?.itemName) pendingReasons += CalcInvalidationReason.ITEM_REVEAL
        if (previous.opponentActive?.abilityName != snapshot.opponentActive?.abilityName) pendingReasons += CalcInvalidationReason.ABILITY_REVEAL

        previousSnapshot = snapshot
        return true
    }

    fun consumeReasons(): Set<CalcInvalidationReason> {
        val copy = pendingReasons.toSet()
        pendingReasons.clear()
        return copy
    }
}
