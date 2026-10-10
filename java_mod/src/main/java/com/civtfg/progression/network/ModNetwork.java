package com.civtfg.progression.network;

import com.civtfg.progression.ProgressionMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.maven.artifact.versioning.ArtifactVersion;

/**
 * Client/server version check (since 0.9.0). The mod sends no packets of its own, so Forge used
 * to let any client version join - but the Laboratory GUI's synced values changed between
 * versions (0.9.0: seconds instead of ticks, a 6th value), so a mismatched client shows wrong
 * data or errors. This registers an (otherwise unused) channel whose protocol version is the
 * mod's "major.minor" (0.9.0 -> "0.9"): Forge refuses the connection unless both sides have the
 * same major.minor, and a client without the channel (no mod, or 0.8.x and older) is refused too.
 * Fix versions (0.9.0 vs 0.9.1) stay compatible - user's choice, so a fix-only release doesn't
 * force a client update. Bump y (feature) whenever something client-visible changes.
 * The server list check (DisplayTest) uses the same "major.minor" instead of mods.toml's default
 * full-version MATCH_VERSION, so a fix-version difference doesn't show as incompatible there.
 * Must be called from the mod constructor (channels have to exist before the login handshake).
 */
public final class ModNetwork {

    public static SimpleChannel CHANNEL;

    private ModNetwork() {
    }

    public static void register() {
        ArtifactVersion version = ModLoadingContext.get().getActiveContainer().getModInfo().getVersion();
        String protocol = version.getMajorVersion() + "." + version.getMinorVersion();
        CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(ProgressionMod.MOD_ID, "main"),
                () -> protocol, protocol::equals, protocol::equals);
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> protocol, (remote, isServer) -> protocol.equals(remote)));
        ProgressionMod.LOGGER.info("[s3_progression_mod] Network protocol {} (mod version {}) - clients need the same major.minor",
                protocol, version);
    }
}
