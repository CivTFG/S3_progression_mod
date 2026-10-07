package com.civtfg.progression.block;

import com.civtfg.progression.blockentity.LaboratoryBlockEntity;
import com.civtfg.progression.registry.ModBlockEntities;
import com.civtfg.progression.registry.ModScienceItems;
import com.civtfg.progression.stage.ProgressionTiers;
import dev.ftb.mods.ftbteams.api.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class LaboratoryBlock extends BaseEntityBlock {

    /**
     * Which of the 5 laboratory block variants this is, and which science Ages it's
     * allowed to research - see {@link LaboratoryBlockEntity#getMatchingScience(Level)},
     * which rejects a craft outright if the single Age present among its slotted items
     * isn't in {@link #allowedAges}. Independent of the ACTIVE/"out of order" mechanic
     * below: that one is about which physical lab (of a given tier) is functional, this
     * one is about which items a functional lab of that tier will accept.
     *
     * <p>Cumulative, not exclusive: each successive tier's set is the previous tier's set
     * plus its own new pair of Ages, so e.g. Industrial still researches Bronze/Iron items
     * too, not just Steel/Steam - "every lab from here on can do more, not switch to a
     * different two ages". {@code ELITE} is simply every Age, since Mars is the last one
     * (IV was removed as a research tier entirely - crossing Mars now unlocks everything).
     */
    public enum LabTier {
        PRIMITIVE(EnumSet.of(
                ModScienceItems.Age.BRONZE, ModScienceItems.Age.IRON)),
        INDUSTRIAL(EnumSet.of(
                ModScienceItems.Age.BRONZE, ModScienceItems.Age.IRON,
                ModScienceItems.Age.STEEL, ModScienceItems.Age.STEAM)),
        ELECTRIC(EnumSet.of(
                ModScienceItems.Age.BRONZE, ModScienceItems.Age.IRON,
                ModScienceItems.Age.STEEL, ModScienceItems.Age.STEAM,
                ModScienceItems.Age.LV, ModScienceItems.Age.MV)),
        ADVANCED(EnumSet.of(
                ModScienceItems.Age.BRONZE, ModScienceItems.Age.IRON,
                ModScienceItems.Age.STEEL, ModScienceItems.Age.STEAM,
                ModScienceItems.Age.LV, ModScienceItems.Age.MV,
                ModScienceItems.Age.HV, ModScienceItems.Age.MOON)),
        ELITE(EnumSet.allOf(ModScienceItems.Age.class));

        private final Set<ModScienceItems.Age> allowedAges;

        LabTier(Set<ModScienceItems.Age> allowedAges) {
            this.allowedAges = allowedAges;
        }

        public boolean allows(ModScienceItems.Age age) {
            return allowedAges.contains(age);
        }
    }

    /**
     * Whether this particular lab is THE team's one functional Laboratory (see
     * {@link #getStateForPlacement} and {@link ProgressionTiers#hasLaboratory}). A lab
     * placed in a team's claim becomes ACTIVE if the team has no working lab or if it is a
     * higher {@link LabTier} than the current one - which {@link #setPlacedBy} then switches
     * to "out of order" (an upgrade; since 0.8.0). Every other placement (unclaimed chunk,
     * same or lower tier) is an inert decorative copy: no GUI, no ticking, just an "out of
     * order" message on right-click. A team gets exactly **one** active lab total, not one per tier - this
     * was briefly one-per-tier (see Pitfall #16), reverted per the user's explicit
     * correction (Pitfall #17). All variants share the same model/loot table structure per
     * variant, so a broken decorative lab still drops - and can be re-placed as - a normal
     * laboratory item of that same variant.
     */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private final LabTier tier;

    public LaboratoryBlock(Properties properties, LabTier tier) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, true));
    }

    public LabTier getTier() {
        return tier;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            // FTBChunksAPI's manager is only usable server-side - calling resolveTeam here
            // NPEs on the client (crash confirmed via FTBChunksAPIImpl#getManager), since
            // this method runs on both sides (client for placement prediction, server for
            // the real placement). The client's guess is only ever used for a moment before
            // the server's real state syncs back, so a fixed default is harmless here.
            return defaultBlockState();
        }
        Team team = ProgressionTiers.resolveTeam(level, context.getClickedPos());
        if (team == null) {
            return defaultBlockState().setValue(ACTIVE, false);
        }
        ActiveLab current = findActiveLab(team, level);
        // Active if the team has no working lab (none recorded, or the recorded one is gone)
        // or this one is a higher tier - setPlacedBy then deactivates the old one.
        boolean active = current == null || current.tier().ordinal() < tier.ordinal();
        return defaultBlockState().setValue(ACTIVE, active);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && state.getValue(ACTIVE)) {
            Team team = ProgressionTiers.resolveTeam(level, pos);
            if (team != null) {
                // Side effects only here, not in getStateForPlacement (that one also runs
                // for placements that end up failing).
                ActiveLab previous = findActiveLab(team, level);
                if (previous != null && !(previous.level() == level && previous.pos().equals(pos))) {
                    previous.level().setBlock(previous.pos(), previous.state().setValue(ACTIVE, false), Block.UPDATE_ALL);
                    if (placer instanceof Player player) {
                        player.displayClientMessage(Component.translatable(
                                "block.s3_progression_mod.laboratory.replaced",
                                previous.state().getBlock().getName(),
                                previous.pos().getX(), previous.pos().getY(), previous.pos().getZ()), false);
                    }
                }
                ProgressionTiers.setHasLaboratory(team, level, pos, tier);
            }
        }
    }

    /** The team's recorded functional lab, as found in the world. */
    private record ActiveLab(Level level, BlockPos pos, BlockState state, LabTier tier) {
    }

    /**
     * @return {@code team}'s recorded functional Laboratory, or {@code null} if there is none
     * or the record is stale (no ACTIVE lab at the recorded position anymore - unclaimed
     * before it was broken, rollback, ...) or has no position (pre-0.5.0 boolean record).
     * Server-side only; loads the recorded chunk if needed (only on placement/right-click).
     */
    @Nullable
    private static ActiveLab findActiveLab(Team team, Level level) {
        BlockPos pos = ProgressionTiers.getLaboratoryPos(team);
        Level labLevel = pos == null ? null : ProgressionTiers.getLaboratoryLevel(team, level);
        if (labLevel == null) {
            return null;
        }
        BlockState state = labLevel.getBlockState(pos);
        if (state.getBlock() instanceof LaboratoryBlock lab && state.getValue(ACTIVE)) {
            return new ActiveLab(labLevel, pos, state, lab.getTier());
        }
        return null;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaboratoryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(ACTIVE)) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.LABORATORY.get(), LaboratoryBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                  InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(ACTIVE)) {
            if (!level.isClientSide) {
                player.displayClientMessage(outOfOrderMessage(level, pos), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            if (level.getBlockEntity(pos) instanceof LaboratoryBlockEntity laboratory
                    && player instanceof ServerPlayer serverPlayer) {
                // NetworkHooks.openScreen (not the vanilla Player#openMenu, which has no
                // overload for passing extra data) writes the BlockPos into the container-open
                // packet, which is what ModMenuTypes' IForgeMenuType factory reads back out via
                // data.readBlockPos() to build the client-side LaboratoryMenu.
                NetworkHooks.openScreen(serverPlayer, laboratory, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * @return the reason this decorative lab is out of order - server-side only
     * ({@code resolveTeam}), only ever called from {@link #use} which already guards on
     * {@code !level.isClientSide}. Distinguishes an unclaimed chunk (no team to credit
     * research to at all) from a claimed chunk whose team already has a different,
     * functional Laboratory elsewhere (names its exact position, so the player doesn't
     * have to go hunting for it).
     */
    private static Component outOfOrderMessage(Level level, BlockPos pos) {
        Team team = ProgressionTiers.resolveTeam(level, pos);
        if (team == null) {
            return Component.translatable("block.s3_progression_mod.laboratory.out_of_order.unclaimed");
        }
        ActiveLab active = findActiveLab(team, level);
        if (active == null) {
            // The recorded active lab is gone (or was never recorded) - placing this one
            // again makes it the active one (see getStateForPlacement).
            return Component.translatable("block.s3_progression_mod.laboratory.out_of_order.none_active");
        }
        BlockPos activePos = active.pos();
        if (active.level() != level) {
            return Component.translatable("block.s3_progression_mod.laboratory.out_of_order.active_elsewhere_dimension",
                    activePos.getX(), activePos.getY(), activePos.getZ(), active.level().dimension().location().toString());
        }
        return Component.translatable("block.s3_progression_mod.laboratory.out_of_order.active_elsewhere",
                activePos.getX(), activePos.getY(), activePos.getZ());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof LaboratoryBlockEntity laboratory) {
                laboratory.dropContents(level, pos);
            }
            // The team's one functional lab was just destroyed - clear the record so the
            // next lab they place (any tier, anywhere in their claim) can become the
            // functional one again. Only if the record points at THIS lab, so removing some
            // other lab never wipes a valid record.
            if (!level.isClientSide() && state.getValue(ACTIVE)) {
                Team team = ProgressionTiers.resolveTeam(level, pos);
                if (team != null && ProgressionTiers.isLaboratoryAt(team, level, pos)) {
                    ProgressionTiers.clearHasLaboratory(team);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
