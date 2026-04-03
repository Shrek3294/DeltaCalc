package com.deltacalc.battle;

import com.deltacalc.config.DeltaCalcConfig;
import com.deltacalc.inference.ConfidenceBand;
import com.deltacalc.inference.GuessState;
import com.deltacalc.inference.GuessValue;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.slf4j.Logger;

public final class BattleTracker {
    private final Logger logger;
    private final DeltaCalcConfig config;
    private String lastScreenName = "";
    private ActiveBattleSnapshot currentSnapshot;
    private int demoRevealStep;

    public BattleTracker(Logger logger, DeltaCalcConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public Optional<ActiveBattleSnapshot> currentSnapshot() {
        return Optional.ofNullable(currentSnapshot);
    }

    public void tick(MinecraftClient client) {
        if (client == null) {
            return;
        }

        Screen screen = client.currentScreen;
        String screenName = screen == null ? "<none>" : screen.getClass().getName();
        if (!screenName.equals(lastScreenName)) {
            lastScreenName = screenName;
            logger.info("DeltaCalc screen changed to {}", screenName);
        }

        boolean battleActive = config.demoBattleForced() || isLikelyBattleScreen(screen);
        if (battleActive && currentSnapshot == null) {
            currentSnapshot = createDemoSnapshot();
            logger.info("DeltaCalc entered battle context using {}", config.demoBattleForced() ? "forced demo snapshot" : "screen heuristic");
            return;
        }

        if (!battleActive && currentSnapshot != null && !config.demoBattleForced()) {
            logger.info("DeltaCalc left battle context");
            currentSnapshot = null;
            demoRevealStep = 0;
        }
    }

    public void toggleDemoBattle() {
        config.setDemoBattleForced(!config.demoBattleForced());
        if (!config.demoBattleForced()) {
            currentSnapshot = null;
            demoRevealStep = 0;
        }
        logger.info("DeltaCalc demo battle {}", config.demoBattleForced() ? "enabled" : "disabled");
    }

    public void cycleDemoReveal() {
        if (currentSnapshot == null) {
            currentSnapshot = createDemoSnapshot();
        }

        demoRevealStep = (demoRevealStep + 1) % 4;
        ActiveMonSnapshot opponent = currentSnapshot.opponentActive();

        ActiveMonSnapshot updatedOpponent = switch (demoRevealStep) {
            case 1 -> withOpponentReveal(opponent, "Giga Drain", null, null);
            case 2 -> withOpponentReveal(opponent, "Giga Drain", "Heavy Duty Boots", null);
            case 3 -> withOpponentReveal(opponent, "Giga Drain", "Heavy Duty Boots", "Flame Body");
            default -> resetOpponent(opponent);
        };

        currentSnapshot = new ActiveBattleSnapshot(
            currentSnapshot.battleId(),
            currentSnapshot.turn() + 1,
            currentSnapshot.demo(),
            currentSnapshot.playerActive(),
            updatedOpponent,
            currentSnapshot.weather(),
            currentSnapshot.terrain(),
            currentSnapshot.sideConditions(),
            currentSnapshot.globalConditions()
        );

        logger.info("DeltaCalc cycled demo reveal state to step {}", demoRevealStep);
    }

    private boolean isLikelyBattleScreen(Screen screen) {
        if (screen == null) {
            return false;
        }

        String className = screen.getClass().getName().toLowerCase(Locale.ROOT);
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString().toLowerCase(Locale.ROOT);
        return className.contains("battle")
            || className.contains("cobblemon")
            || title.contains("battle");
    }

    private ActiveMonSnapshot withOpponentReveal(ActiveMonSnapshot opponent, String move, String item, String ability) {
        List<String> knownMoves = move == null ? opponent.knownMoves() : List.of(move);
        GuessValue<String> revealedItem = item == null
            ? new GuessValue<>("Unknown", GuessState.GUESSED, ConfidenceBand.LOW)
            : new GuessValue<>(item, GuessState.REVEALED, ConfidenceBand.HIGH);
        GuessValue<String> revealedAbility = ability == null
            ? new GuessValue<>("Unknown", GuessState.GUESSED, ConfidenceBand.LOW)
            : new GuessValue<>(ability, GuessState.REVEALED, ConfidenceBand.HIGH);

        return new ActiveMonSnapshot(
            opponent.speciesId(),
            opponent.displayName(),
            opponent.level(),
            opponent.types(),
            opponent.currentHp(),
            opponent.maxHp(),
            opponent.status(),
            opponent.statStages(),
            revealedAbility,
            revealedItem,
            knownMoves,
            opponent.moveList(),
            opponent.baseStats(),
            opponent.effectiveStats()
        );
    }

    private ActiveMonSnapshot resetOpponent(ActiveMonSnapshot opponent) {
        return new ActiveMonSnapshot(
            opponent.speciesId(),
            opponent.displayName(),
            opponent.level(),
            opponent.types(),
            opponent.currentHp(),
            opponent.maxHp(),
            opponent.status(),
            opponent.statStages(),
            new GuessValue<>("Unknown", GuessState.GUESSED, ConfidenceBand.LOW),
            new GuessValue<>("Unknown", GuessState.GUESSED, ConfidenceBand.LOW),
            List.of(),
            opponent.moveList(),
            opponent.baseStats(),
            opponent.effectiveStats()
        );
    }

    private ActiveBattleSnapshot createDemoSnapshot() {
        ActiveMonSnapshot player = new ActiveMonSnapshot(
            "garchomp",
            "Garchomp",
            100,
            List.of("dragon", "ground"),
            333,
            333,
            "healthy",
            StatStages.NEUTRAL,
            new GuessValue<>("Rough Skin", GuessState.CONFIRMED, ConfidenceBand.HIGH),
            new GuessValue<>("Life Orb", GuessState.CONFIRMED, ConfidenceBand.HIGH),
            List.of("Earthquake", "Dragon Claw", "Fire Fang", "Swords Dance"),
            List.of("Earthquake", "Dragon Claw", "Fire Fang", "Swords Dance"),
            new StatBlock(108, 130, 95, 80, 85, 102),
            new StatBlock(357, 394, 226, 196, 206, 303)
        );
        ActiveMonSnapshot opponent = new ActiveMonSnapshot(
            "volcarona",
            "Volcarona",
            100,
            List.of("bug", "fire"),
            311,
            311,
            "healthy",
            StatStages.NEUTRAL,
            new GuessValue<>("Unknown", GuessState.GUESSED, ConfidenceBand.LOW),
            new GuessValue<>("Unknown", GuessState.GUESSED, ConfidenceBand.LOW),
            List.of(),
            List.of(),
            new StatBlock(85, 60, 65, 135, 105, 100),
            new StatBlock(311, 156, 166, 369, 266, 328)
        );
        return new ActiveBattleSnapshot(
            "demo-" + UUID.randomUUID(),
            1,
            true,
            player,
            opponent,
            "none",
            "none",
            List.of(),
            List.of()
        );
    }
}

