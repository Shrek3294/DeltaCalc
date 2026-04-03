package com.cobblemonextendedbattleui.compat.core

import java.util.UUID

interface BattlePlatformAdapter {
    fun currentBattleId(): UUID?
    fun currentTurn(): Int
    fun selectedMoveName(): String?
    fun currentScreenClassName(): String?
    fun compatNotes(): List<String>

    fun isBattleActive(): Boolean = currentBattleId() != null
}

