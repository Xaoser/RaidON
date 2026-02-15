package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;

/**
 * Optional raid points loaded from configuration.
 * <ul>
 *     <li>mainPoint - raid center.</li>
 *     <li>spawnPoint - where mobs appear (defaults to main point).</li>
 *     <li>raidTargetPoint - where mobs move to (defaults to main point).</li>
 * </ul>
 */
public record RaidPointSettings(BlockPos mainPoint, BlockPos spawnPoint, BlockPos raidTargetPoint) {
    public static final RaidPointSettings DEFAULT = new RaidPointSettings(null, null, null);

    public ResolvedPoints resolve(BlockPos fallbackCenter) {
        BlockPos main = mainPoint != null ? mainPoint.immutable() : fallbackCenter.immutable();
        BlockPos spawn = spawnPoint != null ? spawnPoint.immutable() : main;
        BlockPos target = raidTargetPoint != null ? raidTargetPoint.immutable() : main;
        return new ResolvedPoints(main, spawn, target);
    }

    public record ResolvedPoints(BlockPos mainPoint, BlockPos spawnPoint, BlockPos raidTargetPoint) { }
}
