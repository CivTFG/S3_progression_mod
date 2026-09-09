package com.civtfg.progression.block;

import com.civtfg.progression.blockentity.LaboratoryBlockEntity;
import com.civtfg.progression.registry.ModBlockEntities;
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

public class LaboratoryBlock extends BaseEntityBlock {

    /**
     * Whether this particular lab is the functional one - only the first lab placed in a
     * team's claim gets ACTIVE=true (see {@link #getStateForPlacement} and
     * {@link ProgressionTiers#hasLaboratory}); every other placement (unclaimed chunk, or
     * a team that already has one) is an inert decorative copy: no GUI, no ticking, just
     * an "out of order" message on right-click. Both variants share the same model/loot
     * table, so a broken decorative lab still drops - and can be re-placed as - a normal
     * laboratory item.
     */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public LaboratoryBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Team team = ProgressionTiers.resolveTeam(context.getLevel(), context.getClickedPos());
        boolean active = team != null && !ProgressionTiers.hasLaboratory(team);
        return defaultBlockState().setValue(ACTIVE, active);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && state.getValue(ACTIVE)) {
            Team team = ProgressionTiers.resolveTeam(level, pos);
            if (team != null) {
                ProgressionTiers.setHasLaboratory(team, true);
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
            // The team's one functional lab was just destroyed - clear the flag so the
            // next lab they place (anywhere in their claim) can become the functional one
            // again, rather than every future placement being permanently "out of order".
            if (!level.isClientSide() && state.getValue(ACTIVE)) {
                Team team = ProgressionTiers.resolveTeam(level, pos);
                if (team != null) {
                    ProgressionTiers.setHasLaboratory(team, false);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
