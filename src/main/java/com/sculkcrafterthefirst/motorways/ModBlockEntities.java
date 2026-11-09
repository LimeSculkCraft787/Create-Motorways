package com.sculkcrafterthefirst.motorways;

import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Motorways.MODID);

    public static final Supplier<BlockEntityType<RoadNodeBlockEntity>> ROAD_NODE =
            BLOCK_ENTITIES.register("road_node", () -> BlockEntityType.Builder
                    .of(RoadNodeBlockEntity::new, ModBlocks.ROAD_NODE.get()).build(null));
}
