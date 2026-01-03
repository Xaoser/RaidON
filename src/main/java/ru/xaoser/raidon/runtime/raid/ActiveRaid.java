package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidWave;
import ru.xaoser.raidon.api.sup.MobEntry;
import ru.xaoser.raidon.api.sup.RaidRuntime;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.raid.ai.MobAiHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ActiveRaid implements RaidRuntime {
    private final Raid raid;
    private final ServerLevel level;
    private final BlockPos center;
    private final RaidSpawnSettings spawnSettings;
    private final BasicRaidContext context;

    private int currentWaveIndex = -1;
    private final Map<Integer, List<UUID>> waveMobs = new HashMap<>();
    private final Map<Integer, Integer> waveTotals = new HashMap<>();
    private int lastSentAlive = -1;
    private int lastSentWave = -2;
    private boolean completed = false;

    ActiveRaid(Raid raid, ServerLevel level, BlockPos center, RaidSpawnSettings spawnSettings) {
        this.raid = raid;
        this.level = level;
        this.center = center;
        this.spawnSettings = RaidSpawnSettings.sanitized(spawnSettings);
        this.context = new BasicRaidContext(level, center, raid.difficulty());
    }

    void tick() {
        if (completed) return;

        if (currentWaveIndex < 0) {
            startWave(0);
            sendProgressIfNeeded();
            return;
        }

        RaidWave wave = raid.waves().get(currentWaveIndex);
        if (wave.completeCondition().isComplete(this, context)) {
            wave.onWaveEnd().run(context);
            int next = currentWaveIndex + 1;
            if (next >= raid.waves().size()) {
                completed = true;
                raid.endAction().run(context);
                sendProgressIfNeeded();
            } else {
                startWave(next);
            }
        }

        sendProgressIfNeeded();
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
        sendProgressIfNeeded();
    }

    private void spawnWaveMobs(RaidWave wave) {
        List<UUID> spawned = new ArrayList<>();
        RandomSource random = level.getRandom();
        int total = 0;

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
                mob.setPersistenceRequired();
                MobAiHelper.applyBehavior(mob, entry.behavior());
                if (level.addFreshEntity(mob)) {
                    spawned.add(mob.getUUID());
                    total++;
                }
            }
        }

        waveMobs.put(wave.index(), spawned);
        waveTotals.put(wave.index(), total);
    }

    private BlockPos findSpawnPos(RandomSource random, int waveRadius) {
        int minRadius = waveRadius > 0 ? waveRadius : spawnSettings.minRadius();
        int maxRadius = waveRadius > 0 ? waveRadius : spawnSettings.maxRadius();
        int attempts = Math.max(1, spawnSettings.attemptsPerMob());
        for (int attempt = 0; attempt < attempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = minRadius + random.nextInt(Math.max(1, maxRadius - minRadius + 1));
            int dx = (int) Math.round(Math.cos(angle) * radius);
            int dz = (int) Math.round(Math.sin(angle) * radius);
            BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(dx, 0, dz));

            if (spawnSettings.requireGround()) {
                BlockState stateBelow = level.getBlockState(candidate.below());
                if (!stateBelow.isSolidRender(level, candidate.below())) {
                    continue;
                }
            }

            if (spawnSettings.avoidWater() && level.getFluidState(candidate).isSource()) {
                continue;
            }

            return candidate;
        }
        // fallback to raid center if no suitable position was found
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center);
    }

    void notifyPlayers(String msg) {
        for (ServerPlayer player : level.players()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal(msg), false);
        }
    }

    private void sendProgressIfNeeded() {
        int alive = aliveMobsInCurrentWave();
        if (alive != lastSentAlive || currentWaveIndex != lastSentWave || completed) {
            RaidManager.sendProgress(this, completed);
            lastSentAlive = alive;
            lastSentWave = currentWaveIndex;
        }
    }

    RaidProgressS2CPacket.Progress payload() {
        int totalWaves = raid.waves().size();
        int waveTotal = waveTotals.getOrDefault(currentWaveIndex, 0);
        return new RaidProgressS2CPacket.Progress(
                raid.id(),
                currentWaveIndex,
                totalWaves,
                aliveMobsInCurrentWave(),
                waveTotal
        );
    }

    ResourceLocation raidId() {
        return raid.id();
    }

    BlockPos center() {
        return center;
    }

    ServerLevel level() {
        return level;
    }

    void forceComplete() {
        if (completed) return;
        completed = true;
        despawnTracked();
        raid.endAction().run(context);
        sendProgressIfNeeded();
    }

    int currentWaveZeroBased() {
        return currentWaveIndex;
    }

    int totalWaves() {
        return raid.waves().size();
    }

    int totalMobsInCurrentWave() {
        int cached = waveTotals.getOrDefault(currentWaveIndex, -1);
        if (cached >= 0) return cached;
        if (currentWaveIndex < 0 || currentWaveIndex >= raid.waves().size()) return 0;
        int computed = raid.waves().get(currentWaveIndex).mobs().stream()
                .mapToInt(MobEntry::count)
                .sum();
        waveTotals.put(currentWaveIndex, computed);
        return computed;
    }

    private void despawnTracked() {
        for (List<UUID> ids : waveMobs.values()) {
            for (UUID id : ids) {
                Entity entity = level.getEntity(id);
                if (entity instanceof Mob mob) {
                    mob.discard();
                }
            }
        }
        waveMobs.clear();
    }
}
