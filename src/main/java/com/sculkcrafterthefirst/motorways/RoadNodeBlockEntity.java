package com.sculkcrafterthefirst.motorways;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class RoadNodeBlockEntity extends BlockEntity {
    private BlockPos target;
    private int width = 5;

    public RoadNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROAD_NODE.get(), pos, state);
    }

    public BlockPos getTarget() {
        return target;
    }

    public int getWidth() {
        return width;
    }

    public void setConnection(BlockPos target, int width) {
        this.target = target;
        this.width = width;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("width", width);
        if (target != null) {
            tag.putLong("target", target.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        width = tag.getInt("width");
        target = tag.contains("target") ? BlockPos.of(tag.getLong("target")) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (target == null) {
            return super.getRenderBoundingBox();
        }
        return new AABB(worldPosition, target).inflate(7);
    }
}
