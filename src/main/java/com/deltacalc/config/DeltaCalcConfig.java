package com.deltacalc.config;

public final class DeltaCalcConfig {
    private boolean overlayEnabled = true;
    private boolean demoBattleForced;

    public boolean overlayEnabled() {
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
    }

    public boolean demoBattleForced() {
        return demoBattleForced;
    }

    public void setDemoBattleForced(boolean demoBattleForced) {
        this.demoBattleForced = demoBattleForced;
    }
}

