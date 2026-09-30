package com.civtfg.progression.menu;

import com.civtfg.progression.blockentity.PrimitiveAssemblerBlockEntity;
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
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.SlotItemHandler;

public class PrimitiveAssemblerMenu extends AbstractContainerMenu {

    public final PrimitiveAssemblerBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final ContainerData data;

    /** Client-side constructor, invoked via the registered MenuType factory. */
    public PrimitiveAssemblerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, resolveBlockEntity(playerInventory, pos), new SimpleContainerData(4));
    }

    /** Server-side constructor, invoked from PrimitiveAssemblerBlockEntity#createMenu. */
    public PrimitiveAssemblerMenu(int containerId, Inventory playerInventory, PrimitiveAssemblerBlockEntity blockEntity, ContainerData data) {
        super(ModMenuTypes.PRIMITIVE_ASSEMBLER.get(), containerId);
        this.blockEntity = blockEntity;
        this.data = data;
        this.access = blockEntity.getLevel() != null
                ? ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos())
                : ContainerLevelAccess.NULL;

        IItemHandler handler = blockEntity.getItemHandler();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(handler, col + row * 3, 30 + col * 18, 17 + row * 18));
            }
        }
        addSlot(new SlotItemHandler(handler, PrimitiveAssemblerBlockEntity.OUTPUT_SLOT, 124, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, 9 + col + row * 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
        addDataSlots(data);
    }

    private static PrimitiveAssemblerBlockEntity resolveBlockEntity(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof PrimitiveAssemblerBlockEntity be) {
            return be;
        }
        throw new IllegalStateException("No PrimitiveAssemblerBlockEntity found at " + pos);
    }

    public int getProgress() {
        return data.get(0);
    }

    /** Current recipe's duration in ticks, 0 when nothing is running. */
    public int getMaxProgress() {
        return data.get(1);
    }

    public int getFluidAmount() {
        return data.get(2);
    }

    /** Raw registry id of the tank's fluid, -1 when empty. */
    public int getFluidId() {
        return data.get(3);
    }

    /** Button id sent by the screen when the tank is clicked with an item on the cursor. */
    public static final int BUTTON_TANK_CLICK = 0;

    /**
     * Clicking the tank with a fluid container (bucket, barrel, tank item...) on the cursor:
     * empties it into the tank, or - if it can't be emptied (it's empty, or the tank is
     * full/other fluid) - fills it from the tank. Emptied containers are stowed in the
     * player's inventory / replace the stack on the cursor, same as a bucket-on-block click.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_TANK_CLICK) {
            return false;
        }
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        InvWrapper playerInv = new InvWrapper(player.getInventory());
        FluidActionResult result = FluidUtil.tryEmptyContainerAndStow(
                carried, blockEntity.getTank(), playerInv, Integer.MAX_VALUE, player, true);
        if (!result.isSuccess()) {
            result = FluidUtil.tryFillContainerAndStow(
                    carried, blockEntity.getTank(), playerInv, Integer.MAX_VALUE, player, true);
        }
        if (result.isSuccess()) {
            setCarried(result.getResult());
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, blockEntity.getBlockState().getBlock());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            result = stackInSlot.copy();

            if (index < PrimitiveAssemblerBlockEntity.SLOT_COUNT) {
                if (!moveItemStackTo(stackInSlot, PrimitiveAssemblerBlockEntity.SLOT_COUNT, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, 0, PrimitiveAssemblerBlockEntity.INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
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
