package ru.xaoser.raidon;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Raidon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {

    }
}
