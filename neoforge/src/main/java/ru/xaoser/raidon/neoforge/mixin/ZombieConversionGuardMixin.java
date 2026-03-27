package ru.xaoser.raidon.neoforge.mixin;

import net.minecraft.world.entity.monster.Zombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.xaoser.raidon.runtime.raid.ai.MobAiHelper;

@Mixin(Zombie.class)
public abstract class ZombieConversionGuardMixin {
    @Shadow
    protected int conversionTime;

    @Inject(method = "doUnderWaterConversion", at = @At("HEAD"), cancellable = true)
    private void raidon$preventUnderWaterConversion(CallbackInfo ci) {
        if (!MobAiHelper.isRaidMob((Zombie) (Object) this)) {
            return;
        }
        conversionTime = -1;
        ci.cancel();
    }
}
