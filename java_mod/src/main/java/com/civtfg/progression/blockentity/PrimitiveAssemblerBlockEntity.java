package com.civtfg.progression.blockentity;

import com.civtfg.progression.block.PrimitiveAssemblerBlock;
import com.civtfg.progression.menu.PrimitiveAssemblerMenu;
import com.civtfg.progression.recipe.PrimitiveAssemblerRecipe;
import com.civtfg.progression.registry.ModBlockEntities;
import com.civtfg.progression.registry.ModRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Unpowered assembler: 9 item input slots, 1 output slot, one fluid input tank. Runs the
 * best-matching {@link PrimitiveAssemblerRecipe} (most item inputs wins, so a more specific
 * recipe beats one that is a subset of it) for that recipe's duration, with no energy.
 */
public class PrimitiveAssemblerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOT = 9;
    public static final int SLOT_COUNT = 10;
    public static final int TANK_CAPACITY = 8000;

    private final ItemStackHandler itemHandler = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            recheck = true;
            setChanged();
        }
    };

    private final FluidTank tank = new FluidTank(TANK_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            recheck = true;
            setChanged();
        }
    };

    /** What pipes/hoppers/etc. see: inputs can only be inserted, the output can only be extracted. */
    private final IItemHandler automationHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return itemHandler.getStackInSlot(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot < INPUT_SLOTS ? itemHandler.insertItem(slot, stack, simulate) : stack;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == OUTPUT_SLOT ? itemHandler.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return itemHandler.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot < INPUT_SLOTS && itemHandler.isItemValid(slot, stack);
        }
    };

    /** Fluid side of automation: fill only - nothing can pump the input tank back out. */
    private final IFluidHandler automationFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int t) {
            return tank.getFluidInTank(t);
        }

        @Override
        public int getTankCapacity(int t) {
            return tank.getTankCapacity(t);
        }

        @Override
        public boolean isFluidValid(int t, @NotNull FluidStack stack) {
            return tank.isFluidValid(t, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return tank.fill(resource, action);
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> automationHandler);
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> automationFluidHandler);

    private boolean recheck = true;
    private int progress = 0;
    @Nullable
    private PrimitiveAssemblerRecipe currentRecipe = null;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> currentRecipe != null ? currentRecipe.getDuration() : 0;
                case 2 -> tank.getFluidAmount();
                case 3 -> tank.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(tank.getFluid().getFluid());
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public PrimitiveAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRIMITIVE_ASSEMBLER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PrimitiveAssemblerBlockEntity be) {
        if (be.recheck) {
            be.recheck = false;
            PrimitiveAssemblerRecipe found = be.findRecipe(level);
            if (found != be.currentRecipe) {
                be.progress = 0;
            }
            be.currentRecipe = found;
        }

        boolean working = false;
        PrimitiveAssemblerRecipe recipe = be.currentRecipe;
        if (recipe != null) {
            // Output slot full (or holding a different item): pause, keep progress.
            if (be.itemHandler.insertItem(OUTPUT_SLOT, recipe.getOutput().copy(), true).isEmpty()) {
                working = true;
                be.progress++;
                if (be.progress >= recipe.getDuration()) {
                    recipe.consume(be.itemHandler, INPUT_SLOTS, be.tank);
                    be.itemHandler.insertItem(OUTPUT_SLOT, recipe.getOutput().copy(), false);
                    be.progress = 0;
                    be.recheck = true;
                }
                be.setChanged();
            }
        } else if (be.progress != 0) {
            be.progress = 0;
        }

        if (state.getValue(PrimitiveAssemblerBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(PrimitiveAssemblerBlock.WORKING, working), 3);
        }
    }

    @Nullable
    private PrimitiveAssemblerRecipe findRecipe(Level level) {
        PrimitiveAssemblerRecipe best = null;
        for (PrimitiveAssemblerRecipe candidate : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.PRIMITIVE_ASSEMBLER.get())) {
            if (candidate.matches(itemHandler, INPUT_SLOTS, tank.getFluid())
                    && (best == null || candidate.getInputs().size() > best.getInputs().size())) {
                best = candidate;
            }
        }
        return best;
    }

    // ----------------------------------------------------------------
    // Inventory / capability
    // ----------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        fluidCap.invalidate();
    }

    /** Raw handler for the GUI (full access, unlike the automation view). */
    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    /** Raw tank, used for player bucket interaction (fill and drain). */
    public FluidTank getTank() {
        return tank;
    }

    public void dropContents(Level level, BlockPos pos) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            stacks.add(itemHandler.getStackInSlot(i));
        }
        Containers.dropContents(level, pos, stacks);
    }

    // ----------------------------------------------------------------
    // Save / load (items and fluid persist, progress intentionally does not)
    // ----------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }
        if (tag.contains("Tank")) {
            tank.readFromNBT(tag.getCompound("Tank"));
        }
        recheck = true;
    }

    // ----------------------------------------------------------------
    // MenuProvider
    // ----------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PrimitiveAssemblerMenu(containerId, playerInventory, this, data);
    }
}
