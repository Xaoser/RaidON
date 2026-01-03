package ru.xaoser.raidon.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import ru.xaoser.raidon.api.Raid;
import ru.xaoser.raidon.api.RaidWave;
import ru.xaoser.raidon.api.sup.MobEntry;
import ru.xaoser.raidon.api.sup.RaidAction;
import ru.xaoser.raidon.api.sup.RaidContext;
import ru.xaoser.raidon.api.sup.RaidRuntime;

import java.util.*;

/**
 * Central raid runtime that owns registrations and active raid lifecycles.
 */
public final class RaidManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final RaidManager INSTANCE = new RaidManager();

    private final Map<ResourceLocation, Raid> registeredRaids = new HashMap<>();
    private final List<ActiveRaid> activeRaids = new ArrayList<>();

    private boolean registeredOnBus;

    private RaidManager() {
    }

    public static RaidManager get() {
        return INSTANCE;
    }

    /** Register event listeners only once. */
    public void init() {
        if (!registeredOnBus) {
            MinecraftForge.EVENT_BUS.register(this);
            registeredOnBus = true;
            LOGGER.debug("RaidManager registered on the event bus");
        }
    }

    /** Register a new raid definition. */
    public void registerRaid(Raid raid) {
        Objects.requireNonNull(raid, "raid");
        Raid existing = registeredRaids.putIfAbsent(raid.id(), raid);
        if (existing != null) {
            LOGGER.warn("Raid with id {} is already registered; ignoring duplicate", raid.id());
        }
    }

    /** Start a raid by id if it was registered. */
    public Optional<ActiveRaid> startRaid(ResourceLocation id, ServerLevel level, BlockPos center) {
        Raid definition = registeredRaids.get(id);
        if (definition == null) {
            LOGGER.warn("Cannot start raid {}: not registered", id);
            return Optional.empty();
        }

        ActiveRaid raid = new ActiveRaid(definition, level, center);
        raid.begin();
        activeRaids.add(raid);
        LOGGER.info("Started raid {} at {} in {}", id, center, level.dimension().location());
        return Optional.of(raid);
    }

    /** Tick all active raids and prune those that finished. */
    private void tickActiveRaids() {
        Iterator<ActiveRaid> it = activeRaids.iterator();
        while (it.hasNext()) {
            ActiveRaid raid = it.next();
            raid.tick();
            if (raid.isFinished()) {
                it.remove();
            }
        }
    }

    /** Remove raids running in an unloading level. */
    private void dropForLevel(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        activeRaids.removeIf(raid -> raid.isInLevel(serverLevel));
    }

    /** Remove raids that no longer have any nearby players. */
    private void dropEmptyRaids(ServerLevel level) {
        Iterator<ActiveRaid> it = activeRaids.iterator();
        while (it.hasNext()) {
            ActiveRaid raid = it.next();
            if (raid.isInLevel(level) && !raid.hasPlayersNearby()) {
                raid.stopSilently();
                it.remove();
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickActiveRaids();
    }

    @SubscribeEvent
    public void onMobDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        UUID id = mob.getUUID();
        for (ActiveRaid raid : activeRaids) {
            if (raid.forgetMob(id)) break;
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        dropForLevel((Level) event.getLevel());
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            dropEmptyRaids(player.serverLevel());
        }
    }

    @SubscribeEvent
    public void onPlayerDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            dropEmptyRaids(player.serverLevel());
        }
    }

    /** Active raid runtime that owns progression. */
    public static final class ActiveRaid implements RaidRuntime {
        private static final double DEFAULT_AREA_RADIUS = 64.0D;

        private final Raid raid;
        private final ServerLevel level;
        private final BlockPos center;
        private final Set<UUID> aliveMobs = new HashSet<>();

        private int waveIndex = -1;
        private boolean finished;

        private ActiveRaid(Raid raid, ServerLevel level, BlockPos center) {
            this.raid = raid;
            this.level = level;
            this.center = center.immutable();
        }

        public void begin() {
            advanceWave();
        }

        public boolean isFinished() {
            return finished;
        }

        public boolean isInLevel(ServerLevel level) {
            return this.level == level;
        }

        public void tick() {
            if (finished) return;

            pruneDeadMobs();

            if (!hasPlayersNearby()) {
                LOGGER.info("Ending raid {} because no players are nearby", raid.id());
                stopSilently();
                return;
            }

            RaidWave current = currentWave();
            if (current == null) {
                stopSilently();
                return;
            }

            if (current.completeCondition().isComplete(this, createContext())) {
                current.onWaveEnd().run(createContext());
                advanceWave();
            }
        }

        @Override
        public int aliveMobsInCurrentWave() {
            return aliveMobs.size();
        }

        public boolean hasPlayersNearby() {
            Vec3 centerVec = Vec3.atCenterOf(center);
            double distSqr = DEFAULT_AREA_RADIUS * DEFAULT_AREA_RADIUS;
            return !level.getPlayers(player -> player.distanceToSqr(centerVec) <= distSqr).isEmpty();
        }

        public boolean forgetMob(UUID id) {
            return aliveMobs.remove(id);
        }

        public void stopSilently() {
            finishRaid(false);
        }

        private void advanceWave() {
            waveIndex++;

            if (waveIndex >= raid.waves().size()) {
                finishRaid(true);
                return;
            }

            aliveMobs.clear();
            RaidWave wave = raid.waves().get(waveIndex);
            spawnWave(wave);
            wave.onWaveStart().run(createContext());
        }

        private RaidWave currentWave() {
            if (waveIndex < 0 || waveIndex >= raid.waves().size()) return null;
            return raid.waves().get(waveIndex);
        }

        private void spawnWave(RaidWave wave) {
            LOGGER.debug("Spawning wave {} for raid {} ({} mobs)", wave.index(), raid.id(), wave.mobs().size());
            for (MobEntry entry : wave.mobs()) {
                for (int i = 0; i < entry.count(); i++) {
                    Mob mob = entry.type().create(level);
                    if (mob == null) {
                        LOGGER.warn("Could not create mob {} for raid {}", entry.type().builtInRegistryHolder().key(), raid.id());
                        continue;
                    }

                    BlockPos spawnPos = findSpawnPos(wave.spawnRadius());
                    mob.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
                    if (level.addFreshEntity(mob)) {
                        aliveMobs.add(mob.getUUID());
                    }
                }
            }
        }

        private BlockPos findSpawnPos(int radius) {
            Random random = (Random) level.random;
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            BlockPos base = center.offset(dx, 0, dz);
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
        }

        private void pruneDeadMobs() {
            Iterator<UUID> it = aliveMobs.iterator();
            while (it.hasNext()) {
                UUID id = it.next();
                Entity e = level.getEntity(id);
                if (!(e instanceof Mob mob) || !mob.isAlive()) {
                    it.remove();
                }
            }
        }

        private void finishRaid(boolean reward) {
            if (finished) return;
            finished = true;

            RaidAction action = raid.endAction();
            if (action != null) {
                action.run(createContext());
            }

            if (reward) {
                createContext().awardVictoryLoot();
            }
            aliveMobs.clear();
        }

        private RaidContext createContext() {
            return new RaidContextImpl(level, center, raid.difficulty());
        }
    }

    private static final class RaidContextImpl implements RaidContext {
        private final ServerLevel level;
        private final BlockPos center;
        private final float difficulty;

        RaidContextImpl(ServerLevel level, BlockPos center, float difficulty) {
            this.level = level;
            this.center = center;
            this.difficulty = difficulty;
        }

        @Override
        public net.minecraft.server.level.ServerLevel level() {
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
            Vec3 centerVec = Vec3.atCenterOf(center);
            double radius = 64.0D;
            double distSqr = radius * radius;
            level.getPlayers(player -> player.distanceToSqr(centerVec) <= distSqr)
                    .forEach(player -> player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg)));
        }
    }
}
