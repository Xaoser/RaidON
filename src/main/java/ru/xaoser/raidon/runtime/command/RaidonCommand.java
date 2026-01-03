package ru.xaoser.raidon.runtime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
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
                        .executes(ctx -> reload(ctx.getSource())));
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
                source.sendSuccess(() -> Component.literal("Рейд " + id + " запущен в " + center), true);
                return 1;
            }
            case ALREADY_ACTIVE -> {
                source.sendFailure(Component.literal("Рейд " + id + " уже активен."));
                return 0;
            }
            case NOT_FOUND -> {
                source.sendFailure(Component.literal("Рейд " + id + " не найден. Проверьте конфиг в config/raidon/raids/."));
                return 0;
            }
            default -> {
                return 0;
            }
        }
    }

    private static int reload(CommandSourceStack source) {
        RaidConfigLoader.load(source.getServer(), ru.xaoser.raidon.Raidon.LOGGER);
        source.sendSuccess(() -> Component.literal("Рейды перезагружены из конфигов."), true);
        return 1;
    }
}
