  package com.sculkcrafterthefirst.motorways;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Motorways.MODID);

    public static final DeferredBlock<RoadNodeBlock> ROAD_NODE = BLOCKS.register("road_node",
            () -> new RoadNodeBlock(BlockBehaviour.Properties.of().noOcclusion().strength(1.0F)));
}
