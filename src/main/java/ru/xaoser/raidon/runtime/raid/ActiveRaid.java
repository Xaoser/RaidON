package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidWave;
import ru.xaoser.raidon.api.sup.MobEntry;
import ru.xaoser.raidon.api.sup.RaidRuntime;

import java.util.*;

class ActiveRaid implements RaidRuntime {
    private final Raid raid;
    private final ServerLevel level;
    private final BlockPos center;
    private final RaidSpawnSettings spawnSettings;
    private final BasicRaidContext context;

    private int currentWaveIndex = -1;
    private final Map<Integer, List<UUID>> waveMobs = new HashMap<>();
    private boolean completed = false;

    ActiveRaid(Raid raid, ServerLevel level, BlockPos center, RaidSpawnSettings spawnSettings) {
        this.raid = raid;
        this.level = level;
        this.center = center;
        this.spawnSettings = spawnSettings;
        this.context = new BasicRaidContext(level, center, raid.difficulty());
    }

    void tick() {
        if (completed) return;

        if (currentWaveIndex < 0) {
            startWave(0);
            return;
        }

        RaidWave wave = raid.waves().get(currentWaveIndex);
        if (wave.completeCondition().isComplete(this, context)) {
            wave.onWaveEnd().run(context);
            int next = currentWaveIndex + 1;
            if (next >= raid.waves().size()) {
                completed = true;
                raid.endAction().run(context);
            } else {
                startWave(next);
            }
        }
    }

    boolean isCompleted() {
        return completed;
    }

    @Override
    public int aliveMobsInCurrentWave() {
        List<UUID> ids = waveMobs.getOrDefault(currentWaveIndex, List.of());
        int alive = 0;
        for (UUID id : ids) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    private void startWave(int waveIndex) {
        currentWaveIndex = waveIndex;
        RaidWave wave = raid.waves().get(waveIndex);
        wave.onWaveStart().run(context);
        spawnWaveMobs(wave);
    }

    private void spawnWaveMobs(RaidWave wave) {
        List<UUID> spawned = new ArrayList<>();
        Random random = level.getRandom();

        for (MobEntry entry : wave.mobs()) {
            EntityType<? extends Mob> type = entry.type();
            for (int i = 0; i < entry.count(); i++) {
                BlockPos pos = findSpawnPos(random, wave.spawnRadius());
                if (pos == null) {
                    continue;
                }
                Mob mob = type.create(level);
                if (mob == null) {
                    continue;
                }
                mob.moveTo(pos, random.nextFloat() * 360.0F, 0.0F);
                level.addFreshEntity(mob);
                spawned.add(mob.getUUID());
            }
        }

        waveMobs.put(wave.index(), spawned);
    }

    private BlockPos findSpawnPos(Random random, int waveRadius) {
        int minRadius = waveRadius > 0 ? waveRadius : spawnSettings.minRadius();
        int maxRadius = waveRadius > 0 ? waveRadius : spawnSettings.maxRadius();
        for (int attempt = 0; attempt < spawnSettings.attemptsPerMob(); attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockPos candidate = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));

            if (spawnSettings.requireGround()) {
                BlockState stateBelow = level.getBlockState(candidate.below());
                if (!stateBelow.getMaterial().isSolid()) {
                    continue;
                }
            }

            if (spawnSettings.avoidWater() && level.getFluidState(candidate).isSource()) {
                continue;
            }

            return candidate;
        }
        return null;
    }

    void notifyPlayers(String msg) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), false);
        }
    }
}
