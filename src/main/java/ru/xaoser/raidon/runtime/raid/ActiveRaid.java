package ru.xaoser.raidon.runtime.raid;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidWave;
import ru.xaoser.raidon.api.sup.DropEntry;
import ru.xaoser.raidon.api.sup.MobEntry;
import ru.xaoser.raidon.api.sup.RaidRuntime;
import ru.xaoser.raidon.api.sup.SpawnBehavior;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.raid.ai.MobAiHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class ActiveRaid implements RaidRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RESPAWN_ATTEMPTS = 3;
    private static final int MIN_PLAYER_DISTANCE = 12;

    private final Raid raid;
    private final ServerLevel level;
    private final BlockPos center;
    private final BlockPos spawnPoint;
    private final BlockPos raidTargetPoint;
    private final RaidSpawnSettings spawnSettings;
    private final BasicRaidContext context;

    private int currentWaveIndex = -1;
    private final Map<Integer, List<UUID>> waveMobs = new HashMap<>();
    private final Map<Integer, Integer> waveTotals = new HashMap<>();
    private final Map<Integer, List<PendingSpawn>> pendingMobs = new HashMap<>();
    private final Map<UUID, List<DropEntry>> mobDrops = new HashMap<>();
    private int lastSentAlive = -1;
    private int lastSentWave = -2;
    private boolean completed = false;
    private int remainingRespawnAttempts = MAX_RESPAWN_ATTEMPTS;
    private SpawnResult lastSpawnResult = SpawnResult.empty();

    ActiveRaid(Raid raid, ServerLevel level, BlockPos center, BlockPos spawnPoint, BlockPos raidTargetPoint, RaidSpawnSettings spawnSettings) {
        this.raid = raid;
        this.level = level;
        this.center = center.immutable();
        this.spawnPoint = spawnPoint.immutable();
        this.raidTargetPoint = raidTargetPoint.immutable();
        this.spawnSettings = RaidSpawnSettings.sanitized(spawnSettings);
        this.context = new BasicRaidContext(level, this.center, raid.difficulty());
    }

    void tick() {
        if (completed) return;

        if (currentWaveIndex < 0) {
            startWave(0);
            sendProgressIfNeeded();
            return;
        }

        RaidWave wave = raid.waves().get(currentWaveIndex);
        if (hasPendingMobs(currentWaveIndex)) {
            trySpawnPending(wave);
        }

        boolean waveComplete = wave.completeCondition().isComplete(this, context);
        if (waveComplete && hasPendingMobs(currentWaveIndex)) {
            waveComplete = false;
        }

        if (waveComplete) {
            wave.onWaveEnd().run(context);
            int next = currentWaveIndex + 1;
            if (next >= raid.waves().size()) {
                completeRaid();
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
        remainingRespawnAttempts = MAX_RESPAWN_ATTEMPTS;
        LOGGER.info("[Raidon][{}] startWave index={} mobs={} spawnRadius={} minR={} maxR={} attemptsPerMob={} requireGround={} avoidWater={} spawnPoint={} targetPoint={}",
                raid.id(), waveIndex, wave.mobs().size(), wave.spawnRadius(),
                spawnSettings.minRadius(), spawnSettings.maxRadius(), spawnSettings.attemptsPerMob(),
                spawnSettings.requireGround(), spawnSettings.avoidWater(), spawnPoint, raidTargetPoint);
        wave.onWaveStart().run(context);
        preparePendingWave(wave);
        trySpawnCurrentWave(wave);
        sendProgressIfNeeded();
    }

    private void trySpawnCurrentWave(RaidWave wave) {
        lastSpawnResult = spawnWaveMobs(wave);

        while (lastSpawnResult.planned > 0 && lastSpawnResult.spawned == 0 && remainingRespawnAttempts > 0) {
            remainingRespawnAttempts--;
            LOGGER.warn("[Raidon][{}] wave {} spawn failed (planned={}, created={}, spawned={}, posNull={}, addFailed={}). Retrying... (left={})",
                    raid.id(), wave.index(), lastSpawnResult.planned, lastSpawnResult.created, lastSpawnResult.spawned,
                    lastSpawnResult.posNull, lastSpawnResult.addFailed, remainingRespawnAttempts);
            lastSpawnResult = spawnWaveMobs(wave);
        }

        if (lastSpawnResult.planned > 0 && lastSpawnResult.spawned == 0 && remainingRespawnAttempts == 0) {
            LOGGER.error("[Raidon][{}] wave {} could not spawn any mobs after {} attempts; wave will complete immediately",
                    raid.id(), wave.index(), MAX_RESPAWN_ATTEMPTS + 1);
        } else if (lastSpawnResult.planned == 0) {
            LOGGER.warn("[Raidon][{}] wave {} has no valid mobs after config parsing; wave will complete immediately", raid.id(), wave.index());
        }
    }

    private SpawnResult spawnWaveMobs(RaidWave wave) {
        List<UUID> spawned = new ArrayList<>();
        RandomSource random = level.getRandom();
        int planned = pendingCount(wave.index());
        int created = 0;
        int spawnedCount = 0;
        int posNull = 0;
        int addFailed = 0;

        SpawnAttemptResult attempt = spawnPendingMobs(wave, random);
        spawned.addAll(attempt.spawned());
        created += attempt.created();
        spawnedCount += attempt.spawnedCount();
        posNull += attempt.posNull();
        addFailed += attempt.addFailed();

        waveMobs.put(wave.index(), spawned);
        waveTotals.put(wave.index(), planned);

        LOGGER.info("[Raidon][{}] spawnWaveMobs wave={} planned={} created={} spawned={} posNull={} addFailed={} center={} spawnPoint={} targetPoint={}",
                raid.id(), wave.index(), planned, created, spawnedCount, posNull, addFailed, center, spawnPoint, raidTargetPoint);

        return new SpawnResult(planned, created, spawnedCount, posNull, addFailed);
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
            BlockPos candidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPoint.offset(dx, 0, dz));

            if (spawnSettings.requireGround()) {
                BlockState stateBelow = level.getBlockState(candidate.below());
                if (!stateBelow.isSolidRender(level, candidate.below())) {
                    continue;
                }
            }

            if (spawnSettings.avoidWater() && level.getFluidState(candidate).isSource()) {
                continue;
            }

            if (isTooCloseToPlayer(candidate)) {
                continue;
            }

            return candidate;
        }

        BlockPos fallback = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPoint);
        if (!isTooCloseToPlayer(fallback)) {
            return fallback;
        }

        return null;
    }

    private void preparePendingWave(RaidWave wave) {
        List<PendingSpawn> pending = new ArrayList<>();
        int total = 0;
        for (MobEntry entry : wave.mobs()) {
            pending.add(new PendingSpawn(entry.type(), entry.behavior(), entry.baseDamage(), entry.drops(), entry.count()));
            total += entry.count();
        }
        pendingMobs.put(wave.index(), pending);
        waveTotals.put(wave.index(), total);
    }

    private boolean hasPendingMobs(int waveIndex) {
        return pendingCount(waveIndex) > 0;
    }

    private int pendingCount(int waveIndex) {
        List<PendingSpawn> pending = pendingMobs.get(waveIndex);
        if (pending == null) return 0;
        int total = 0;
        for (PendingSpawn entry : pending) {
            total += entry.remaining();
        }
        return total;
    }

    private void trySpawnPending(RaidWave wave) {
        SpawnAttemptResult attempt = spawnPendingMobs(wave, level.getRandom());
        if (attempt.spawnedCount() > 0) {
            List<UUID> spawned = waveMobs.computeIfAbsent(wave.index(), key -> new ArrayList<>());
            spawned.addAll(attempt.spawned());
            LOGGER.info("[Raidon][{}] pending spawn wave={} plannedLeft={} created={} spawned={} posNull={} addFailed={}",
                    raid.id(), wave.index(), pendingCount(wave.index()), attempt.created(), attempt.spawnedCount(),
                    attempt.posNull(), attempt.addFailed());
        }
    }

    private SpawnAttemptResult spawnPendingMobs(RaidWave wave, RandomSource random) {
        List<UUID> spawned = new ArrayList<>();
        int created = 0;
        int spawnedCount = 0;
        int posNull = 0;
        int addFailed = 0;

        List<PendingSpawn> pending = pendingMobs.get(wave.index());
        if (pending == null || pending.isEmpty()) {
            return new SpawnAttemptResult(spawned, created, spawnedCount, posNull, addFailed);
        }

        for (PendingSpawn entry : pending) {
            int remaining = entry.remaining();
            for (int i = 0; i < remaining; i++) {
                BlockPos pos = findSpawnPos(random, wave.spawnRadius());
                if (pos == null) {
                    posNull++;
                    continue;
                }
                Mob mob = entry.type().create(level);
                if (mob == null) {
                    addFailed++;
                    continue;
                }
                created++;
                applyDifficultyScaling(mob, entry);
                mob.moveTo(pos, random.nextFloat() * 360.0F, 0.0F);
                mob.setPersistenceRequired();
                MobAiHelper.applyBehavior(mob, entry.behavior(), raidTargetPoint);
                if (level.addFreshEntity(mob)) {
                    spawned.add(mob.getUUID());
                    spawnedCount++;
                    entry.decrement();
                    if (!entry.drops().isEmpty()) {
                        mobDrops.put(mob.getUUID(), entry.drops());
                    }
                } else {
                    addFailed++;
                }
            }
        }

        pending.removeIf(p -> p.remaining() <= 0);

        return new SpawnAttemptResult(spawned, created, spawnedCount, posNull, addFailed);
    }

    private boolean isTooCloseToPlayer(BlockPos pos) {
        double minDistSq = MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) < minDistSq) {
                return true;
            }
        }
        return false;
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
        completeRaid();
        despawnTracked();
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
        pendingMobs.clear();
        mobDrops.clear();
    }

    boolean handleMobDeath(Mob mob) {
        UUID id = mob.getUUID();
        boolean tracked = removeTrackedMob(id);
        List<DropEntry> drops = mobDrops.remove(id);
        if (drops != null && !drops.isEmpty()) {
            dropLoot(drops, mob.blockPosition(), level.getRandom());
        }
        return tracked;
    }

    private boolean removeTrackedMob(UUID id) {
        boolean removed = false;
        for (List<UUID> ids : waveMobs.values()) {
            if (ids.remove(id)) {
                removed = true;
                break;
            }
        }
        return removed;
    }

    private void completeRaid() {
        if (completed) {
            return;
        }
        completed = true;
        raid.endAction().run(context);
        if (!raid.globalDrops().isEmpty()) {
            dropLoot(raid.globalDrops(), center, level.getRandom());
        }
    }

    private void dropLoot(List<DropEntry> drops, BlockPos pos, RandomSource random) {
        for (DropEntry drop : drops) {
            if (random.nextDouble() > drop.chance()) {
                continue;
            }
            int min = drop.min();
            int max = drop.max();
            int count = min == max ? min : min + random.nextInt(max - min + 1);
            if (count <= 0) {
                continue;
            }
            ItemStack stack = new ItemStack(drop.item(), count);
            ItemEntity entity = new ItemEntity(
                    level,
                    pos.getX() + 0.5D,
                    pos.getY() + 0.5D,
                    pos.getZ() + 0.5D,
                    stack
            );
            level.addFreshEntity(entity);
        }
    }

    private void applyDifficultyScaling(Mob mob, PendingSpawn entry) {
        float multiplier = difficultyMultiplier(raid.difficulty());
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double baseHealth = health.getBaseValue();
            double scaledHealth = Math.max(1.0D, baseHealth * multiplier);
            health.setBaseValue(scaledHealth);
            mob.setHealth((float) scaledHealth);
        }
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            double baseDamage = entry.baseDamage() != null ? entry.baseDamage() : damage.getBaseValue();
            double scaledDamage = Math.max(0.0D, baseDamage * multiplier);
            damage.setBaseValue(scaledDamage);
        }
    }

    private static float difficultyMultiplier(float difficulty) {
        if (difficulty <= 1.0F) {
            return 0.5F;
        }
        return Math.max(0.5F, difficulty - 1.0F);
    }

    private record SpawnResult(int planned, int created, int spawned, int posNull, int addFailed) {
        static SpawnResult empty() {
            return new SpawnResult(0, 0, 0, 0, 0);
        }
    }

    private record SpawnAttemptResult(List<UUID> spawned, int created, int spawnedCount, int posNull, int addFailed) {}

    private static final class PendingSpawn {
        private final EntityType<? extends Mob> type;
        private final SpawnBehavior behavior;
        private final Float baseDamage;
        private final List<DropEntry> drops;
        private int remaining;

        private PendingSpawn(EntityType<? extends Mob> type, SpawnBehavior behavior, Float baseDamage, List<DropEntry> drops, int remaining) {
            this.type = type;
            this.behavior = behavior;
            this.baseDamage = baseDamage;
            this.drops = drops == null ? List.of() : List.copyOf(drops);
            this.remaining = remaining;
        }

        private EntityType<? extends Mob> type() {
            return type;
        }

        private SpawnBehavior behavior() {
            return behavior;
        }

        private Float baseDamage() {
            return baseDamage;
        }

        private List<DropEntry> drops() {
            return drops;
        }

        private int remaining() {
            return remaining;
        }

        private void decrement() {
            if (remaining > 0) {
                remaining--;
            }
        }
    }
}
