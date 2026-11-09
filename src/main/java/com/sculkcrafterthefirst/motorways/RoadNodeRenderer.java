package com.sculkcrafterthefirst.motorways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;

public class RoadNodeRenderer implements BlockEntityRenderer<RoadNodeBlockEntity> {
    private static final float HEIGHT = 5f / 16f;
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("block/asphalt");

    public RoadNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    public AABB getRenderBoundingBox(RoadNodeBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        BlockPos target = be.getTarget();
        if (target == null) {
            return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        }
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                target.getX() + 1, target.getY() + 1, target.getZ() + 1).inflate(7);
    }

    @Override
    public void render(RoadNodeBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        BlockPos target = be.getTarget();
        if (target == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();

        float dx = target.getX() - pos.getX();
        float dy = target.getY() - pos.getY();
        float dz = target.getZ() - pos.getZ();
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01f) {
            return;
        }

        float ux = dx / len;
        float uz = dz / len;
        float half = be.getWidth() / 2.0f;
        float px = -uz * half;
        float pz = ux * half;

        float sx = 0.5f;
        float sz = 0.5f;
        float ex = dx + 0.5f;
        float ez = dz + 0.5f;
        float h = HEIGHT;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(TEXTURE);
        VertexConsumer c = buffer.getBuffer(RenderType.solid());
        PoseStack.Pose pose = poseStack.last();

        // Oberseite
        quad(c, pose, sprite, light, 0, 1, 0,
                sx + px, h, sz + pz,
                sx - px, h, sz - pz,
                ex - px, dy + h, ez - pz,
                ex + px, dy + h, ez + pz);
        // linke Seite
        quad(c, pose, sprite, light, -uz, 0, ux,
                sx + px, 0, sz + pz,
                sx + px, h, sz + pz,
                ex + px, dy + h, ez + pz,
                ex + px, dy, ez + pz);
        // rechte Seite
        quad(c, pose, sprite, light, uz, 0, -ux,
                sx - px, 0, sz - pz,
                sx - px, h, sz - pz,
                ex - px, dy + h, ez - pz,
                ex - px, dy, ez - pz);
        // Anfang
        quad(c, pose, sprite, light, -ux, 0, -uz,
                sx + px, 0, sz + pz,
                sx + px, h, sz + pz,
                sx - px, h, sz - pz,
                sx - px, 0, sz - pz);
        // Ende
        quad(c, pose, sprite, light, ux, 0, uz,
                ex + px, dy, ez + pz,
                ex + px, dy + h, ez + pz,
                ex - px, dy + h, ez - pz,
                ex - px, dy, ez - pz);
    }

    // Zeichnet ein Viereck von vorn und von hinten, damit die Reihenfolge der Ecken egal ist.
    private static void quad(VertexConsumer c, PoseStack.Pose pose, TextureAtlasSprite s, int light,
                             float nx, float ny, float nz,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        float u0 = s.getU0();
        float u1 = s.getU1();
        float v0 = s.getV0();
        float v1 = s.getV1();
        vertex(c, pose, x0, y0, z0, u0, v0, light, nx, ny, nz);
        vertex(c, pose, x1, y1, z1, u1, v0, light, nx, ny, nz);
        vertex(c, pose, x2, y2, z2, u1, v1, light, nx, ny, nz);
        vertex(c, pose, x3, y3, z3, u0, v1, light, nx, ny, nz);
        vertex(c, pose, x3, y3, z3, u0, v1, light, -nx, -ny, -nz);
        vertex(c, pose, x2, y2, z2, u1, v1, light, -nx, -ny, -nz);
        vertex(c, pose, x1, y1, z1, u1, v0, light, -nx, -ny, -nz);
        vertex(c, pose, x0, y0, z0, u0, v0, light, -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z,
                               float u, float v, int light, float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
