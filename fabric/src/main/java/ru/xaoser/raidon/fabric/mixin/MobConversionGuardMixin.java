package ru.xaoser.raidon.fabric.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.xaoser.raidon.runtime.raid.ai.MobAiHelper;

@Mixin(Mob.class)
public abstract class MobConversionGuardMixin {
    @Inject(method = "convertTo", at = @At("HEAD"), cancellable = true)
    private <T extends Mob> void raidon$preventRaidConversion(EntityType<T> entityType, boolean keepEquipment,
                                                              CallbackInfoReturnable<T> cir) {
        if (MobAiHelper.isRaidMob((Mob) (Object) this)) {
            cir.setReturnValue(null);
        }
    }
}
