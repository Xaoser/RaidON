package ru.xaoser.raidon.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

final class RaidZoneLimitedSoundInstance extends AbstractTickableSoundInstance {
    private final Minecraft minecraft;
    private final double zoneX;
    private final double zoneY;
    private final double zoneZ;
    private final double zoneRadiusSq;
    private final boolean exclusiveMusic;

    RaidZoneLimitedSoundInstance(ResourceLocation soundId, SoundSource source, float volume, float pitch,
                                 double zoneX, double zoneY, double zoneZ, float zoneRadius, boolean exclusiveMusic) {
        super(SoundEvent.createVariableRangeEvent(soundId), source, RandomSource.create());
        this.minecraft = Minecraft.getInstance();
        this.zoneX = zoneX;
        this.zoneY = zoneY;
        this.zoneZ = zoneZ;
        double clampedRadius = Math.max(0.0D, zoneRadius);
        this.zoneRadiusSq = clampedRadius * clampedRadius;
        this.exclusiveMusic = exclusiveMusic;
        this.volume = volume;
        this.pitch = pitch;
        this.looping = false;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = true;
        this.x = zoneX;
        this.y = zoneY;
        this.z = zoneZ;
    }

    @Override
    public void tick() {
        if (!canPlaySound()) {
            stop();
            return;
        }

        if (exclusiveMusic) {
            minecraft.getMusicManager().stopPlaying();
        }
    }

    @Override
    public boolean canPlaySound() {
        return minecraft.level != null
                && minecraft.player != null
                && minecraft.player.distanceToSqr(zoneX, zoneY, zoneZ) <= zoneRadiusSq;
    }
}
