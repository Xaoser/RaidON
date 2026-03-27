package ru.xaoser.raidon.api.sup;

@FunctionalInterface
public interface RaidAction {
    void run(RaidContext ctx);
}