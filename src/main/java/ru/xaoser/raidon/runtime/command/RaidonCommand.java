package ru.xaoser.raidon.runtime.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ResourceLocationArgument;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class RaidonCommand {
    private RaidonCommand() {}

    public static void registerDispatcher(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(root());
    }

    private static ArgumentBuilder<CommandSourceStack, ?> root() {
        return Commands.literal("raidon")
                .requires(stack -> stack.hasPermission(2))
                .then(Commands.literal("start")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .executes(ctx -> startRaid(ctx.getSource(), ResourceLocationArgument.getId(ctx, "id"), null))
                                .then(Commands.argument("x", Commands.integer(0))
                                        .then(Commands.argument("y", Commands.integer(0))
                                                .then(Commands.argument("z", Commands.integer(0))
                                                        .executes(ctx -> {
                                                            ResourceLocation id = ResourceLocationArgument.getId(ctx, "id");
                                                            BlockPos pos = new BlockPos(ctx.getArgument("x", Integer.class), ctx.getArgument("y", Integer.class), ctx.getArgument("z", Integer.class));
                                                            return startRaid(ctx.getSource(), id, pos);
                                                        })))))));
    }

    private static int startRaid(CommandSourceStack source, ResourceLocation id, BlockPos pos) {
        ServerLevel level = source.getLevel();
        BlockPos center = pos != null ? pos : BlockPos.containing(source.getPosition());
        boolean started = RaidManager.startRaid(id, level, center);
        if (!started) {
            source.sendFailure(Component.literal("Не удалось запустить рейд " + id + ". Убедитесь, что он загружен и не запущен уже."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Рейд " + id + " запущен в " + center), true);
        return 1;
    }
}
