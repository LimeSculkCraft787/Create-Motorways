package com.sculkcrafterthefirst.motorways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

// Vorschau für Mittel- und Randlinien: grünes Band dort, wo die Linie landen würde
@EventBusSubscriber(modid = Motorways.MODID, value = Dist.CLIENT)
public class LinePreview {
    private static final ResourceLocation WHITE =
            ResourceLocation.withDefaultNamespace("block/white_concrete");
    private static final float HEIGHT = 5f / 16f;

    private static boolean hasStart(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().contains("LineNode");
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

        // Linien-Item in Haupt- oder Nebenhand mit gesetztem Anfang
        ItemStack stack = null;
        for (ItemStack s : new ItemStack[] { player.getMainHandItem(), player.getOffhandItem() }) {
            if (s.getItem() instanceof LineItem && hasStart(s)) {
                stack = s;
                break;
            }
        }
        if (stack == null) {
            return;
        }
        LineItem item = (LineItem) stack.getItem();
        boolean edge = item.isEdge();
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        // Straße des gesetzten Anfangs suchen
        BlockPos sp = BlockPos.of(tag.getLong("LineNode"));
        BlockPos st = BlockPos.of(tag.getLong("LineTarget"));
        if (!mc.level.hasChunkAt(sp)) {
            return;
        }
        if (!(mc.level.getBlockEntity(sp) instanceof RoadNodeBlockEntity startNode)) {
            return;
        }
        RoadNodeBlockEntity.Road startRoad = null;
        for (RoadNodeBlockEntity.Road r : startNode.getRoads()) {
            if (r.target.equals(st)) {
                startRoad = r;
            }
        }
        if (startRoad == null) {
            return;
        }
        double s0 = tag.getDouble("LineS");
        double ppb = Config.PIXELS_PER_BLOCK.get();

        // Teilstücke berechnen, genau wie beim Setzen
        List<RoadGraph.Piece> pieces = new ArrayList<>();
        String problem = null;
        RoadLocator.Hit hit = MarkingTools.pick(mc.level, player, true);
        if (hit != null) {
            if (!edge && hit.road().width != RoadPlanner.DOUBLE) {
                problem = "Mittellinien gibt es nur auf Zweispur-Straßen";
            } else {
                double roadLen = hit.road().curve(hit.node().getBlockPos()).length;
                double s = Math.max(0.0, Math.min(roadLen, Math.round(hit.s() * ppb) / ppb));
                double[] lats = LineItem.lats(edge, startRoad.width, LineItem.getSide(stack));
                if (startRoad == hit.road()) {
                    double a = Math.min(s0, s);
                    double b = Math.max(s0, s);
                    if (b - a >= 1.0 / ppb) {
                        boolean back = s < s0;
                        for (double latTravel : lats) {
                            pieces.add(new RoadGraph.Piece(startNode, startRoad, a, b, back,
                                    back ? -latTravel : latTravel));
                        }
                    }
                } else {
                    RoadGraph.Route route = RoadGraph.find(mc.level, startNode, startRoad, s0,
                            hit.node(), hit.road(), s);
                    if (route == null) {
                        problem = "Nicht verbunden";
                    } else {
                        for (double latTravel : lats) {
                            pieces.addAll(RoadGraph.pieces(route, startNode, startRoad, s0,
                                    hit.node(), hit.road(), s, latTravel));
                        }
                    }
                }
            }
        }
        if (problem != null) {
            player.displayClientMessage(Component.literal(problem), true);
        }

        // Zeichnen
        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer c = buffers.getBuffer(RenderType.translucent());
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WHITE);
        float u = (sprite.getU0() + sprite.getU1()) / 2f;
        float v = (sprite.getV0() + sprite.getV1()) / 2f;

        // Gelbe Querleiste am gesetzten Anfang
        poseStack.pushPose();
        poseStack.translate(sp.getX() - cam.x, sp.getY() - cam.y, sp.getZ() - cam.z);
        drawStartBar(c, poseStack.last(), startRoad.curve(sp), s0, startRoad.width / 2f, u, v);
        poseStack.popPose();

