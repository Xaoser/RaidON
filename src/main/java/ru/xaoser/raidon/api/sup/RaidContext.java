package ru.xaoser.raidon.api.sup;


public interface RaidContext {
    // заполняете тем, что нужно рантайму
    net.minecraft.server.level.ServerLevel level();
    net.minecraft.core.BlockPos center();
    float difficulty(); // уже нормализованная 0..10
    void broadcast(String msg);
    java.util.List<net.minecraft.server.level.ServerPlayer> playersInRaidZone();

    default void awardVictoryLoot() {
        // заглушка — вы реализуете
    }
}