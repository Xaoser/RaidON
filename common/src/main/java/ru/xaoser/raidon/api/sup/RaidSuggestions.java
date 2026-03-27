package ru.xaoser.raidon.api.sup;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import ru.xaoser.raidon.Raidon;
import ru.xaoser.raidon.runtime.config.RaidConfigLoader;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class RaidSuggestions {
    private RaidSuggestions() {}

    public static final SuggestionProvider<CommandSourceStack> RAID_IDS =
            (ctx, builder) -> {
                if (RaidManager.raidsView().isEmpty()) {
                    RaidConfigLoader.load(ctx.getSource().getServer(), Raidon.LOGGER);
                }
                return SharedSuggestionProvider.suggestResource(
                        RaidManager.raidsView().keySet(),
                        builder
                );
            };
}
