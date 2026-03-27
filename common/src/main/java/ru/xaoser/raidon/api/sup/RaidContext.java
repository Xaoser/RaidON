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

    /** Sends text above the hotbar. */
    void sendActionBar(String msg);

    /** Sends the main center-screen title. */
    void sendTitle(String msg, int fadeIn, int stay, int fadeOut);

    /** Sends the smaller center-screen subtitle. */
    void sendSubtitle(String msg, int fadeIn, int stay, int fadeOut);

    /** Starts or replaces the looping raid sound. */
    void setRaidLoopSound(net.minecraft.resources.ResourceLocation soundId,
                          net.minecraft.sounds.SoundSource source,
                          float volume,
                          float pitch,
                          int repeatTicks);

    /** Returns players currently inside the raid area. */
    java.util.List<net.minecraft.server.level.ServerPlayer> playersInRaidZone();

    /** Override to grant custom rewards. */
    default void awardVictoryLoot() {
    }
}
