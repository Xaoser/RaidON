package ru.xaoser.raidon.fabric.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.xaoser.raidon.fabric.RaidonFabricHooks;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique
    private ItemStack raidon$beforePickup = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"))
    private void raidon$capturePickup(Player player, CallbackInfo ci) {
        if (!player.level().isClientSide()) {
            raidon$beforePickup = ((ItemEntity) (Object) this).getItem().copy();
        }
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void raidon$afterPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer) || raidon$beforePickup.isEmpty()) {
            raidon$beforePickup = ItemStack.EMPTY;
            return;
        }

        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack current = self.getItem();
        int afterCount = current.is(raidon$beforePickup.getItem()) ? current.getCount() : 0;
        int pickedCount = Math.max(0, raidon$beforePickup.getCount() - afterCount);
        if (pickedCount > 0) {
            ItemStack pickedStack = raidon$beforePickup.copy();
            pickedStack.setCount(pickedCount);
            RaidonFabricHooks.onItemPickup(serverPlayer, pickedStack);
        }

        raidon$beforePickup = ItemStack.EMPTY;
    }
}
