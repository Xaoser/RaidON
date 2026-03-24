package ru.xaoser.raidon.runtime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import ru.xaoser.raidon.api.sup.RaidSuggestions;
import ru.xaoser.raidon.runtime.config.RaidConfigLoader;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class RaidonCommand {
    private RaidonCommand() {}

    public static void registerDispatcher(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("raidon")
                .requires(stack -> stack.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(RaidSuggestions.RAID_IDS)
                                .executes(ctx -> startRaid(
                                        ctx.getSource(),
                                        ResourceLocationArgument.getId(ctx, "id"),
                                        null
                                ))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> {
                                            ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                            BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
                                            return startRaid(ctx.getSource(), id, pos);
                                        })
                                )
                        )
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> reload(ctx.getSource())))
                .then(Commands.literal("activeRaids")
                        .executes(ctx -> showActive(ctx.getSource())))
                .then(Commands.literal("stopRaid")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(RaidSuggestions.RAID_IDS)
                                .executes(ctx -> stopRaid(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id")))));
    }

    private static int startRaid(CommandSourceStack source, ResourceLocation id, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockPos center = pos != null ? pos : BlockPos.containing(source.getPosition());
        RaidManager.StartResult result = RaidManager.startRaid(id, level, center);
        if (result == RaidManager.StartResult.NOT_FOUND) {
            RaidConfigLoader.load(source.getServer(), ru.xaoser.raidon.Raidon.LOGGER);
            result = RaidManager.startRaid(id, level, center);
        }

        switch (result) {
            case STARTED -> {
                source.sendSuccess(() -> Component.translatable("raidon.command.start.started", id, center), true);
                return 1;
            }
            case ALREADY_ACTIVE -> {
                source.sendFailure(Component.translatable("raidon.command.start.already_active", id));
                return 0;
            }
            case AREA_BUSY -> {
                source.sendFailure(Component.literal("РќРµР»СЊР·СЏ Р·Р°РїСѓСЃС‚РёС‚СЊ СЂРµР№Рґ: РІ СЌС‚РѕР№ РѕР±Р»Р°СЃС‚Рё СѓР¶Рµ РёРґС‘С‚ РґСЂСѓРіРѕР№ СЂРµР№Рґ."));
                return 0;
            }
            case NOT_FOUND -> {
                source.sendFailure(Component.translatable("raidon.command.start.not_found", id));
                return 0;
            }
            default -> {
                return 0;
            }
        }
    }

    private static int reload(CommandSourceStack source) {
        RaidConfigLoader.LoadReport report = RaidConfigLoader.load(source.getServer(), ru.xaoser.raidon.Raidon.LOGGER);
        if (report.hasErrors()) {
            source.sendFailure(Component.translatable("raidon.command.reload.failed", report.loaded(), report.found()));
            for (String error : report.errors()) {
                source.sendFailure(Component.translatable("raidon.command.reload.error_entry", error));
            }
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("raidon.command.reload.success", report.loaded(), report.found()), true);
        return 1;
    }

    private static int showActive(CommandSourceStack source) {
        var active = RaidManager.activeStatuses();
        if (active.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("raidon.command.active.none"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("raidon.command.active.header"), false);
        for (RaidManager.ActiveRaidStatus status : active) {
            int waveDisplay = status.waveIndex() + 1;
            source.sendSuccess(() -> Component.translatable(
                    "raidon.command.active.entry",
                    status.id(),
                    status.center(),
                    waveDisplay,
                    status.totalWaves(),
                    status.aliveInWave(),
                    status.totalInWave()
            ), false);
        }
        return active.size();
    }

    private static int stopRaid(CommandSourceStack source, ResourceLocation id) {
        boolean stopped = RaidManager.stopRaid(id);
        if (stopped) {
            source.sendSuccess(() -> Component.translatable("raidon.command.stop.stopped", id), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("raidon.command.stop.not_found", id));
            return 0;
        }
    }
}
