package com.namedloot.util;

import net.minecraft.client.MinecraftClient;

/**
 * Synthetic partial tick: (time since last world tick) / 50ms, clamped [0,1].
 */
public final class TickDeltaResolver {
    private static long lastWorldTick = Long.MIN_VALUE;
    private static long lastTickNano = System.nanoTime();
    private static float cached = 0f;

    private TickDeltaResolver() {}

    public static float get(MinecraftClient client) {
        if (client.world == null) return 0f;
        long gameTime = client.world.getTime();
        if (gameTime != lastWorldTick) {
            lastWorldTick = gameTime;
            lastTickNano = System.nanoTime();
            cached = 0f;
            return 0f;
        }
        long dt = System.nanoTime() - lastTickNano;
        // 50_000_000 ns ~ 50 ms per tick
        cached = dt <= 0 ? 0f : Math.min(1f, dt / 50_000_000f);
        return cached;
    }
}