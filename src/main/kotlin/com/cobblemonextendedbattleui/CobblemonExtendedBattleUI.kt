package com.cobblemonextendedbattleui

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CobblemonExtendedBattleUI : ModInitializer {
    const val MOD_ID = "cobblemonextendedbattleui"
    const val DISPLAY_NAME = "DeltaCalc"
    const val MODRINTH_PROJECT_SLUG = ""
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("$DISPLAY_NAME initialized")
        BoostTraceLog.append("session start")
    }
}
