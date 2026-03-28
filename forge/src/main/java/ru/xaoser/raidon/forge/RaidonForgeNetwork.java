package ru.xaoser.raidon.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.network.RaidPacket;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class RaidonForgeNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Objects.requireNonNull(ResourceLocation.tryBuild(Raidon.MODID, "play")),
            () -> RaidNetwork.PROTOCOL_VERSION,
            RaidNetwork.PROTOCOL_VERSION::equals,
            RaidNetwork.PROTOCOL_VERSION::equals
    );
    private static boolean initialized;

    private RaidonForgeNetwork() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        int packetId = 0;
        register(packetId++, RaidProgressS2CPacket.class, RaidProgressS2CPacket::encode, RaidProgressS2CPacket::decode,
                RaidProgressS2CPacket::handle);
        register(packetId++, RaidPlaySoundS2CPacket.class, RaidPlaySoundS2CPacket::encode, RaidPlaySoundS2CPacket::decode,
                RaidPlaySoundS2CPacket::handle);
        register(packetId++, RaidStopSoundS2CPacket.class, RaidStopSoundS2CPacket::encode, RaidStopSoundS2CPacket::decode,
                RaidStopSoundS2CPacket::handle);
    }

    private static <T extends RaidPacket> void register(int packetId, Class<T> type,
                                                        BiConsumer<T, FriendlyByteBuf> encoder,
                                                        Function<FriendlyByteBuf, T> decoder,
                                                        java.util.function.Consumer<T> handler) {
        CHANNEL.registerMessage(packetId, type, encoder, decoder, (payload, contextSupplier) -> {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> handler.accept(payload));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToPlayer(ServerPlayer player, RaidPacket payload) {
        if (player == null || payload == null) {
            return;
        }
        init();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }
}
