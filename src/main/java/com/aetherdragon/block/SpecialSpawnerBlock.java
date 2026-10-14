package com.aetherdragon.block;

import com.aetherdragon.registry.ModEntities;
import com.aetherdragon.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.aetherdragon.entity.RebornMobEntity;
import net.minecraftforge.registries.RegistryObject;

public class SpecialSpawnerBlock extends Block {
    private static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final String action;

    public SpecialSpawnerBlock(String action) {
        super(action.equals("lamp")
                ? BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 3600000.0F).sound(SoundType.METAL).lightLevel(state -> 4).noOcclusion()
                : BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 3600000.0F).sound(SoundType.METAL).lightLevel(state -> 4));
        this.action = action;
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    public void checkPlayerProximity(ServerLevel level, BlockPos pos, Player player) {
        if (!action.equals("dragon") || player.isSpectator()) {
            return;
        }
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        if (dx * dx + dy * dy + dz * dz <= 64.0D) {
            activate(level, pos, player);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !action.equals("dragon")) {
            return InteractionResult.PASS;
        }
        activate(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private boolean activate(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return false;
        }
        String entityId = "aetherdragon";
        RegistryObject<EntityType<RebornMobEntity>> type = ModEntities.MOBS.get(entityId);
        if (type == null) {
            return false;
        }
        RebornMobEntity entity = type.get().create(level);
        if (entity == null) {
            return false;
        }
        double y = pos.getY() + (action.equals("dragon") ? 10.0D : 1.0D);
        entity.moveTo(pos.getX() + 0.5D, y, pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        if (level instanceof ServerLevel server) {
            double particleY = pos.getY() + (action.equals("dragon") ? 10.0D : 1.0D);
            server.sendParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5D, particleY, pos.getZ() + 0.5D, 5, 3.0D, 3.0D, 3.0D, 1.0D);
            server.sendParticles(ModParticles.FIRE.get(), pos.getX() + 0.5D, particleY, pos.getZ() + 0.5D, 10, 3.0D, 3.0D, 3.0D, 1.0D);
            server.sendParticles(ModParticles.RUNESEFFECT.get(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 16, 0.5D, 0.5D, 0.5D, 0.05D);
        }
        if (action.equals("dragon")) {
            level.explode(null, pos.getX() + 0.5D, y, pos.getZ() + 0.5D, 0.0F, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        return true;
    }
}






