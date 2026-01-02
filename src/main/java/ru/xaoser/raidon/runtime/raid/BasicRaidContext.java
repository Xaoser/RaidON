package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import ru.xaoser.raidon.api.sup.RaidContext;

public class BasicRaidContext implements RaidContext {
    private final ServerLevel level;
    private final BlockPos center;
    private final float difficulty;

    public BasicRaidContext(ServerLevel level, BlockPos center, float difficulty) {
        this.level = level;
        this.center = center;
        this.difficulty = difficulty;
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
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }
}
