package ru.xaoser.raidon.runtime.raid;

import net.minecraft.core.BlockPos;

public record RaidPointSettings(PointTemplate mainPoint, PointTemplate spawnPoint, PointTemplate raidTargetPoint, Integer mobWanderRadius) {
    public static final RaidPointSettings DEFAULT = new RaidPointSettings(null, null, null, null);

    public ResolvedPoints resolve(BlockPos currentCenter) {
        BlockPos main = mainPoint != null ? mainPoint.resolve(currentCenter).immutable() : currentCenter.immutable();
        BlockPos spawn = spawnPoint != null ? spawnPoint.resolve(currentCenter).immutable() : main;
        BlockPos target = raidTargetPoint != null ? raidTargetPoint.resolve(currentCenter).immutable() : main;
        int wanderRadius = mobWanderRadius != null && mobWanderRadius > 0 ? mobWanderRadius : 50;
        return new ResolvedPoints(main, spawn, target, wanderRadius);
    }

    public record PointTemplate(AxisValue x, AxisValue y, AxisValue z) {
        public BlockPos resolve(BlockPos currentCenter) {
            return new BlockPos(
                    x.resolve(currentCenter.getX()),
                    y.resolve(currentCenter.getY()),
                    z.resolve(currentCenter.getZ())
            );
        }
    }

    public record AxisValue(Integer absolute, int currentOffset) {
        public static AxisValue absolute(int value) {
            return new AxisValue(value, 0);
        }

        public static AxisValue current(int offset) {
            return new AxisValue(null, offset);
        }

        public int resolve(int current) {
            return absolute != null ? absolute : current + currentOffset;
        }
    }

    public record ResolvedPoints(BlockPos mainPoint, BlockPos spawnPoint, BlockPos raidTargetPoint, int mobWanderRadius) { }
}
