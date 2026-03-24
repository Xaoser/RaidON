package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import ru.xaoser.raidon.api.sup.RaidContext;
import ru.xaoser.raidon.runtime.text.RaidTextFormatter;

public class BasicRaidContext implements RaidContext {
    @FunctionalInterface
    public interface LoopSoundController {
        void set(ResourceLocation soundId, SoundSource source, float volume, float pitch, int repeatTicks);
    }

    private static final int DEFAULT_FADE_IN = 10;
    private static final int DEFAULT_STAY = 70;
    private static final int DEFAULT_FADE_OUT = 20;

    private final ServerLevel level;
    private final BlockPos center;
    private final float difficulty;
    private final double zoneRadius;
    private final LoopSoundController loopSoundController;

    public BasicRaidContext(ServerLevel level, BlockPos center, float difficulty, double zoneRadius) {
        this(level, center, difficulty, zoneRadius, null);
    }

    public BasicRaidContext(ServerLevel level, BlockPos center, float difficulty, double zoneRadius, LoopSoundController loopSoundController) {
        this.level = level;
        this.center = center;
        this.difficulty = difficulty;
        this.zoneRadius = Math.max(16.0D, zoneRadius);
        this.loopSoundController = loopSoundController;
    }

    @Override
    public ServerLevel level() {
        return level;
    }

    @Override
    public BlockPos center() {
        return center;
    }

    @Override
    public float difficulty() {
        return difficulty;
    }

    @Override
    public void broadcast(String msg) {
        Component component = RaidTextFormatter.parse(msg);
        for (var player : playersInRaidZone()) {
            player.sendSystemMessage(component);
        }
    }

    @Override
    public void sendActionBar(String msg) {
        Component component = RaidTextFormatter.parse(msg);
        for (var player : playersInRaidZone()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(component));
        }
    }

    @Override
    public void sendTitle(String msg, int fadeIn, int stay, int fadeOut) {
        Component component = RaidTextFormatter.parse(msg);
        for (var player : playersInRaidZone()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(resolveTiming(fadeIn, DEFAULT_FADE_IN),
                    resolveTiming(stay, DEFAULT_STAY), resolveTiming(fadeOut, DEFAULT_FADE_OUT)));
            player.connection.send(new ClientboundSetTitleTextPacket(component));
        }
    }

    @Override
    public void sendSubtitle(String msg, int fadeIn, int stay, int fadeOut) {
        Component component = RaidTextFormatter.parse(msg);
        for (var player : playersInRaidZone()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(resolveTiming(fadeIn, DEFAULT_FADE_IN),
                    resolveTiming(stay, DEFAULT_STAY), resolveTiming(fadeOut, DEFAULT_FADE_OUT)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(component));
        }
    }

    @Override
    public void setRaidLoopSound(ResourceLocation soundId, SoundSource source, float volume, float pitch, int repeatTicks) {
        if (loopSoundController != null) {
            loopSoundController.set(soundId, source, volume, pitch, repeatTicks);
        }
    }


    @Override
    public java.util.List<net.minecraft.server.level.ServerPlayer> playersInRaidZone() {
        double maxDistSq = zoneRadius * zoneRadius;
        java.util.List<net.minecraft.server.level.ServerPlayer> players = new java.util.ArrayList<>();
        for (var player : level.players()) {
            if (player.distanceToSqr(center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D) <= maxDistSq) {
                players.add(player);
            }
        }
        return players;
    }

    private static int resolveTiming(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
