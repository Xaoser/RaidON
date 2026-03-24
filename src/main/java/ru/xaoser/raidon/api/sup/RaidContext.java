package ru.xaoser.raidon.api.sup;

public interface RaidContext {
    /** Returns the server level where the raid runs. */
    net.minecraft.server.level.ServerLevel level();

    /** Returns the main raid center. */
    net.minecraft.core.BlockPos center();

    /** Returns normalized difficulty in the 0..10 range. */
    float difficulty();

    /** Broadcasts a message to raid players. */
    void broadcast(String msg);

    /** Returns players currently inside the raid area. */
    java.util.List<net.minecraft.server.level.ServerPlayer> playersInRaidZone();

    /** Override to grant custom rewards. */
    default void awardVictoryLoot() {
    }
}
