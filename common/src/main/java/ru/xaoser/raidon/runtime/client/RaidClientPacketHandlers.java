package ru.xaoser.raidon.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;

import java.util.HashMap;
import java.util.Map;

public final class RaidClientPacketHandlers {
    private static final Map<String, RaidZoneLimitedSoundInstance> ACTIVE_ZONE_SOUNDS = new HashMap<>();
    private static String activeRaidMusicZoneKey;
    private static Double suppressedRecordsVolume;

    private RaidClientPacketHandlers() {
    }

    public static void handleRaidProgress(RaidProgressS2CPacket payload) {
        Minecraft.getInstance().execute(() -> RaidHudState.update(payload.progress()));
    }

    public static void handlePlaySound(RaidPlaySoundS2CPacket payload) {
        Minecraft.getInstance().execute(() -> playClientSound(payload));
    }

    public static void handleStopSound(RaidStopSoundS2CPacket payload) {
        Minecraft.getInstance().execute(() -> stopManagedZoneSound(Minecraft.getInstance(), zoneKey(
                payload.zoneX(),
                payload.zoneY(),
                payload.zoneZ(),
                payload.zoneRadius()
        )));
    }

    public static void resetTransientState() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            ACTIVE_ZONE_SOUNDS.values().forEach(sound -> minecraft.getSoundManager().stop(sound));
            ACTIVE_ZONE_SOUNDS.clear();
            activeRaidMusicZoneKey = null;
            restoreSuppressedRecordsVolume(minecraft);
        });
    }

    private static void playClientSound(RaidPlaySoundS2CPacket payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        pruneInactiveZoneSounds(minecraft);
        if (payload.zoneLimited()) {
            playZoneLimitedSound(minecraft, payload);
            return;
        }

        minecraft.getSoundManager().play(new SimpleSoundInstance(
                payload.soundId(),
                payload.source(),
                clamp(payload.volume(), 0.0F, 64.0F),
                clamp(payload.pitch(), 0.0F, 4.0F),
                RandomSource.create(),
                false,
                0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                0.0D,
                0.0D,
                0.0D,
                true
        ));
    }

    private static void playZoneLimitedSound(Minecraft minecraft, RaidPlaySoundS2CPacket payload) {
        String key = zoneKey(payload.zoneX(), payload.zoneY(), payload.zoneZ(), payload.zoneRadius());
        boolean exclusiveMusic = payload.source() == SoundSource.MUSIC;
        if (exclusiveMusic) {
            stopActiveRaidMusicIfDifferent(minecraft, key);
        }

        RaidZoneLimitedSoundInstance existing = ACTIVE_ZONE_SOUNDS.get(key);
        float volume = clamp(payload.volume(), 0.0F, 64.0F);
        float pitch = clamp(payload.pitch(), 0.0F, 4.0F);
        if (isSameManagedSound(minecraft, existing, payload, volume, pitch)) {
            return;
        }

        stopManagedZoneSound(minecraft, key);
        if (exclusiveMusic) {
            suppressCompetingMusic(minecraft);
        }

        RaidZoneLimitedSoundInstance sound = new RaidZoneLimitedSoundInstance(
                payload.soundId(),
                payload.source(),
                volume,
                pitch,
                payload.zoneX(),
                payload.zoneY(),
                payload.zoneZ(),
                payload.zoneRadius(),
                exclusiveMusic
        );
        ACTIVE_ZONE_SOUNDS.put(key, sound);
        if (exclusiveMusic) {
            activeRaidMusicZoneKey = key;
        }
        minecraft.getSoundManager().play(sound);
    }

    private static void pruneInactiveZoneSounds(Minecraft minecraft) {
        ACTIVE_ZONE_SOUNDS.entrySet().removeIf(entry -> {
            RaidZoneLimitedSoundInstance sound = entry.getValue();
            boolean remove = sound == null || sound.isStopped() || !minecraft.getSoundManager().isActive(sound);
            if (remove && entry.getKey().equals(activeRaidMusicZoneKey)) {
                activeRaidMusicZoneKey = null;
                restoreSuppressedRecordsVolume(minecraft);
            }
            return remove;
        });
    }

    private static boolean isSameManagedSound(Minecraft minecraft, RaidZoneLimitedSoundInstance existing,
                                               RaidPlaySoundS2CPacket payload, float volume, float pitch) {
        return existing != null
                && !existing.isStopped()
                && minecraft.getSoundManager().isActive(existing)
                && existing.getLocation().equals(payload.soundId())
                && existing.getSource() == payload.source()
                && Math.abs(existing.getVolume() - volume) < 0.001F
                && Math.abs(existing.getPitch() - pitch) < 0.001F;
    }

    private static void stopActiveRaidMusicIfDifferent(Minecraft minecraft, String key) {
        if (activeRaidMusicZoneKey == null || activeRaidMusicZoneKey.equals(key)) {
            return;
        }
        stopManagedZoneSound(minecraft, activeRaidMusicZoneKey);
    }

    private static void stopManagedZoneSound(Minecraft minecraft, String key) {
        if (key == null) {
            return;
        }
        RaidZoneLimitedSoundInstance sound = ACTIVE_ZONE_SOUNDS.remove(key);
        if (sound != null) {
            minecraft.getSoundManager().stop(sound);
        }
        if (key.equals(activeRaidMusicZoneKey)) {
            activeRaidMusicZoneKey = null;
            restoreSuppressedRecordsVolume(minecraft);
        }
    }

    private static void suppressCompetingMusic(Minecraft minecraft) {
        minecraft.getMusicManager().stopPlaying();
        if (suppressedRecordsVolume == null) {
            suppressedRecordsVolume = (double) minecraft.options.getSoundSourceVolume(SoundSource.RECORDS);
        }
        minecraft.getSoundManager().updateSourceVolume(SoundSource.RECORDS, 0.0F);
    }

    private static void restoreSuppressedRecordsVolume(Minecraft minecraft) {
        if (suppressedRecordsVolume == null) {
            return;
        }
        double restoreValue = suppressedRecordsVolume;
        suppressedRecordsVolume = null;
        minecraft.getSoundManager().updateSourceVolume(SoundSource.RECORDS, (float) restoreValue);
    }

    private static String zoneKey(double zoneX, double zoneY, double zoneZ, float zoneRadius) {
        return zoneX + "|" + zoneY + "|" + zoneZ + "|" + zoneRadius;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
