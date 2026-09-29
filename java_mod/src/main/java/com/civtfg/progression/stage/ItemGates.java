package com.civtfg.progression.stage;

import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Item-based gate checks shared by the Java-side gates: "crafting"/"gtceu_voltage_crafting"
 * ({@link com.civtfg.progression.mixin.CraftingLockMixin} + its client mirror) and
 * "placement"/"gtceu_voltage_placement" ({@link PlacementGateEnforcer}). The id mechanism
 * matches a gate's {@code blocks} list; the voltage mechanism matches every GTCEU machine
 * item of the gate's {@code voltage} tier, minus its optional {@code exceptBlocks}. Uses
 * GameStageHelper, which works on both logical sides (stages are synced to the client).
 */
public final class ItemGates {

    // GTCEU is only reached reflectively (no compile-time dependency, same as
    // blocked_blocks.js) - all null if GTCEU isn't installed, which just disables the
    // voltage mechanisms. Signatures verified via javap against gtceu-1.20.1-7.5.3.
    @Nullable
    private static final Class<?> META_MACHINE_BLOCK;
    @Nullable
    private static final Method GET_DEFINITION;
    @Nullable
    private static final Method GET_TIER;
    @Nullable
    private static final String[] VOLTAGE_NAMES;

    static {
        Class<?> metaMachineBlock = null;
        Method getDefinition = null;
        Method getTier = null;
        String[] voltageNames = null;
        try {
            metaMachineBlock = Class.forName("com.gregtechceu.gtceu.api.block.MetaMachineBlock");
            getDefinition = metaMachineBlock.getMethod("getDefinition");
            getTier = getDefinition.getReturnType().getMethod("getTier");
            voltageNames = (String[]) Class.forName("com.gregtechceu.gtceu.api.GTValues").getField("VN").get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            metaMachineBlock = null;
        }
        META_MACHINE_BLOCK = metaMachineBlock;
        GET_DEFINITION = getDefinition;
        GET_TIER = getTier;
        VOLTAGE_NAMES = voltageNames;
    }

    @Nullable
    public static String craftingLockedMessage(Player player, ItemStack stack) {
        return lockedMessage(player, stack, "crafting", "gtceu_voltage_crafting");
    }

    @Nullable
    public static String placementLockedMessage(Player player, ItemStack stack) {
        return lockedMessage(player, stack, "placement", "gtceu_voltage_placement");
    }

    /** @return the message of the first gate locking {@code stack} for {@code player}, or null. */
    @Nullable
    private static String lockedMessage(Player player, ItemStack stack, String idMechanism, String voltageMechanism) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        String voltage = null;
        boolean voltageResolved = false;
        for (ProgressionTiers.Gate gate : ProgressionTiers.GATES) {
            boolean byId = idMechanism.equals(gate.mechanism());
            boolean byVoltage = voltageMechanism.equals(gate.mechanism());
            if (!byId && !byVoltage) {
                continue;
            }
            String stageId = ProgressionTiers.stageIdFor(gate.requiresTier());
            if (stageId == null || GameStageHelper.hasStage(player, stageId)) {
                continue;
            }
            if (byId && contains(gate.blocks(), id)) {
                return gate.message();
            }
            if (byVoltage) {
                if (!voltageResolved) {
                    voltage = gtceuVoltageOf(stack);
                    voltageResolved = true;
                }
                if (voltage != null && voltage.equals(gate.voltage()) && !contains(gate.exceptBlocks(), id)) {
                    return gate.message();
                }
            }
        }
        return null;
    }

    /** @return the GTCEU voltage name ("LV", "MV", ...) of a machine item, or null if it isn't one. */
    @Nullable
    private static String gtceuVoltageOf(ItemStack stack) {
        if (META_MACHINE_BLOCK == null || !(stack.getItem() instanceof BlockItem blockItem)
                || !META_MACHINE_BLOCK.isInstance(blockItem.getBlock())) {
            return null;
        }
        try {
            int tier = (int) GET_TIER.invoke(GET_DEFINITION.invoke(blockItem.getBlock()));
            return tier >= 0 && tier < VOLTAGE_NAMES.length ? VOLTAGE_NAMES[tier] : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean contains(@Nullable String[] ids, String id) {
        if (ids == null) {
            return false;
        }
        for (String candidate : ids) {
            if (candidate.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private ItemGates() {
    }
}
