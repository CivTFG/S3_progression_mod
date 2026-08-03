package com.civtfg.progression.event;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraftforge.eventbus.api.Event;

/**
 * Fired server-side whenever a Laboratory block finishes a recipe.
 *
 * This is a plain, non-cancellable Forge event. It is picked up directly
 * from a KubeJS STARTUP script (the "ForgeEvents" binding only exists there,
 * not in server_scripts) with:
 *
 *   ForgeEvents.onEvent('com.civtfg.progression.event.ProgressionEvent', event => {
 *       let pos = event.pos    // dev.ftb.mods.ftblibrary.math.ChunkDimPos (chunk + dimension, not exact BlockPos)
 *       let value = event.value
 *   })
 *
 * No custom KubeJS-side registration is required - KubeJS 6's ForgeEvents.onEvent
 * can listen to any class posted on the Forge event bus by fully-qualified name.
 */
public class ProgressionEvent extends Event {

    private final ChunkDimPos pos;
    private final int value;

    public ProgressionEvent(ChunkDimPos pos, int value) {
        this.pos = pos;
        this.value = value;
    }

    public ChunkDimPos getPos() {
        return pos;
    }

    public int getValue() {
        return value;
    }
}
