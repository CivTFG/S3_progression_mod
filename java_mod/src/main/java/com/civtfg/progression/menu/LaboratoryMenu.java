package com.civtfg.progression.menu;

import com.civtfg.progression.blockentity.LaboratoryBlockEntity;
import com.civtfg.progression.registry.ModBlocks;
import com.civtfg.progression.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class LaboratoryMenu extends AbstractContainerMenu {

    private static final int LAB_SLOT_COUNT = LaboratoryBlockEntity.SLOT_COUNT;

    public final LaboratoryBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client-side constructor, invoked via the registered MenuType factory. */
    public LaboratoryMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveBlockEntity(playerInventory, pos), new SimpleContainerData(4));
    }

    /** Server-side constructor, invoked from LaboratoryBlockEntity#createMenu. */
    public LaboratoryMenu(int containerId, Inventory playerInventory, LaboratoryBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.LABORATORY.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = blockEntity.getLevel() != null
                ? ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                : ContainerLevelAccess.NULL;

        IItemHandler handler = blockEntity.getItemHandler();

        // 5 laboratory slots in a row
        int slotX = 44;
        int slotY = 35;
        for (int i = 0; i < LAB_SLOT_COUNT; i++) {
            addSlot(new SlotItemHandler(handler, i, slotX + i * 18, slotY));
        }

        addPlayerInventorySlots(playerInventory);
        addDataSlots(data);
    }

    private static LaboratoryBlockEntity resolveBlockEntity(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof LaboratoryBlockEntity be) {
            return be;
        }
        throw new IllegalStateException("No LaboratoryBlockEntity found at " + pos);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + col + row * 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getMaxProgress() {
        return data.get(1);
    }

    /** Research banked so far toward the chunk-owning team's current tier. */
    public int getTierProgress() {
        return data.get(2);
    }

    /** That tier's unlock threshold, or -1 if every tier is already unlocked (or the chunk is unclaimed). */
    public int getTierThreshold() {
        return data.get(3);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.LABORATORY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            if (index < LAB_SLOT_COUNT) {
                // moving out of a laboratory slot into player inventory
                if (!moveItemStackTo(stackInSlot, LAB_SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // moving from player inventory into the laboratory slots
                if (!moveItemStackTo(stackInSlot, 0, LAB_SLOT_COUNT, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stackInSlot.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }
}
