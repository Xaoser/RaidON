package ru.xaoser.raidon.api.sup;

@FunctionalInterface
public interface WaveCompleteCondition {
    boolean isComplete(RaidRuntime runtime, RaidContext ctx);
}