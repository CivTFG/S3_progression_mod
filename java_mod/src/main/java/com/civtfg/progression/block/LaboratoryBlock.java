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
     * Which of the 6 laboratory block variants this is, and which science Ages it's
     * allowed to research - see {@link LaboratoryBlockEntity#getMatchingScience(Level)},
     * which rejects a craft outright if the single Age present among its slotted items
     * isn't in {@link #allowedAges}. Independent of the ACTIVE/"out of order" mechanic
     * below: that one is about which physical lab (of a given tier) is functional, this
     * one is about which items a functional lab of that tier will accept.
     */
    public enum LabTier {
        PRIMITIVE(EnumSet.of(ModScienceItems.Age.BRONZE, ModScienceItems.Age.IRON)),
        INDUSTRIAL(EnumSet.of(ModScienceItems.Age.STEEL, ModScienceItems.Age.STEAM)),
        ELECTRIC(EnumSet.of(ModScienceItems.Age.LV, ModScienceItems.Age.MV)),
        ADVANCED(EnumSet.of(ModScienceItems.Age.HV, ModScienceItems.Age.MOON)),
        ELITE(EnumSet.of(ModScienceItems.Age.EV, ModScienceItems.Age.MARS)),
        QUANTUM(EnumSet.of(ModScienceItems.Age.IV));

        private final Set<ModScienceItems.Age> allowedAges;

        LabTier(Set<ModScienceItems.Age> allowedAges) {
            this.allowedAges = allowedAges;
        }

        public boolean allows(ModScienceItems.Age age) {
            return allowedAges.contains(age);
        }
    }

    /**
     * Whether this particular lab is the functional one for its {@link #tier} - only the
     * first lab of a given tier placed in a team's claim gets ACTIVE=true (see
     * {@link #getStateForPlacement} and {@link ProgressionTiers#hasLaboratory}); every
     * other same-tier placement (unclaimed chunk, or a team that already has one of this
     * tier) is an inert decorative copy: no GUI, no ticking, just an "out of order"
     * message on right-click. A team can have up to one functional lab per tier (6 total)
     * active simultaneously. All variants share the same model/loot table structure per
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
        boolean active = team != null && !ProgressionTiers.hasLaboratory(team, tier);
        return defaultBlockState().setValue(ACTIVE, active);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && state.getValue(ACTIVE)) {
            Team team = ProgressionTiers.resolveTeam(level, pos);
            if (team != null) {
                ProgressionTiers.setHasLaboratory(team, tier, true);
            }
        }
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
                player.displayClientMessage(Component.translatable("block.s3_progression_mod.laboratory.out_of_order"), false);
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

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof LaboratoryBlockEntity laboratory) {
                laboratory.dropContents(level, pos);
            }
            // The team's one functional lab of this tier was just destroyed - clear the
            // flag so the next lab of this same tier they place (anywhere in their claim)
            // can become the functional one again, rather than every future placement of
            // this tier being permanently "out of order". Other tiers' active flags are
            // untouched.
            if (!level.isClientSide() && state.getValue(ACTIVE)) {
                Team team = ProgressionTiers.resolveTeam(level, pos);
                if (team != null) {
                    ProgressionTiers.setHasLaboratory(team, tier, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
