package com.cobblemonextendedbattleui.calc

import java.util.UUID

data class CalcPreviewSelection(
    val playerUuid: UUID?,
    val opponentUuid: UUID?
)

class CalcPreviewSelectionState {
    private var battleId: UUID? = null
    private var lastPlayerActiveUuid: UUID? = null
    private var lastOpponentActiveUuid: UUID? = null
    private var selectedPlayerUuid: UUID? = null
    private var selectedOpponentUuid: UUID? = null

    fun resolve(snapshot: CalcBattleSnapshot): CalcPreviewSelection {
        val battleChanged = battleId != snapshot.battleId
        val activeChanged = lastPlayerActiveUuid != snapshot.playerActiveUuid || lastOpponentActiveUuid != snapshot.opponentActiveUuid
        val playerSelectable = snapshot.playerTeam.map { it.uuid }.toSet()
        val opponentSelectable = snapshot.opponentTeam.map { it.uuid }.toSet()
        val selectedMissing = selectedPlayerUuid !in playerSelectable || selectedOpponentUuid !in opponentSelectable

        if (battleChanged || activeChanged || selectedMissing) {
            selectedPlayerUuid = snapshot.playerActiveUuid
            selectedOpponentUuid = snapshot.opponentActiveUuid
        }

        battleId = snapshot.battleId
        lastPlayerActiveUuid = snapshot.playerActiveUuid
        lastOpponentActiveUuid = snapshot.opponentActiveUuid

        if (selectedPlayerUuid !in playerSelectable) {
            selectedPlayerUuid = snapshot.playerActiveUuid
        }
        if (selectedOpponentUuid !in opponentSelectable) {
            selectedOpponentUuid = snapshot.opponentActiveUuid
        }

        return CalcPreviewSelection(selectedPlayerUuid, selectedOpponentUuid)
    }

    fun selectPlayer(uuid: UUID?) {
        selectedPlayerUuid = uuid
    }

    fun selectOpponent(uuid: UUID?) {
        selectedOpponentUuid = uuid
    }

    fun fingerprint(): String {
        return listOf(
            battleId?.toString().orEmpty(),
            selectedPlayerUuid?.toString().orEmpty(),
            selectedOpponentUuid?.toString().orEmpty()
        ).joinToString("::")
    }
}
