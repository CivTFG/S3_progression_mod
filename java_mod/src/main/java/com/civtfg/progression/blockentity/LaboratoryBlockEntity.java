package com.civtfg.progression.blockentity;

import com.civtfg.progression.event.ProgressionEvent;
import com.civtfg.progression.menu.LaboratoryMenu;
import com.civtfg.progression.recipe.LaboratoryRecipe;
import com.civtfg.progression.recipe.ModRecipeTypes;
import com.civtfg.progression.registry.ModBlockEntities;
import com.civtfg.progression.stage.ProgressionTiers;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class LaboratoryBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_COUNT = 5;
    public static final int MAX_PROGRESS = 100;

    private final ItemStackHandler itemHandler = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            contentsChanged = true;
            setChanged();
        }
    };

    private final LazyOptional<IItemHandler> itemHandlerOptional = LazyOptional.of(() -> itemHandler);

    private boolean contentsChanged = false;
    private int progress = 0;
    @Nullable
    private ResourceLocation currentRecipeId = null;
    @Nullable
    private String currentTier = null;
    private int currentValue = 0;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> MAX_PROGRESS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public LaboratoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LABORATORY.get(), pos, state);
    }

    // ----------------------------------------------------------------
    // Ticking / recipe processing
    // ----------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaboratoryBlockEntity be) {
        boolean dirty = false;

        // A slot was emptied or filled since the last tick: cancel whatever was in progress.
        if (be.contentsChanged) {
            be.progress = 0;
            be.contentsChanged = false;
            dirty = true;
        }

        Optional<LaboratoryRecipe> match = be.getMatchingRecipe(level);

        if (match.isEmpty()) {
            if (be.progress != 0 || be.currentRecipeId != null) {
                be.progress = 0;
                be.currentRecipeId = null;
                be.currentTier = null;
                be.currentValue = 0;
                dirty = true;
            }
            if (dirty) {
                setChanged(level, pos, state);
            }
            return;
        }

        LaboratoryRecipe recipe = match.get();
        if (!recipe.getId().equals(be.currentRecipeId)) {
            be.currentRecipeId = recipe.getId();
            be.currentTier = recipe.getTier();
            be.currentValue = recipe.getValue();
            be.progress = 0;
        }

        be.progress++;
        if (be.progress >= MAX_PROGRESS) {
            be.craft(level, pos);
        }

        setChanged(level, pos, state);
    }

    private Optional<LaboratoryRecipe> getMatchingRecipe(Level level) {
        RecipeWrapper wrapper = new RecipeWrapper(itemHandler);
        Optional<LaboratoryRecipe> match =
                level.getRecipeManager().getRecipeFor(ModRecipeTypes.LABORATORY_TYPE.get(), wrapper, level);
        if (match.isEmpty()) {
            return match;
        }

        // Hard gate: a tier's recipe is only allowed to start progressing once the
        // team owning this chunk has already crossed the immediately preceding tier's
        // threshold. Rejecting here (rather than in the KubeJS listener after the fact)
        // means out-of-order items are never consumed in the first place.
        Team team = ProgressionTiers.resolveTeam(level, getBlockPos());
        if (team == null || !ProgressionTiers.canCraftTier(team, match.get().getTier())) {
            return Optional.empty();
        }

        return match;
    }

    private void craft(Level level, BlockPos pos) {
        ChunkDimPos chunkDimPos = new ChunkDimPos(level, pos);
        MinecraftForge.EVENT_BUS.post(new ProgressionEvent(chunkDimPos, currentTier, currentValue));

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                // v1 recipes only ever need 1 item per slot; if a future datapack recipe
                // needs more than 1 of an ingredient in a slot, this needs to shrink by
                // that ingredient's count instead of a flat 1.
                stack.shrink(1);
            }
        }

        progress = 0;
        currentRecipeId = null;
        currentTier = null;
        currentValue = 0;
    }

    // ----------------------------------------------------------------
    // Inventory / capability
    // ----------------------------------------------------------------

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerOptional.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerOptional.invalidate();
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    public void dropContents(Level level, BlockPos pos) {
        NonNullList<ItemStack> stacks = NonNullList.create();
        for (int i = 0; i < itemHandler.getSlots(); i++) {
            stacks.add(itemHandler.getStackInSlot(i));
        }
        Containers.dropContents(level, pos, stacks);
    }

    // ----------------------------------------------------------------
    // Save / load (items persist, progress intentionally does not)
    // ----------------------------------------------------------------

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", itemHandler.serializeNBT());
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) {
            itemHandler.deserializeNBT(tag.getCompound("Inventory"));
        }
    }

    // ----------------------------------------------------------------
    // MenuProvider
    // ----------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.s3_progression_mod.laboratory");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new LaboratoryMenu(containerId, playerInventory, this, data);
    }
}
