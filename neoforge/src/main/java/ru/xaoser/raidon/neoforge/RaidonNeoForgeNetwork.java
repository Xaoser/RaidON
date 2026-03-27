package ru.xaoser.raidon.neoforge;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.xaoser.raidon.runtime.network.RaidNetwork;
import ru.xaoser.raidon.runtime.network.packet.RaidPlaySoundS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidProgressS2CPacket;
import ru.xaoser.raidon.runtime.network.packet.RaidStopSoundS2CPacket;

public final class RaidonNeoForgeNetwork {
    private RaidonNeoForgeNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(RaidNetwork.PROTOCOL_VERSION);
        registrar.playToClient(
                RaidProgressS2CPacket.TYPE,
                RaidProgressS2CPacket.STREAM_CODEC,
                (payload, context) -> RaidProgressS2CPacket.handle(payload)
        );
        registrar.playToClient(
                RaidPlaySoundS2CPacket.TYPE,
                RaidPlaySoundS2CPacket.STREAM_CODEC,
                (payload, context) -> RaidPlaySoundS2CPacket.handle(payload)
        );
        registrar.playToClient(
                RaidStopSoundS2CPacket.TYPE,
                RaidStopSoundS2CPacket.STREAM_CODEC,
                (payload, context) -> RaidStopSoundS2CPacket.handle(payload)
        );
    }
}
