package com.sculkcrafterthefirst.motorways;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredItem;

public class RoadSegmentBlock extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[17];

    static {
        for (int i = 1; i <= 16; i++) {
            SHAPES[i] = Block.box(0, 0, 0, 16, i, 16);
        }
    }

    public RoadSegmentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 5));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    // Zum Anklicken nur sichtbar, wenn man ein Markierungs-Item in der Haupthand hält
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        for (DeferredItem<Item> item : ModItems.MARKINGS.values()) {
            if (context.isHoldingItem(item.get())) {
                return SHAPES[state.getValue(LAYERS)];
            }
        }
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