        // Grünes Band für jedes Teilstück
        float actualHalf = (float) ((edge ? Config.EDGE_LINE_WIDTH_PIXELS.get()
                : Config.LINE_WIDTH_PIXELS.get()) / ppb / 2.0);
        float half = Math.max(0.08f, actualHalf);
        for (RoadGraph.Piece p : pieces) {
            BlockPos np = p.node().getBlockPos();
            poseStack.pushPose();
            poseStack.translate(np.getX() - cam.x, np.getY() - cam.y, np.getZ() - cam.z);
            drawStrip(c, poseStack.last(), p.road().curve(np), p.s0(), p.s1(), (float) p.lat(), half, u, v);
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.translucent());
    }

    private static void drawStartBar(VertexConsumer c, PoseStack.Pose pose, RoadCurve curve, double s,
                                     float roadHalf, float u, float v) {
        float[] p = new float[5];
        curve.at(Math.max(0.0, Math.min(curve.length, s)), p);
        float tx = p[3];
        float tz = p[4];
        float px = -tz;
        float pz = tx;
        float d = 0.06f;
        float y = p[1] + HEIGHT + 0.035f;
        ghostQuad(c, pose, u, v, 255, 220, 40, 200,
                p[0] + px * roadHalf - tx * d, y, p[2] + pz * roadHalf - tz * d,
                p[0] - px * roadHalf - tx * d, y, p[2] - pz * roadHalf - tz * d,
                p[0] - px * roadHalf + tx * d, y, p[2] - pz * roadHalf + tz * d,
                p[0] + px * roadHalf + tx * d, y, p[2] + pz * roadHalf + tz * d);
    }

    private static void drawStrip(VertexConsumer c, PoseStack.Pose pose, RoadCurve curve, double sA, double sB,
                                  float lat, float halfWidth, float u, float v) {
        double a = Math.max(0.0, sA);
        double b = Math.min(curve.length, sB);
        if (b - a < 1e-6) {
            return;
        }
        int slices = Math.max(1, (int) Math.ceil((b - a) / 0.25));
        float lift = HEIGHT + 0.03f;

        float[] p = new float[5];
        float prevLx = 0;
        float prevLz = 0;
        float prevRx = 0;
        float prevRz = 0;
        float prevY = 0;
        for (int k = 0; k <= slices; k++) {
            double s = a + (b - a) * k / slices;
            curve.at(s, p);
            float px = -p[4];
            float pz = p[3];
            float bx = p[0] + px * lat;
            float bz = p[2] + pz * lat;
            float lx = bx + px * halfWidth;
            float lz = bz + pz * halfWidth;
            float rx = bx - px * halfWidth;
            float rz = bz - pz * halfWidth;
            float y = p[1] + lift;
            if (k > 0) {
                ghostQuad(c, pose, u, v, 80, 255, 80, 150,
                        prevLx, prevY, prevLz,
                        prevRx, prevY, prevRz,
                        rx, y, rz,
                        lx, y, lz);
            }
            prevLx = lx;
            prevLz = lz;
            prevRx = rx;
            prevRz = rz;
            prevY = y;
        }
    }

    private static void ghostQuad(VertexConsumer c, PoseStack.Pose pose, float u, float v,
                                  int r, int g, int b, int a,
                                  float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3) {
        vtx(c, pose, x0, y0, z0, u, v, r, g, b, a);
        vtx(c, pose, x1, y1, z1, u, v, r, g, b, a);
        vtx(c, pose, x2, y2, z2, u, v, r, g, b, a);
        vtx(c, pose, x3, y3, z3, u, v, r, g, b, a);
        vtx(c, pose, x3, y3, z3, u, v, r, g, b, a);
        vtx(c, pose, x2, y2, z2, u, v, r, g, b, a);
        vtx(c, pose, x1, y1, z1, u, v, r, g, b, a);
        vtx(c, pose, x0, y0, z0, u, v, r, g, b, a);
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
