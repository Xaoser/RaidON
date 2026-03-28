package ru.xaoser.raidon.runtime.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface RaidPacket {
    ResourceLocation id();

    void encode(FriendlyByteBuf buf);
}
