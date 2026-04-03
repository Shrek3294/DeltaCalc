package com.cobblemonextendedbattleui

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW

object CobblemonExtendedBattleUIClient : ClientModInitializer {

    lateinit var togglePanelKey: KeyBinding
        private set
    lateinit var increaseFontKey: KeyBinding
        private set
    lateinit var decreaseFontKey: KeyBinding
        private set

    override fun onInitializeClient() {
        CobblemonExtendedBattleUI.LOGGER.info("Cobblemon Extended Battle UI Client initializing...")

        BattleInfoPanel.initialize()
        registerKeybindings()
        registerHudRenderer()
        UpdateChecker.checkForUpdates()

        CobblemonExtendedBattleUI.LOGGER.info("Cobblemon Extended Battle UI Client initialized!")
    }

    private fun registerHudRenderer() {
        HudRenderCallback.EVENT.register { context, _ ->
            BattleInfoRenderer.render(context)
        }
    }

    private fun registerKeybindings() {
        togglePanelKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.cobblemonextendedbattleui.toggle_panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                "category.cobblemonextendedbattleui"
            )
        )

        increaseFontKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.cobblemonextendedbattleui.increase_font",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_BRACKET,
                "category.cobblemonextendedbattleui"
            )
        )

        decreaseFontKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding(
                "key.cobblemonextendedbattleui.decrease_font",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_BRACKET,
                "category.cobblemonextendedbattleui"
            )
        )
    }
}
