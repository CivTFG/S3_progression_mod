package com.civtfg.progression.blockentity;

import com.civtfg.progression.event.ProgressionEvent;
import com.civtfg.progression.menu.LaboratoryMenu;
import com.civtfg.progression.registry.ModBlockEntities;
import com.civtfg.progression.registry.ModScienceItems;
import com.civtfg.progression.stage.ProgressionTiers;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class LaboratoryBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOT_COUNT = 5;
    public static final int MAX_PROGRESS = 100;

    /**
     * What a tier's laboratory craft resolves to once its 5 slots are read: the tier
     * being progressed and how much its counter advances. Not tied to a fixed recipe -
     * see {@link #getMatchingScience(Level)}.
     */
    private record Match(String tier, int value) {
    }

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
    private String currentTier = null;
    private int currentValue = 0;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> MAX_PROGRESS;
                case 2 -> tierProgress();
                case 3 -> tierThreshold();
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
            return 4;
        }
    };

    /**
     * Research progress toward whichever tier the chunk owning this lab is currently
     * working on - the same team/tier the craft-gating in {@link #getMatchingScience}
     * uses, so this reflects exactly why a craft here is or isn't allowed right now.
     * {@code tierThreshold()} returns -1 once every tier is unlocked (nothing left to
     * show progress for) or if this lab sits in unclaimed territory.
     */
    @Nullable
    private ProgressionTiers.Progress currentTeamProgress() {
        Level level = getLevel();
        if (level == null) {
            return null;
        }
        Team team = ProgressionTiers.resolveTeam(level, getBlockPos());
        return team != null ? ProgressionTiers.currentProgress(team) : null;
    }

    private int tierProgress() {
        ProgressionTiers.Progress teamProgress = currentTeamProgress();
        return teamProgress != null ? teamProgress.current() : 0;
    }

    private int tierThreshold() {
        ProgressionTiers.Progress teamProgress = currentTeamProgress();
        return teamProgress != null ? teamProgress.threshold() : -1;
    }

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

        // Recomputed fresh every tick from the current slot contents (cheap - 5 slots) -
        // its result can only change when contents change, which already reset progress
        // above, so there's no need to track a separate "recipe identity" to detect that.
        Optional<Match> match = be.getMatchingScience(level);

        if (match.isEmpty()) {
            if (be.progress != 0 || be.currentTier != null) {
                be.progress = 0;
                be.currentTier = null;
                be.currentValue = 0;
                dirty = true;
            }
            if (dirty) {
                setChanged(level, pos, state);
            }
            return;
        }

        be.currentTier = match.get().tier();
        be.currentValue = match.get().value();

        be.progress++;
        if (be.progress >= MAX_PROGRESS) {
            be.craft(level, pos);
        }

        setChanged(level, pos, state);
    }

    /**
     * Reads the 5 slots directly instead of matching a fixed recipe: any 1-5 DIFFERENT
     * science items from the same tier are valid, and the value fired advances
     * geometrically with how many distinct sciences are present (1, 2, 4, 8, 16 for
     * 1..5 distinct items) - putting a duplicate category in two slots, or mixing two
     * different tiers' items, invalidates the craft entirely rather than partially
     * counting it.
     */
    private Optional<Match> getMatchingScience(Level level) {
        Map<ModScienceItems.Age, Set<ModScienceItems.Category>> byAge = new EnumMap<>(ModScienceItems.Age.class);

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            Optional<ModScienceItems.Identity> identity = ModScienceItems.identify(stack.getItem());
            if (identity.isEmpty()) {
                return Optional.empty();
            }

            Set<ModScienceItems.Category> categories =
                    byAge.computeIfAbsent(identity.get().age(), a -> EnumSet.noneOf(ModScienceItems.Category.class));
            if (!categories.add(identity.get().category())) {
                return Optional.empty();
            }
        }

        if (byAge.size() != 1) {
            return Optional.empty();
        }

        Map.Entry<ModScienceItems.Age, Set<ModScienceItems.Category>> entry = byAge.entrySet().iterator().next();
        String tierKey = entry.getKey().name();
        int value = 1 << (entry.getValue().size() - 1);

        // Hard gate: a tier is only allowed to start progressing once the team owning
        // this chunk has already crossed the immediately preceding tier's threshold.
        // Rejecting here (rather than in the KubeJS listener after the fact) means
        // out-of-order items are never consumed in the first place.
        Team team = ProgressionTiers.resolveTeam(level, getBlockPos());
        if (team == null || !ProgressionTiers.canCraftTier(team, tierKey)) {
            return Optional.empty();
        }

        return Optional.of(new Match(tierKey, value));
    }

    private void craft(Level level, BlockPos pos) {
        ChunkDimPos chunkDimPos = new ChunkDimPos(level, pos);
        MinecraftForge.EVENT_BUS.post(new ProgressionEvent(chunkDimPos, currentTier, currentValue));

        for (int slot = 0; slot < itemHandler.getSlots(); slot++) {
            ItemStack stack = itemHandler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                // Every science item present is consumed - getMatchingScience already
                // guarantees at most 1 of each category per slot, so a flat shrink(1) is
                // always "consume everything that was inserted".
                stack.shrink(1);
            }
        }

        progress = 0;
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
