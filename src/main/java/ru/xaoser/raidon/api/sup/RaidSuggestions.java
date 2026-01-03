package ru.xaoser.raidon.api.sup;


import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import ru.xaoser.raidon.runtime.raid.RaidManager;

public final class RaidSuggestions {
    private RaidSuggestions() {}

    public static final SuggestionProvider<CommandSourceStack> RAID_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(
                    RaidManager.raidsView().keySet(),
                    builder
            );
}