package ru.xaoser.raidon.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.xaoser.raidon.fabric.RaidonFabricHooks;

@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
    @Inject(method = "onTake", at = @At("TAIL"))
    private void raidon$afterTrade(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && !player.level().isClientSide() && !stack.isEmpty()) {
            RaidonFabricHooks.onTrade(serverPlayer, stack.copy());
        }
    }
}
