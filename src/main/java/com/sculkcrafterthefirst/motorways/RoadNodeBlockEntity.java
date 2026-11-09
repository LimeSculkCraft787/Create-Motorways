package com.sculkcrafterthefirst.motorways;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RoadNodeBlockEntity extends BlockEntity {
    private BlockPos target;
    private BlockPos partner;
    private int width = 5;
    private double sdx;
    private double sdz;
    private double edx;
    private double edz;
    private RoadCurve curve;
    private boolean removing;

    public RoadNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROAD_NODE.get(), pos, state);
    }

    public BlockPos getTarget() {
        return target;
    }

    public int getWidth() {
        return width;
    }

    public RoadCurve getCurve() {
        if (target == null) {
            return null;
        }
        if (curve == null) {
            curve = new RoadCurve(
                    target.getX() - worldPosition.getX(),
                    target.getY() - worldPosition.getY(),
                    target.getZ() - worldPosition.getZ(),
                    sdx, sdz, edx, edz);
        }
        return curve;
    }

    public void setConnection(BlockPos target, int width, double sdx, double sdz, double edx, double edz) {
        this.target = target;
        this.width = width;
        this.sdx = sdx;
        this.sdz = sdz;
        this.edx = edx;
        this.edz = edz;
        this.curve = null;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setPartner(BlockPos partner) {
        this.partner = partner;
        setChanged();
    }

    // Entfernt Kollisionsblöcke und den Knoten am anderen Ende
    public void removeRoad() {
        if (removing || level == null || level.isClientSide()) {
            return;
        }
        removing = true;
        RoadNodeBlockEntity startBe;
        BlockPos otherPos;
        if (target != null) {
            startBe = this;
            otherPos = target;
        } else if (partner != null && level.getBlockEntity(partner) instanceof RoadNodeBlockEntity s
                && s.target != null && !s.removing) {
            startBe = s;
            s.removing = true;
            otherPos = partner;
        } else {
            return;
        }
        RoadCollision.remove(level, startBe.getBlockPos(), startBe.getCurve(), startBe.width);
        if (level.getBlockState(otherPos).is(ModBlocks.ROAD_NODE.get())) {
            level.removeBlock(otherPos, false);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("width", width);
        tag.putDouble("sdx", sdx);
        tag.putDouble("sdz", sdz);
        tag.putDouble("edx", edx);
        tag.putDouble("edz", edz);
        if (target != null) {
            tag.putLong("target", target.asLong());
        }
        if (partner != null) {
            tag.putLong("partner", partner.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        width = tag.getInt("width");
        sdx = tag.getDouble("sdx");
        sdz = tag.getDouble("sdz");
        edx = tag.getDouble("edx");
        edz = tag.getDouble("edz");
        target = tag.contains("target") ? BlockPos.of(tag.getLong("target")) : null;
        partner = tag.contains("partner") ? BlockPos.of(tag.getLong("partner")) : null;
        curve = null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
