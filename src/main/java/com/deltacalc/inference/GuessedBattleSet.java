package com.deltacalc.inference;

import com.deltacalc.battle.StatBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GuessedBattleSet {
    private final String speciesId;
    private final int level;
    private GuessValue<String> item;
    private GuessValue<String> ability;
    private GuessValue<String> nature;
    private StatBlock evs;
    private final List<GuessValue<String>> moves;
    private ConfidenceBand sourceConfidence;
    private String notes;

    public GuessedBattleSet(
        String speciesId,
        int level,
        GuessValue<String> item,
        GuessValue<String> ability,
        GuessValue<String> nature,
        StatBlock evs,
        List<GuessValue<String>> moves,
        ConfidenceBand sourceConfidence,
        String notes
    ) {
        this.speciesId = speciesId;
        this.level = level;
        this.item = item;
        this.ability = ability;
        this.nature = nature;
        this.evs = evs;
        this.moves = new ArrayList<>(moves);
        this.sourceConfidence = sourceConfidence;
        this.notes = notes;
    }

    public String speciesId() {
        return speciesId;
    }

    public int level() {
        return level;
    }

    public GuessValue<String> item() {
        return item;
    }

    public GuessValue<String> ability() {
        return ability;
    }

    public GuessValue<String> nature() {
        return nature;
    }

    public StatBlock evs() {
        return evs;
    }

    public List<GuessValue<String>> moves() {
        return Collections.unmodifiableList(moves);
    }

    public ConfidenceBand sourceConfidence() {
        return sourceConfidence;
    }

    public String notes() {
        return notes;
    }

    public void overwriteItem(String itemName, ConfidenceBand confidence) {
        item = item.withValue(itemName, GuessState.REVEALED, confidence);
    }

    public void overwriteAbility(String abilityName, ConfidenceBand confidence) {
        ability = ability.withValue(abilityName, GuessState.REVEALED, confidence);
    }

    public void replaceMove(String moveName, ConfidenceBand confidence) {
        for (int index = 0; index < moves.size(); index++) {
            GuessValue<String> move = moves.get(index);
            if (move.value().equalsIgnoreCase(moveName)) {
                moves.set(index, move.withValue(moveName, GuessState.REVEALED, confidence));
                return;
            }
        }

        int replacementIndex = -1;
        for (int index = moves.size() - 1; index >= 0; index--) {
            GuessValue<String> move = moves.get(index);
            if (move.state() != GuessState.REVEALED && move.state() != GuessState.CONFIRMED) {
                replacementIndex = index;
                break;
            }
        }

        if (replacementIndex == -1 && !moves.isEmpty()) {
            replacementIndex = moves.size() - 1;
        }

        if (replacementIndex >= 0) {
            moves.set(replacementIndex, new GuessValue<>(moveName, GuessState.REVEALED, confidence));
        } else {
            moves.add(new GuessValue<>(moveName, GuessState.REVEALED, confidence));
        }
    }
}

