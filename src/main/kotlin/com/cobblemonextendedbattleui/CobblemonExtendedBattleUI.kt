package com.cobblemonextendedbattleui

import net.fabricmc.api.ModInitializer
import org.slf4j.LoggerFactory

object CobblemonExtendedBattleUI : ModInitializer {
    const val MOD_ID = "cobblemonextendedbattleui"
    val LOGGER = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        LOGGER.info("Cobblemon Extended Battle UI initialized")
    }
}
