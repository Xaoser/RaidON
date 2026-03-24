package ru.xaoser.raidon.runtime.raid;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import ru.xaoser.raidon.api.sup.MobTraits;
import ru.xaoser.raidon.api.sup.MobTargeting;
import ru.xaoser.raidon.api.sup.RaidRuntime;
import ru.xaoser.raidon.api.sup.SpawnBehavior;
import ru.xaoser.raidon.runtime.nbt.RelaxedNbtParser;
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
    private static final int SPAWN_BATCH_PER_TICK = 8;
    private static final int LAND_SEARCH_RADIUS = 8;

    private final Raid raid;
    private final ServerLevel level;
    private final BlockPos center;
    private final BlockPos spawnPoint;
    private final BlockPos raidTargetPoint;
    private final int mobWanderRadius;
    private final RaidSpawnSettings spawnSettings;
    private final RaidGuiSettings guiSettings;
    private final BasicRaidContext context;

    private int currentWaveIndex = -1;
    private final Map<Integer, List<UUID>> waveMobs = new HashMap<>();
    private final Map<Integer, Integer> waveTotals = new HashMap<>();
    private final Map<Integer, List<PendingSpawn>> pendingMobs = new HashMap<>();
    private final Map<UUID, TrackedMobState> trackedMobStates = new HashMap<>();
    private int lastSentAlive = -1;
    private int lastSentWave = -2;
    private boolean raidStarted = false;
    private boolean completed = false;
    private int remainingRespawnAttempts = MAX_RESPAWN_ATTEMPTS;
    private SpawnResult lastSpawnResult = SpawnResult.empty();

    ActiveRaid(Raid raid, ServerLevel level, BlockPos center, BlockPos spawnPoint, BlockPos raidTargetPoint, int mobWanderRadius, RaidSpawnSettings spawnSettings, RaidGuiSettings guiSettings) {
        this.raid = raid;
        this.level = level;
        this.center = center.immutable();
        this.spawnPoint = spawnPoint.immutable();
        this.raidTargetPoint = raidTargetPoint.immutable();
        this.mobWanderRadius = Math.max(4, mobWanderRadius);
        this.spawnSettings = RaidSpawnSettings.sanitized(spawnSettings);
        this.guiSettings = guiSettings == null ? RaidGuiSettings.DEFAULT : guiSettings;
        this.context = new BasicRaidContext(level, this.center, raid.difficulty(), Math.max(96.0D, spawnSettings.maxRadius() + 48.0D));
    }

    void tick() {
        if (completed) return;

        if (currentWaveIndex < 0) {
            triggerRaidStart();
            startWave(0);
            sendProgressIfNeeded();
            return;
        }

        RaidWave wave = raid.waves().get(currentWaveIndex);
        tickMobTunings();
        if (hasPendingMobs(currentWaveIndex)) {
            trySpawnPending(currentWaveIndex, wave);
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
        return waveMobs.getOrDefault(currentWaveIndex, List.of()).size();
    }

    void triggerRaidStart() {
        if (raidStarted) {
            return;
        }
        raidStarted = true;
        raid.startAction().run(context);
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
        preparePendingWave(waveIndex, wave);
        trySpawnCurrentWave(waveIndex, wave);
        sendProgressIfNeeded();
    }

    private void trySpawnCurrentWave(int waveKey, RaidWave wave) {
        lastSpawnResult = spawnWaveMobs(waveKey, wave);

        while (lastSpawnResult.planned > 0 && lastSpawnResult.spawned == 0 && remainingRespawnAttempts > 0) {
            remainingRespawnAttempts--;
            LOGGER.warn("[Raidon][{}] wave {} spawn failed (planned={}, created={}, spawned={}, posNull={}, addFailed={}). Retrying... (left={})",
                    raid.id(), wave.index(), lastSpawnResult.planned, lastSpawnResult.created, lastSpawnResult.spawned,
                    lastSpawnResult.posNull, lastSpawnResult.addFailed, remainingRespawnAttempts);
            lastSpawnResult = spawnWaveMobs(waveKey, wave);
        }

        if (lastSpawnResult.planned > 0 && lastSpawnResult.spawned == 0 && remainingRespawnAttempts == 0) {
            LOGGER.error("[Raidon][{}] wave {} could not spawn any mobs after {} attempts; wave will complete immediately",
                    raid.id(), wave.index(), MAX_RESPAWN_ATTEMPTS + 1);
        } else if (lastSpawnResult.planned == 0) {
            LOGGER.warn("[Raidon][{}] wave {} has no valid mobs after config parsing; wave will complete immediately", raid.id(), wave.index());
        }
    }

    private SpawnResult spawnWaveMobs(int waveKey, RaidWave wave) {
        List<UUID> spawned = new ArrayList<>();
        RandomSource random = level.getRandom();
        int planned = pendingCount(waveKey);
        int created = 0;
        int spawnedCount = 0;
        int posNull = 0;
        int addFailed = 0;

        SpawnAttemptResult attempt = spawnPendingMobs(waveKey, wave, random);
        spawned.addAll(attempt.spawned());
        created += attempt.created();
        spawnedCount += attempt.spawnedCount();
        posNull += attempt.posNull();
        addFailed += attempt.addFailed();

        waveMobs.put(waveKey, spawned);
        waveTotals.put(waveKey, planned);

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
            BlockPos rawCandidate = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPoint.offset(dx, 0, dz));
            BlockPos candidate = adaptSpawnToGround(rawCandidate);
            if (candidate == null || isTooCloseToPlayer(candidate)) {
                continue;
            }
            return candidate;
        }

        BlockPos fallback = adaptSpawnToGround(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPoint));
        if (fallback != null && !isTooCloseToPlayer(fallback)) {
            return fallback;
        }

        return null;
    }


    private BlockPos adaptSpawnToGround(BlockPos start) {
        if (start == null) {
            return null;
        }
        if (isValidGroundSpawn(start)) {
            return start;
        }

        for (int r = 1; r <= LAND_SEARCH_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos probe = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start.offset(dx, 0, dz));
                    if (isValidGroundSpawn(probe)) {
                        return probe;
                    }
                }
            }
        }

        return start.above(3);
    }

    private boolean isValidGroundSpawn(BlockPos pos) {
        if (spawnSettings.requireGround()) {
            BlockState stateBelow = level.getBlockState(pos.below());
            if (!stateBelow.isSolidRender(level, pos.below())) {
                return false;
            }
        }
        return !spawnSettings.avoidWater() || !level.getFluidState(pos).isSource();
    }
    private void preparePendingWave(int waveKey, RaidWave wave) {
        List<PendingSpawn> pending = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < wave.mobs().size(); i++) {
            MobEntry entry = wave.mobs().get(i);
            pending.add(new PendingSpawn(entry.type(), entry.behavior(), entry.baseDamage(), entry.drops(),
                    entry.targeting(), entry.tuning(), entry.nbtData(), i, entry.count()));
            total += entry.count();
        }
        pendingMobs.put(waveKey, pending);
        waveTotals.put(waveKey, total);
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

    private void trySpawnPending(int waveKey, RaidWave wave) {
        SpawnAttemptResult attempt = spawnPendingMobs(waveKey, wave, level.getRandom());
        if (attempt.spawnedCount() > 0) {
            List<UUID> spawned = waveMobs.computeIfAbsent(waveKey, key -> new ArrayList<>());
            for (UUID id : attempt.spawned()) {
                if (!spawned.contains(id)) {
                    spawned.add(id);
                }
            }
            LOGGER.info("[Raidon][{}] pending spawn wave={} plannedLeft={} created={} spawned={} posNull={} addFailed={}",
                    raid.id(), wave.index(), pendingCount(waveKey), attempt.created(), attempt.spawnedCount(),
                    attempt.posNull(), attempt.addFailed());
        }
    }

    private SpawnAttemptResult spawnPendingMobs(int waveKey, RaidWave wave, RandomSource random) {
        List<UUID> spawned = new ArrayList<>();
        int created = 0;
        int spawnedCount = 0;
        int posNull = 0;
        int addFailed = 0;

        List<PendingSpawn> pending = pendingMobs.get(waveKey);
        if (pending == null || pending.isEmpty()) {
            return new SpawnAttemptResult(spawned, created, spawnedCount, posNull, addFailed);
        }

        int quota = SPAWN_BATCH_PER_TICK;
        for (PendingSpawn entry : pending) {
            int remaining = entry.remaining();
            for (int i = 0; i < remaining && quota > 0; i++) {
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
                applyMobNbt(mob, entry.nbtData());
                applyDifficultyScaling(mob, entry);
                applyMobTuning(mob, entry.tuning());
                mob.moveTo(pos, random.nextFloat() * 360.0F, 0.0F);
                mob.setPersistenceRequired();
                mob.addTag(MobAiHelper.RAID_MOB_TAG);
                UUID mobId = mob.getUUID();
                trackedMobStates.put(mobId, new TrackedMobState(waveKey, entry.mobIndex()));
                if (entry.tuning().usesRaidAi()) {
                    MobAiHelper.applyBehavior(mob, entry.behavior(), entry.targeting(), entry.tuning(),
                            raidTargetPoint, mobWanderRadius, spawnPoint);
                }
                if (level.addFreshEntity(mob)) {
                    spawned.add(mobId);
                    spawnedCount++;
                    entry.decrement();
                    quota--;
                } else {
                    trackedMobStates.remove(mobId);
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
                waveTotal,
                guiSettings.mainTexture(),
                guiSettings.progressTexture(),
                guiSettings.width(),
                guiSettings.height()
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

    double hudRange() {
        return Math.max(192.0D, spawnSettings.maxRadius() + 96.0D);
    }

    double conflictRadius() {
        return Math.max(48.0D, spawnSettings.maxRadius() + 16.0D);
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
        for (UUID id : trackedMobStates.keySet()) {
            Entity entity = level.getEntity(id);
            if (entity instanceof Mob mob) {
                mob.discard();
            }
        }
        waveMobs.clear();
        pendingMobs.clear();
        trackedMobStates.clear();
    }

    boolean handleMobDeath(Mob mob) {
        UUID id = mob.getUUID();
        TrackedMobState trackedState = trackedMobStates.get(id);
        boolean tracked = removeTrackedMob(id);
        List<DropEntry> drops = trackedState == null ? List.of() : mergedDrops(trackedState);
        if (drops != null && !drops.isEmpty()) {
            dropLoot(drops, mob.blockPosition(), level.getRandom());
        }
        return tracked;
    }

    private boolean removeTrackedMob(UUID id) {
        TrackedMobState trackedState = trackedMobStates.remove(id);
        if (trackedState == null) {
            return false;
        }
        List<UUID> ids = waveMobs.get(trackedState.waveIndex());
        if (ids != null) {
            ids.remove(id);
            if (ids.isEmpty()) {
                waveMobs.remove(trackedState.waveIndex());
            }
        }
        return true;
    }

    private void completeRaid() {
        if (completed) {
            return;
        }
        completed = true;
        raid.endAction().run(context);
    }

    private void dropLoot(List<DropEntry> drops, BlockPos pos, RandomSource random) {
        for (DropEntry drop : drops) {
            if ((random.nextDouble() * 100.0D) > drop.chance()) {
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

    private void tickMobTunings() {
        for (Map.Entry<UUID, TrackedMobState> entry : trackedMobStates.entrySet()) {
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                continue;
            }
            MobTraits tuning = mobEntry(entry.getValue()).tuning();
            if (Boolean.FALSE.equals(tuning.burnInSun()) && level.isDay() && level.canSeeSky(mob.blockPosition())) {
                mob.clearFire();
            }
            if (Boolean.FALSE.equals(tuning.canDrown())) {
                mob.setAirSupply(mob.getMaxAirSupply());
            }
        }
    }

    private void applyMobNbt(Mob mob, String snbt) {
        if (mob == null || snbt == null || snbt.isBlank()) {
            return;
        }
        try {
            CompoundTag merged = mob.saveWithoutId(new CompoundTag());
            CompoundTag custom = RelaxedNbtParser.parseCompound(snbt);
            custom.remove("id");
            custom.remove("UUID");
            custom.remove("Pos");
            custom.remove("Motion");
            custom.remove("Rotation");
            custom.remove("Passengers");
            merged.merge(custom);
            mob.load(merged);
        } catch (CommandSyntaxException exception) {
            LOGGER.warn("[Raidon][{}] Invalid mob NBT for type {}: {}", raid.id(),
                    EntityType.getKey(mob.getType()), exception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("[Raidon][{}] Failed to apply mob NBT for type {}", raid.id(), mob.getType(), exception);
        }
    }

    private void applyMobTuning(Mob mob, MobTraits tuning) {
        if (tuning == null || tuning.isDefault()) {
            return;
        }
        if (tuning.knockbackResistance() != null) {
            AttributeInstance kb = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(Math.max(0.0D, Math.min(1.0D, tuning.knockbackResistance())));
            }
        }
    }

    CompoundTag saveState() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", raid.id().toString());
        tag.putString("dimension", level.dimension().location().toString());
        tag.putIntArray("center", new int[]{center.getX(), center.getY(), center.getZ()});
        tag.putIntArray("spawnPoint", new int[]{spawnPoint.getX(), spawnPoint.getY(), spawnPoint.getZ()});
        tag.putIntArray("raidTargetPoint", new int[]{raidTargetPoint.getX(), raidTargetPoint.getY(), raidTargetPoint.getZ()});
        tag.putInt("mobWanderRadius", mobWanderRadius);
        tag.putInt("currentWaveIndex", currentWaveIndex);
        tag.putInt("remainingRespawnAttempts", remainingRespawnAttempts);
        tag.putBoolean("raidStarted", raidStarted);

        ListTag tracked = new ListTag();
        for (Map.Entry<UUID, TrackedMobState> entry : trackedMobStates.entrySet()) {
            CompoundTag mob = new CompoundTag();
            mob.putUUID("uuid", entry.getKey());
            mob.putInt("wave", entry.getValue().waveIndex());
            mob.putInt("mobIndex", entry.getValue().mobIndex());
            tracked.add(mob);
        }
        tag.put("trackedMobs", tracked);

        ListTag pending = new ListTag();
        List<PendingSpawn> currentPending = pendingMobs.get(currentWaveIndex);
        if (currentPending != null) {
            for (PendingSpawn spawn : currentPending) {
                CompoundTag pendingTag = new CompoundTag();
                pendingTag.putInt("mobIndex", spawn.mobIndex());
                pendingTag.putInt("remaining", spawn.remaining());
                pending.add(pendingTag);
            }
        }
        tag.put("pendingMobs", pending);
        return tag;
    }

    boolean restoreState(CompoundTag tag) {
        int waveIndex = tag.getInt("currentWaveIndex");
        if (waveIndex < -1 || waveIndex >= raid.waves().size()) {
            LOGGER.warn("[Raidon][{}] Skipping invalid saved wave index {}", raid.id(), waveIndex);
            return false;
        }

        currentWaveIndex = waveIndex;
        raidStarted = tag.contains("raidStarted", Tag.TAG_BYTE)
                ? tag.getBoolean("raidStarted")
                : currentWaveIndex >= 0;
        remainingRespawnAttempts = tag.contains("remainingRespawnAttempts", Tag.TAG_INT)
                ? tag.getInt("remainingRespawnAttempts")
                : MAX_RESPAWN_ATTEMPTS;
        waveMobs.clear();
        pendingMobs.clear();
        trackedMobStates.clear();
        waveTotals.clear();

        if (currentWaveIndex >= 0) {
            RaidWave wave = raid.waves().get(currentWaveIndex);
            waveTotals.put(currentWaveIndex, wave.mobs().stream().mapToInt(MobEntry::count).sum());
            List<PendingSpawn> pending = restorePendingWave(wave, tag.getList("pendingMobs", Tag.TAG_COMPOUND));
            if (!pending.isEmpty()) {
                pendingMobs.put(currentWaveIndex, pending);
            }
        }

        ListTag tracked = tag.getList("trackedMobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < tracked.size(); i++) {
            CompoundTag mobTag = tracked.getCompound(i);
            if (!mobTag.hasUUID("uuid")) {
                continue;
            }
            UUID uuid = mobTag.getUUID("uuid");
            TrackedMobState state = new TrackedMobState(mobTag.getInt("wave"), mobTag.getInt("mobIndex"));
            if (!isValidTrackedMobState(state)) {
                continue;
            }
            trackedMobStates.put(uuid, state);
            waveMobs.computeIfAbsent(state.waveIndex(), key -> new ArrayList<>()).add(uuid);
        }
        return true;
    }

    boolean tracksMob(UUID uuid) {
        return trackedMobStates.containsKey(uuid);
    }

    boolean rebindTrackedMob(Mob mob) {
        TrackedMobState state = trackedMobStates.get(mob.getUUID());
        if (state == null) {
            return false;
        }
        MobEntry entry = mobEntry(state);
        if (!mob.getTags().contains(MobAiHelper.RAID_MOB_TAG)) {
            mob.addTag(MobAiHelper.RAID_MOB_TAG);
        }
        mob.setPersistenceRequired();
        applyMobTuning(mob, entry.tuning());
        if (entry.tuning().usesRaidAi()) {
            MobAiHelper.applyBehavior(mob, entry.behavior(), entry.targeting(), entry.tuning(),
                    raidTargetPoint, mobWanderRadius, spawnPoint);
        }
        waveMobs.computeIfAbsent(state.waveIndex(), key -> new ArrayList<>());
        List<UUID> ids = waveMobs.get(state.waveIndex());
        if (!ids.contains(mob.getUUID())) {
            ids.add(mob.getUUID());
        }
        return true;
    }

    private List<PendingSpawn> restorePendingWave(RaidWave wave, ListTag pendingTags) {
        List<PendingSpawn> pending = new ArrayList<>();
        if (pendingTags == null || pendingTags.isEmpty()) {
            return pending;
        }
        for (int i = 0; i < pendingTags.size(); i++) {
            CompoundTag pendingTag = pendingTags.getCompound(i);
            int mobIndex = pendingTag.getInt("mobIndex");
            int remaining = pendingTag.getInt("remaining");
            if (remaining <= 0 || mobIndex < 0 || mobIndex >= wave.mobs().size()) {
                continue;
            }
            MobEntry entry = wave.mobs().get(mobIndex);
            pending.add(new PendingSpawn(entry.type(), entry.behavior(), entry.baseDamage(), entry.drops(),
                    entry.targeting(), entry.tuning(), entry.nbtData(), mobIndex, remaining));
        }
        return pending;
    }

    private boolean isValidTrackedMobState(TrackedMobState state) {
        return state.waveIndex() >= 0
                && state.waveIndex() < raid.waves().size()
                && state.mobIndex() >= 0
                && state.mobIndex() < raid.waves().get(state.waveIndex()).mobs().size();
    }

    private MobEntry mobEntry(TrackedMobState state) {
        return raid.waves().get(state.waveIndex()).mobs().get(state.mobIndex());
    }

    private List<DropEntry> mergedDrops(TrackedMobState state) {
        List<DropEntry> drops = new ArrayList<>(raid.globalDrops());
        drops.addAll(mobEntry(state).drops());
        return drops;
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

    private record TrackedMobState(int waveIndex, int mobIndex) {}

    private record SpawnAttemptResult(List<UUID> spawned, int created, int spawnedCount, int posNull, int addFailed) {}

    private static final class PendingSpawn {
        private final EntityType<? extends Mob> type;
        private final SpawnBehavior behavior;
        private final Float baseDamage;
        private final List<DropEntry> drops;
        private final ru.xaoser.raidon.api.sup.MobTargeting targeting;
        private final MobTraits tuning;
        private final String nbtData;
        private final int mobIndex;
        private int remaining;

        private PendingSpawn(EntityType<? extends Mob> type, SpawnBehavior behavior, Float baseDamage, List<DropEntry> drops,
                             ru.xaoser.raidon.api.sup.MobTargeting targeting, MobTraits tuning, String nbtData,
                             int mobIndex, int remaining) {
            this.type = type;
            this.behavior = behavior;
            this.baseDamage = baseDamage;
            this.drops = drops == null ? List.of() : List.copyOf(drops);
            this.targeting = targeting == null ? ru.xaoser.raidon.api.sup.MobTargeting.defaults() : targeting;
            this.tuning = tuning == null ? MobTraits.defaults() : tuning;
            this.nbtData = nbtData == null || nbtData.isBlank() ? null : nbtData.trim();
            this.mobIndex = mobIndex;
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

        private ru.xaoser.raidon.api.sup.MobTargeting targeting() {
            return targeting;
        }

        private MobTraits tuning() {
            return tuning;
        }

        private String nbtData() {
            return nbtData;
        }

        private int mobIndex() {
            return mobIndex;
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
