package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import ru.xaoser.raidon.api.sup.RaidContext;

public class BasicRaidContext implements RaidContext {
    private final ServerLevel level;
    private final BlockPos center;
    private final float difficulty;
    private final double zoneRadius;

    public BasicRaidContext(ServerLevel level, BlockPos center, float difficulty, double zoneRadius) {
        this.level = level;
        this.center = center;
        this.difficulty = difficulty;
        this.zoneRadius = Math.max(16.0D, zoneRadius);
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
        Component component = Component.literal(msg);
        for (var player : playersInRaidZone()) {
            player.displayClientMessage(component, false);
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
}
