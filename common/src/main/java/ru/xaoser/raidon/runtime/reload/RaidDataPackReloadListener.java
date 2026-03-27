package ru.xaoser.raidon.runtime.reload;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import ru.xaoser.raidon.Raidon;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class RaidDataPackReloadListener implements PreparableReloadListener {
    private final Supplier<MinecraftServer> serverSupplier;

    public RaidDataPackReloadListener(Supplier<MinecraftServer> serverSupplier) {
        this.serverSupplier = serverSupplier;
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier,
                                          ResourceManager resourceManager,
                                          ProfilerFiller preparationsProfiler,
                                          ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor,
                                          Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> null, backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(ignored -> Raidon.onDataPackReload(serverSupplier.get()), gameExecutor);
    }
}
