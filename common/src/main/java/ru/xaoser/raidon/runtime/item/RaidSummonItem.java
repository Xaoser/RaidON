package ru.xaoser.raidon.runtime.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.config.RaidConfigLoader;
import ru.xaoser.raidon.runtime.entity.RaidEntityMemory;
import ru.xaoser.raidon.runtime.raid.RaidManager;

import java.util.List;
import java.util.Optional;

public final class RaidSummonItem extends Item {
    private static final String MEMORY_KEY = "raidonSummonItemUse";
    private static final String TARGET_X_KEY = "targetX";
    private static final String TARGET_Y_KEY = "targetY";
    private static final String TARGET_Z_KEY = "targetZ";

    private final ResourceLocation definitionId;

    public RaidSummonItem(ResourceLocation definitionId) {
        super(properties(definitionId));
        this.definitionId = definitionId;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        RaidSummonItemDefinition definition = definition().orElse(null);
        if (definition == null) {
            return InteractionResult.FAIL;
        }

        if (definition.isChargedUse()) {
            Player player = context.getPlayer();
            if (player == null) {
                return InteractionResult.PASS;
            }
            rememberTarget(player, context.getClickedPos());
            player.startUsingItem(context.getHand());
            return InteractionResult.CONSUME;
        }

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        return trigger(player, context.getItemInHand(), context.getClickedPos());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        RaidSummonItemDefinition definition = definition().orElse(null);
        if (definition == null) {
            return InteractionResultHolder.fail(stack);
        }

        if (definition.isChargedUse()) {
            clearStoredTarget(player);
            player.startUsingItem(usedHand);
            return InteractionResultHolder.consume(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        InteractionResult result = trigger(serverPlayer, stack, serverPlayer.blockPosition());
        return new InteractionResultHolder<>(result, stack);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        definition().ifPresent(definition -> RaidSummonItemStacks.applyDefinition(stack, definition));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        RaidSummonItemDefinition definition = definition().orElse(null);
        return definition != null ? Component.literal(definition.displayName()) : super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return definition().map(RaidSummonItemDefinition::glint).orElse(false) || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        definition().ifPresent(definition -> RaidSummonItemStacks.applyDefinition(stack, definition));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext tooltipContext, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        definition().ifPresent(definition -> appendTooltip(definition, tooltipComponents));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        RaidSummonItemDefinition definition = definition().orElse(null);
        if (definition == null || !definition.isChargedUse()) {
            return super.finishUsingItem(stack, level, livingEntity);
        }

        if (livingEntity instanceof ServerPlayer player) {
            BlockPos target = consumeStoredTarget(player);
            if (target == null) {
                target = player.blockPosition();
            }
            trigger(player, stack, target);
        } else {
            clearStoredTarget(livingEntity);
        }

        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        RaidSummonItemDefinition definition = definition().orElse(null);
        if (definition != null && definition.isChargedUse()) {
            clearStoredTarget(livingEntity);
        }
        super.releaseUsing(stack, level, livingEntity, timeCharged);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return definition().map(RaidSummonItemDefinition::useDurationTicks).orElse(0);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return definition().map(RaidSummonItemDefinition::resolvedUseAnimation).orElse(UseAnim.NONE);
    }

    private InteractionResult trigger(ServerPlayer player, ItemStack stack, BlockPos center) {
        RaidSummonItemDefinition definition = definition().orElse(null);
        if (definition == null) {
            return InteractionResult.FAIL;
        }

        RaidManager.StartResult result = RaidManager.startRaid(definition.raidId(), player.serverLevel(), center);
        if (result == RaidManager.StartResult.NOT_FOUND) {
            RaidConfigLoader.load(player.server, Raidon.LOGGER);
            result = RaidManager.startRaid(definition.raidId(), player.serverLevel(), center);
        }

        if (!result.isStarted()) {
            player.displayClientMessage(failureMessage(definition, result), true);
            return InteractionResult.FAIL;
        }

        if (definition.cooldownTicks() > 0) {
            player.getCooldowns().addCooldown(this, definition.cooldownTicks());
        }

        if (definition.consume() && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResult.SUCCESS;
    }

    private Component failureMessage(RaidSummonItemDefinition definition, RaidManager.StartResult result) {
        return switch (result) {
            case ALREADY_ACTIVE -> Component.translatable("raidon.item.summon.already_active",
                            Component.literal(definition.raidId().toString()))
                    .withStyle(ChatFormatting.RED);
            case AREA_BUSY -> Component.translatable("raidon.item.summon.area_busy",
                            Component.literal(definition.raidId().toString()))
                    .withStyle(ChatFormatting.RED);
            case NOT_FOUND -> Component.translatable("raidon.item.summon.not_found",
                            Component.literal(definition.raidId().toString()))
                    .withStyle(ChatFormatting.RED);
            case STARTED -> Component.empty();
        };
    }

    private Optional<RaidSummonItemDefinition> definition() {
        return RaidSummonItemConfigs.definition(definitionId);
    }

    private static void appendTooltip(RaidSummonItemDefinition definition, List<Component> tooltipComponents) {
        for (String line : definition.description()) {
            tooltipComponents.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }

        if (!definition.description().isEmpty() && !definition.usage().isEmpty()) {
            tooltipComponents.add(Component.empty());
        }
        if (!definition.usage().isEmpty()) {
            tooltipComponents.add(Component.translatable("raidon.item.tooltip.usage")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            for (String line : definition.usage()) {
                tooltipComponents.add(Component.literal(line).withStyle(ChatFormatting.YELLOW));
            }
        }

        if ((!definition.description().isEmpty() || !definition.usage().isEmpty()) && !definition.tooltip().isEmpty()) {
            tooltipComponents.add(Component.empty());
        }
        for (String line : definition.tooltip()) {
            tooltipComponents.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
        }

        if (!tooltipComponents.isEmpty()) {
            tooltipComponents.add(Component.empty());
        }
        tooltipComponents.add(Component.translatable("raidon.item.tooltip.usable")
                .withStyle(ChatFormatting.BLUE));
    }

    private static Item.Properties properties(ResourceLocation definitionId) {
        RaidSummonItemDefinition definition = RaidSummonItemConfigs.definition(definitionId).orElse(null);
        Item.Properties properties = new Item.Properties();
        if (definition == null) {
            return properties.stacksTo(1).rarity(Rarity.COMMON);
        }
        return properties
                .stacksTo(definition.maxStackSize())
                .rarity(definition.resolvedRarity());
    }

    private static void rememberTarget(LivingEntity entity, BlockPos target) {
        CompoundTag useTag = new CompoundTag();
        useTag.putInt(TARGET_X_KEY, target.getX());
        useTag.putInt(TARGET_Y_KEY, target.getY());
        useTag.putInt(TARGET_Z_KEY, target.getZ());
        RaidEntityMemory.of(entity).put(MEMORY_KEY, useTag);
    }

    private static BlockPos consumeStoredTarget(LivingEntity entity) {
        CompoundTag memory = RaidEntityMemory.of(entity);
        if (!memory.contains(MEMORY_KEY)) {
            return null;
        }

        CompoundTag useTag = memory.getCompound(MEMORY_KEY);
        memory.remove(MEMORY_KEY);
        if (!useTag.contains(TARGET_X_KEY) || !useTag.contains(TARGET_Y_KEY) || !useTag.contains(TARGET_Z_KEY)) {
            return null;
        }
        return new BlockPos(
                useTag.getInt(TARGET_X_KEY),
                useTag.getInt(TARGET_Y_KEY),
                useTag.getInt(TARGET_Z_KEY)
        );
    }

    private static void clearStoredTarget(LivingEntity entity) {
        RaidEntityMemory.of(entity).remove(MEMORY_KEY);
    }
}
