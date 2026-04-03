package com.deltacalc.ui;

import com.deltacalc.battle.ActiveBattleSnapshot;
import com.deltacalc.battle.ActiveMonSnapshot;
import com.deltacalc.battle.BattleSide;
import com.deltacalc.battle.BattleTracker;
import com.deltacalc.calc.DamageResult;
import com.deltacalc.calc.SimpleDamageCalculator;
import com.deltacalc.config.DeltaCalcConfig;
import com.deltacalc.data.SpeciesUsageEntry;
import com.deltacalc.data.UsageDatabase;
import com.deltacalc.inference.GuessState;
import com.deltacalc.inference.GuessValue;
import com.deltacalc.inference.GuessedBattleSet;
import com.deltacalc.inference.SetInferenceService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public final class BattleOverlayRenderer {
    private static final int PANEL_WIDTH = 208;

    private final DeltaCalcConfig config;
    private final BattleTracker battleTracker;
    private final UsageDatabase usageDatabase;
    private final SetInferenceService inferenceService;
    private final SimpleDamageCalculator damageCalculator;

    public BattleOverlayRenderer(
        DeltaCalcConfig config,
        BattleTracker battleTracker,
        UsageDatabase usageDatabase,
        SetInferenceService inferenceService,
        SimpleDamageCalculator damageCalculator
    ) {
        this.config = config;
        this.battleTracker = battleTracker;
        this.usageDatabase = usageDatabase;
        this.inferenceService = inferenceService;
        this.damageCalculator = damageCalculator;
    }

    public void render(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!config.overlayEnabled()) {
            return;
        }

        Optional<ActiveBattleSnapshot> optionalSnapshot = battleTracker.currentSnapshot();
        if (optionalSnapshot.isEmpty()) {
            return;
        }

        ActiveBattleSnapshot snapshot = optionalSnapshot.get();
        ActiveMonSnapshot opponent = snapshot.opponentActive();
        SpeciesUsageEntry usageEntry = usageDatabase.find(opponent.speciesId())
            .or(() -> usageDatabase.find(opponent.displayName()))
            .orElse(null);
        GuessedBattleSet guessedSet = inferenceService.infer(usageEntry, opponent);
        List<DamageResult> playerRows = buildPlayerRows(snapshot.playerActive(), opponent);
        List<DamageResult> opponentRows = buildOpponentRows(guessedSet, opponent, snapshot.playerActive());

        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        int x = client.getWindow().getScaledWidth() - PANEL_WIDTH - 12;
        int y = 16;
        int height = 170 + (playerRows.size() + opponentRows.size()) * 10;

        drawContext.fill(x, y, x + PANEL_WIDTH, y + height, 0xB0101010);
        drawContext.fill(x, y, x + PANEL_WIDTH, y + 14, 0xCC28333D);

        int lineY = y + 4;
        lineY = drawLine(drawContext, textRenderer, "DeltaCalc " + (snapshot.demo() ? "[DEMO]" : ""), x + 6, lineY, 0xFFE5F3FF);
        lineY += 8;
        lineY = drawLine(drawContext, textRenderer, opponent.displayName() + "  " + guessedSet.sourceConfidence(), x + 6, lineY, 0xFFFFDF8C);
        lineY = drawLine(drawContext, textRenderer, "Item: " + formatGuess(guessedSet.item()), x + 6, lineY, colorFor(guessedSet.item().state()));
        lineY = drawLine(drawContext, textRenderer, "Ability: " + formatGuess(guessedSet.ability()), x + 6, lineY, colorFor(guessedSet.ability().state()));
        lineY = drawLine(drawContext, textRenderer, "Spread: " + guessedSet.nature().value() + " " + formatSpread(guessedSet.evs()), x + 6, lineY, 0xFFC4D7E6);

        lineY += 6;
        lineY = drawLine(drawContext, textRenderer, "Your Moves -> Opponent", x + 6, lineY, 0xFFA6F4C5);
        DamageResult bestPlayerRow = playerRows.stream().max(Comparator.comparingDouble(DamageResult::maxPercent)).orElse(null);
        for (DamageResult result : playerRows) {
            int color = result.equals(bestPlayerRow) ? 0xFFFFFF99 : 0xFFEDEDED;
            lineY = drawLine(drawContext, textRenderer, formatDamageRow(result), x + 8, lineY, color);
        }

        lineY += 6;
        lineY = drawLine(drawContext, textRenderer, "Likely Opponent Moves -> You", x + 6, lineY, 0xFFFFB6B6);
        for (GuessValue<String> move : guessedSet.moves()) {
            int markerColor = colorFor(move.state());
            drawContext.fill(x + 6, lineY + 2, x + 9, lineY + 5, markerColor);
            lineY = drawLine(drawContext, textRenderer, move.value(), x + 12, lineY, markerColor);
            DamageResult row = opponentRows.stream().filter(result -> result.moveId().equalsIgnoreCase(move.value())).findFirst().orElse(null);
            if (row != null) {
                lineY = drawLine(drawContext, textRenderer, "  " + formatDamageRow(row), x + 12, lineY, 0xFFEDEDED);
            }
        }

        lineY += 6;
        if (usageEntry == null) {
            drawLine(drawContext, textRenderer, "No ranked usage entry for species.", x + 6, lineY, 0xFFFF9D9D);
        } else {
            drawLine(drawContext, textRenderer, guessedSet.notes(), x + 6, lineY, 0xFF9FB6C8);
        }
    }

    private List<DamageResult> buildPlayerRows(ActiveMonSnapshot player, ActiveMonSnapshot opponent) {
        List<DamageResult> rows = new ArrayList<>();
        for (String move : player.moveList()) {
            rows.add(damageCalculator.calculate(BattleSide.PLAYER, player, opponent, move));
        }
        return rows;
    }

    private List<DamageResult> buildOpponentRows(GuessedBattleSet guessedSet, ActiveMonSnapshot opponent, ActiveMonSnapshot player) {
        List<DamageResult> rows = new ArrayList<>();
        for (GuessValue<String> move : guessedSet.moves()) {
            rows.add(damageCalculator.calculate(BattleSide.OPPONENT, opponent, player, move.value()));
        }
        return rows;
    }

    private int drawLine(DrawContext drawContext, TextRenderer textRenderer, String text, int x, int y, int color) {
        drawContext.drawText(textRenderer, text, x, y, color, true);
        return y + 10;
    }

    private String formatGuess(GuessValue<String> guess) {
        return switch (guess.state()) {
            case REVEALED, CONFIRMED -> guess.value() + " [R]";
            case GUESSED -> guess.value() + " [G]";
            case UNKNOWN -> "Unknown";
        };
    }

    private String formatSpread(com.deltacalc.battle.StatBlock evs) {
        return evs.hp() + "/" + evs.atk() + "/" + evs.def() + "/" + evs.spa() + "/" + evs.spd() + "/" + evs.spe();
    }

    private String formatDamageRow(DamageResult result) {
        return result.moveId() + " " + result.minPercent() + "-" + result.maxPercent() + "% " + result.koLabel();
    }

    private int colorFor(GuessState state) {
        return switch (state) {
            case REVEALED, CONFIRMED -> 0xFF8FE6A0;
            case GUESSED -> 0xFFF4D27A;
            case UNKNOWN -> 0xFFB0B8C0;
        };
    }
}
