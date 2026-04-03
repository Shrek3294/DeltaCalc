package com.deltacalc.inference;

import com.deltacalc.battle.ActiveMonSnapshot;
import com.deltacalc.battle.StatBlock;
import com.deltacalc.data.EvSpreadUsage;
import com.deltacalc.data.SpeciesUsageEntry;
import com.deltacalc.data.UsageOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SetInferenceService {
    public GuessedBattleSet infer(SpeciesUsageEntry entry, ActiveMonSnapshot opponentSnapshot) {
        if (entry == null) {
            return new GuessedBattleSet(
                opponentSnapshot.speciesId(),
                opponentSnapshot.level(),
                new GuessValue<>("Unknown", GuessState.UNKNOWN, ConfidenceBand.LOW),
                new GuessValue<>("Unknown", GuessState.UNKNOWN, ConfidenceBand.LOW),
                new GuessValue<>("Unknown", GuessState.UNKNOWN, ConfidenceBand.LOW),
                StatBlock.EMPTY,
                List.of(),
                ConfidenceBand.LOW,
                "No ranked usage entry available."
            );
        }

        EvSpreadUsage topSpread = entry.spreads().isEmpty() ? new EvSpreadUsage("Unknown", StatBlock.EMPTY, 0.0) : entry.spreads().getFirst();
        List<GuessValue<String>> topMoves = buildMoveGuesses(entry, opponentSnapshot.types());
        GuessedBattleSet set = new GuessedBattleSet(
            entry.speciesId(),
            opponentSnapshot.level(),
            guessTop(entry.items(), "Unknown item"),
            guessTop(entry.abilities(), "Unknown ability"),
            new GuessValue<>(topSpread.nature(), GuessState.GUESSED, confidenceFor(topSpread.usagePercent())),
            topSpread.evs(),
            topMoves,
            confidenceFor(entry.usagePercent()),
            "Synthesized from marginal ranked usage data."
        );

        if (opponentSnapshot.item() != null && opponentSnapshot.item().value() != null && opponentSnapshot.item().state() == GuessState.REVEALED) {
            set.overwriteItem(opponentSnapshot.item().value(), ConfidenceBand.HIGH);
        }
        if (opponentSnapshot.ability() != null && opponentSnapshot.ability().value() != null && opponentSnapshot.ability().state() == GuessState.REVEALED) {
            set.overwriteAbility(opponentSnapshot.ability().value(), ConfidenceBand.HIGH);
        }
        if (opponentSnapshot.knownMoves() != null) {
            for (String move : opponentSnapshot.knownMoves()) {
                set.replaceMove(move, ConfidenceBand.HIGH);
            }
        }
        return set;
    }

    private GuessValue<String> guessTop(List<UsageOption> options, String fallback) {
        if (options == null || options.isEmpty()) {
            return new GuessValue<>(fallback, GuessState.UNKNOWN, ConfidenceBand.LOW);
        }
        UsageOption top = options.getFirst();
        return new GuessValue<>(top.displayName(), GuessState.GUESSED, confidenceFor(top.usagePercent()));
    }

    private List<GuessValue<String>> buildMoveGuesses(SpeciesUsageEntry entry, List<String> types) {
        List<UsageOption> sorted = new ArrayList<>(entry.moves());
        sorted.sort(Comparator.comparingDouble(UsageOption::usagePercent).reversed());

        Set<String> chosenKeys = new LinkedHashSet<>();
        List<GuessValue<String>> results = new ArrayList<>();
        String preferredType = types == null || types.isEmpty() ? "" : types.getFirst().toLowerCase(Locale.ROOT);

        for (UsageOption option : sorted) {
            if ("Other".equalsIgnoreCase(option.displayName())) {
                continue;
            }
            String key = option.displayName().toLowerCase(Locale.ROOT);
            if (!chosenKeys.add(key)) {
                continue;
            }

            if (results.size() < 4) {
                results.add(new GuessValue<>(option.displayName(), GuessState.GUESSED, confidenceFor(option.usagePercent())));
            }
            if (results.size() == 4) {
                break;
            }
        }

        if (!preferredType.isBlank() && results.stream().noneMatch(move -> move.value().toLowerCase(Locale.ROOT).contains(preferredType))) {
            sorted.stream()
                .filter(option -> preferredType.equalsIgnoreCase(option.type()))
                .findFirst()
                .ifPresent(option -> {
                    if (results.size() == 4) {
                        results.set(results.size() - 1, new GuessValue<>(option.displayName(), GuessState.GUESSED, confidenceFor(option.usagePercent())));
                    } else {
                        results.add(new GuessValue<>(option.displayName(), GuessState.GUESSED, confidenceFor(option.usagePercent())));
                    }
                });
        }

        return results;
    }

    private ConfidenceBand confidenceFor(double usagePercent) {
        if (usagePercent >= 50.0) {
            return ConfidenceBand.HIGH;
        }
        if (usagePercent >= 12.0) {
            return ConfidenceBand.MEDIUM;
        }
        return ConfidenceBand.LOW;
    }
}
