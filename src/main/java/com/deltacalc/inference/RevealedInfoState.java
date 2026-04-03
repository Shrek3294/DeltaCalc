package com.deltacalc.inference;

import java.util.LinkedHashSet;
import java.util.Set;

public final class RevealedInfoState {
    private final Set<String> revealedMoves = new LinkedHashSet<>();
    private String revealedItem;
    private String revealedAbility;
    private int lastRevealTurn;

    public Set<String> revealedMoves() {
        return revealedMoves;
    }

    public String revealedItem() {
        return revealedItem;
    }

    public String revealedAbility() {
        return revealedAbility;
    }

    public int lastRevealTurn() {
        return lastRevealTurn;
    }

    public void revealMove(String moveName, int turn) {
        if (moveName != null && !moveName.isBlank()) {
            revealedMoves.add(moveName);
            lastRevealTurn = turn;
        }
    }

    public void revealItem(String itemName, int turn) {
        if (itemName != null && !itemName.isBlank()) {
            revealedItem = itemName;
            lastRevealTurn = turn;
        }
    }

    public void revealAbility(String abilityName, int turn) {
        if (abilityName != null && !abilityName.isBlank()) {
            revealedAbility = abilityName;
            lastRevealTurn = turn;
        }
    }
}

