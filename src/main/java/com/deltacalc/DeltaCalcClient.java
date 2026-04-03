package com.deltacalc;

import com.deltacalc.battle.BattleTracker;
import com.deltacalc.calc.SimpleDamageCalculator;
import com.deltacalc.config.DeltaCalcConfig;
import com.deltacalc.data.UsageDataLoader;
import com.deltacalc.data.UsageDatabase;
import com.deltacalc.inference.SetInferenceService;
import com.deltacalc.ui.BattleOverlayRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeltaCalcClient implements ClientModInitializer {
    public static final String MOD_ID = "deltacalc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final DeltaCalcConfig config = new DeltaCalcConfig();
    private final UsageDatabase usageDatabase = UsageDataLoader.loadBundled("/data/deltacalc/usage/season-6-mid-1000.sample.json", LOGGER);
    private final SetInferenceService inferenceService = new SetInferenceService();
    private final SimpleDamageCalculator damageCalculator = new SimpleDamageCalculator();
    private final BattleTracker battleTracker = new BattleTracker(LOGGER, config);
    private final BattleOverlayRenderer overlayRenderer = new BattleOverlayRenderer(config, battleTracker, usageDatabase, inferenceService, damageCalculator);

    private KeyBinding overlayToggleKey;
    private KeyBinding demoBattleToggleKey;
    private KeyBinding demoRevealKey;

    @Override
    public void onInitializeClient() {
        overlayToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.deltacalc.toggle_overlay",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            KeyBinding.Category.MISC
        ));
        demoBattleToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.deltacalc.toggle_demo",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            KeyBinding.Category.MISC
        ));
        demoRevealKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.deltacalc.cycle_demo_reveal",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            KeyBinding.Category.MISC
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (overlayToggleKey.wasPressed()) {
                config.setOverlayEnabled(!config.overlayEnabled());
                LOGGER.info("DeltaCalc overlay {}", config.overlayEnabled() ? "enabled" : "disabled");
            }
            while (demoBattleToggleKey.wasPressed()) {
                battleTracker.toggleDemoBattle();
            }
            while (demoRevealKey.wasPressed()) {
                battleTracker.cycleDemoReveal();
            }

            battleTracker.tick(client);
        });

        HudRenderCallback.EVENT.register(overlayRenderer::render);
        LOGGER.info("DeltaCalc initialized with {} bundled usage entries", usageDatabase.size());
    }
}
