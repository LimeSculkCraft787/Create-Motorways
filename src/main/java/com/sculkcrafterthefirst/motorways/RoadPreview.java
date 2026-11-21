package com.sculkcrafterthefirst.motorways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = Motorways.MODID, value = Dist.CLIENT)
public class RoadPreview {
    private static final ResourceLocation WHITE =
            ResourceLocation.withDefaultNamespace("block/white_concrete");

    private static boolean hasStart(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().contains("StartX");
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // Straßen-Item in Haupt- oder Nebenhand (bevorzugt das mit gesetztem Startpunkt)
        ItemStack stack = null;
        for (ItemStack s : new ItemStack[] { player.getMainHandItem(), player.getOffhandItem() }) {
            if (s.getItem() instanceof RoadItem) {
                if (stack == null || (!hasStart(stack) && hasStart(s))) {
                    stack = s;
                }
            }
        }
        if (stack == null) {
            return;
        }
        RoadItem item = (RoadItem) stack.getItem();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean hasStart = tag.contains("StartX");
        BlockPos start = hasStart
                ? new BlockPos(tag.getInt("StartX"), tag.getInt("StartY"), tag.getInt("StartZ"))
                : null;

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        drawNodes(mc, player, poseStack, cam, buffers, item.getWidth(), start);

        if (!hasStart) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos clicked = hit.getBlockPos();
        BlockPos end = mc.level.getBlockState(clicked).is(ModBlocks.ROAD_NODE.get()) ? clicked : clicked.above();
        double[] look = RoadItem.snappedLook(player);
        Vec3 loc = hit.getLocation();

        RoadPlanner.Plan plan = RoadPlanner.plan(mc.level, item.getWidth(), start,
                tag.getDouble("DirX"), tag.getDouble("DirZ"), tag.getDouble("StartOff"),
                end, look[0], look[1], loc.x, loc.z, tag.getBoolean("Curves"));
        if (!plan.ok()) {
            player.displayClientMessage(Component.literal(plan.message()), true);
        }
        RoadCurve curve = plan.curve();
        if (curve == null) {
            return;
        }

        int r = plan.ok() ? 80 : 255;
        int g = plan.ok() ? 255 : 60;
        int b = plan.ok() ? 80 : 60;
        int a = 120;

        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WHITE);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        VertexConsumer c = buffers.getBuffer(RenderType.translucent());

        poseStack.pushPose();
        poseStack.translate(start.getX() - cam.x, start.getY() - cam.y, start.getZ() - cam.z);
        PoseStack.Pose pose = poseStack.last();

        float half = item.getWidth() / 2.0f;
        float top = 5f / 16f + 0.03f;
        for (int i = 0; i < curve.count - 1; i++) {
            int j = i + 1;
            float pxi = -curve.tz[i];
            float pzi = curve.tx[i];
            float pxj = -curve.tz[j];
            float pzj = curve.tx[j];
            ghostQuad(c, pose, r, g, b, a, u0, u1, v0, v1,
                    curve.x[i] + pxi * half, curve.y[i] + top, curve.z[i] + pzi * half,
                    curve.x[i] - pxi * half, curve.y[i] + top, curve.z[i] - pzi * half,
                    curve.x[j] - pxj * half, curve.y[j] + top, curve.z[j] - pzj * half,
                    curve.x[j] + pxj * half, curve.y[j] + top, curve.z[j] + pzj * half);
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.translucent());
    }

    // Umriss um alle Knoten in der Nähe, bei der Einspur-Straße zusätzlich die Spurmitten der Doppelstraßen
    private static void drawNodes(Minecraft mc, LocalPlayer player, PoseStack poseStack, Vec3 cam,
                                  MultiBufferSource.BufferSource buffers, int heldWidth, BlockPos pending) {
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        int pcx = player.blockPosition().getX() >> 4;
        int pcz = player.blockPosition().getZ() >> 4;

        int radius = Config.NODE_HIGHLIGHT_CHUNKS.get();
        for (int cxo = -radius; cxo <= radius; cxo++) {
            for (int czo = -radius; czo <= radius; czo++) {
                LevelChunk chunk = mc.level.getChunk(pcx + cxo, pcz + czo);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof RoadNodeBlockEntity node)) {
                        continue;
                    }
                    BlockPos p = node.getBlockPos();
                    boolean isPending = p.equals(pending);
                    float r = isPending ? 1.0f : 0.2f;
                    float g = isPending ? 0.85f : 0.9f;
                    float b = isPending ? 0.1f : 1.0f;
                    AABB box = new AABB(p).inflate(0.01).move(-cam.x, -cam.y, -cam.z);
                    LevelRenderer.renderLineBox(poseStack, lines, box, r, g, b, 1.0f);

                    if (heldWidth == RoadPlanner.SINGLE) {
                        for (RoadNodeBlockEntity.Arm arm : node.getArms()) {
                            if (arm.offset() != 0 || arm.width() != RoadPlanner.DOUBLE) {
                                continue;
                            }
                            for (int s = -1; s <= 1; s += 2) {
                                double ax = p.getX() + 0.5 + (-arm.dz()) * s * RoadPlanner.LANE_OFFSET;
                                double az = p.getZ() + 0.5 + arm.dx() * s * RoadPlanner.LANE_OFFSET;
                                AABB marker = new AABB(ax - 0.2, p.getY(), az - 0.2,
                                        ax + 0.2, p.getY() + 0.45, az + 0.2)
                                        .move(-cam.x, -cam.y, -cam.z);
                                LevelRenderer.renderLineBox(poseStack, lines, marker, 1.0f, 0.55f, 0.1f, 1.0f);
                            }
                        }
                    }
                }
            }
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void ghostQuad(VertexConsumer c, PoseStack.Pose pose, int r, int g, int b, int a,
                                  float u0, float u1, float v0, float v1,
                                  float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3) {
        vtx(c, pose, x0, y0, z0, u0, v0, r, g, b, a);
        vtx(c, pose, x1, y1, z1, u1, v0, r, g, b, a);
        vtx(c, pose, x2, y2, z2, u1, v1, r, g, b, a);
        vtx(c, pose, x3, y3, z3, u0, v1, r, g, b, a);
        vtx(c, pose, x3, y3, z3, u0, v1, r, g, b, a);
        vtx(c, pose, x2, y2, z2, u1, v1, r, g, b, a);
        vtx(c, pose, x1, y1, z1, u1, v0, r, g, b, a);
        vtx(c, pose, x0, y0, z0, u0, v0, r, g, b, a);
    }

    private static void vtx(VertexConsumer c, PoseStack.Pose pose, float x, float y, float z,
                            float u, float v, int r, int g, int b, int a) {
        c.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }
}
