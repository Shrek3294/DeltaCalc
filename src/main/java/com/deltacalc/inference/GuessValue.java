package com.deltacalc.inference;

public final class GuessValue<T> {
    private final T value;
    private final GuessState state;
    private final ConfidenceBand confidence;

    public GuessValue(T value, GuessState state, ConfidenceBand confidence) {
        this.value = value;
        this.state = state;
        this.confidence = confidence;
    }

    public T value() {
        return value;
    }

    public GuessState state() {
        return state;
    }

    public ConfidenceBand confidence() {
        return confidence;
    }

    public GuessValue<T> withState(GuessState nextState) {
        return new GuessValue<>(value, nextState, confidence);
    }

    public GuessValue<T> withValue(T nextValue, GuessState nextState, ConfidenceBand nextConfidence) {
        return new GuessValue<>(nextValue, nextState, nextConfidence);
    }
}

