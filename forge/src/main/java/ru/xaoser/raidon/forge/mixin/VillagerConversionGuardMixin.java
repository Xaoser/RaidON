package ru.xaoser.raidon.forge.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.xaoser.raidon.runtime.raid.ai.MobAiHelper;

@Mixin(net.minecraft.world.entity.npc.Villager.class)
public abstract class VillagerConversionGuardMixin extends AbstractVillager {
    protected VillagerConversionGuardMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "thunderHit", at = @At("HEAD"), cancellable = true)
    private void raidon$preventLightningConversion(ServerLevel level, LightningBolt lightningBolt, CallbackInfo ci) {
        if (!MobAiHelper.isRaidMob(this)) {
            return;
        }
        super.thunderHit(level, lightningBolt);
        ci.cancel();
    }
}
