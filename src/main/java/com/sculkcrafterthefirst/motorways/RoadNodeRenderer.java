package com.sculkcrafterthefirst.motorways;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Function;
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
            ResourceLocation.fromNamespaceAndPath(Motorways.MODID, "block/asphalt");

    public RoadNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(RoadNodeBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        double minX = pos.getX();
        double minY = pos.getY();
        double minZ = pos.getZ();
        double maxX = pos.getX() + 1;
        double maxY = pos.getY() + 1;
        double maxZ = pos.getZ() + 1;
        double maxChord = 0;
        for (RoadNodeBlockEntity.Road r : be.getRoads()) {
            BlockPos t = r.target;
            minX = Math.min(minX, t.getX());
            minY = Math.min(minY, t.getY());
            minZ = Math.min(minZ, t.getZ());
            maxX = Math.max(maxX, t.getX() + 1);
            maxY = Math.max(maxY, t.getY() + 1);
            maxZ = Math.max(maxZ, t.getZ() + 1);
            double dx = t.getX() - pos.getX();
            double dz = t.getZ() - pos.getZ();
            maxChord = Math.max(maxChord, Math.sqrt(dx * dx + dz * dz));
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.4 * maxChord + 7);
    }

    // Straße immer zeichnen, auch wenn der Startknoten gerade nicht im Bild ist
    @Override
    public boolean shouldRenderOffScreen(RoadNodeBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return Config.ROAD_VIEW_DISTANCE.get();
    }

    @Override
    public void render(RoadNodeBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int light, int overlay) {
        if (be.getRoads().isEmpty()) {
            return;
        }

        Function<ResourceLocation, TextureAtlasSprite> atlas =
                Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite sprite = atlas.apply(TEXTURE);
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float vSide = v0 + (v1 - v0) * HEIGHT;

        VertexConsumer c = buffer.getBuffer(RenderType.solid());
        PoseStack.Pose pose = poseStack.last();
        BlockPos pos = be.getBlockPos();

        for (RoadNodeBlockEntity.Road road : be.getRoads()) {
            RoadCurve curve = road.curve(pos);
            int width = road.width;
            float half = width / 2.0f;
            int seed = pos.hashCode() * 31 + road.target.hashCode();
            float h = HEIGHT + ((seed >>> 3) & 15) * 0.0004f;

            for (int i = 0; i < curve.count - 1; i++) {
                int j = i + 1;
                float pxi = -curve.tz[i];
                float pzi = curve.tx[i];
                float pxj = -curve.tz[j];
                float pzj = curve.tx[j];

                // Oberseite: pro Block eine Kachel, zufällig gedreht
                for (int k = 0; k < width; k++) {
                    float a0 = -half + k;
                    float a1 = a0 + 1;
                    int hash = (seed * 31 + i) * 31 + k;
                    hash ^= hash >>> 15;
                    hash *= 0x2c1b3c6d;
                    hash ^= hash >>> 12;
                    int rot = hash & 3;
                    quadRot(c, pose, light, rot, 0, 1, 0, u0, u1, v0, v1,
                            curve.x[i] + pxi * a0, curve.y[i] + h, curve.z[i] + pzi * a0,
                            curve.x[i] + pxi * a1, curve.y[i] + h, curve.z[i] + pzi * a1,
                            curve.x[j] + pxj * a1, curve.y[j] + h, curve.z[j] + pzj * a1,
                            curve.x[j] + pxj * a0, curve.y[j] + h, curve.z[j] + pzj * a0);
                }

                // linke Seite
                quad(c, pose, light, pxi, 0, pzi, u0, u1, v0, vSide,
                        curve.x[i] + pxi * half, curve.y[i], curve.z[i] + pzi * half,
                        curve.x[j] + pxj * half, curve.y[j], curve.z[j] + pzj * half,
                        curve.x[j] + pxj * half, curve.y[j] + h, curve.z[j] + pzj * half,
                        curve.x[i] + pxi * half, curve.y[i] + h, curve.z[i] + pzi * half);

                // rechte Seite
                quad(c, pose, light, -pxi, 0, -pzi, u0, u1, v0, vSide,
                        curve.x[i] - pxi * half, curve.y[i], curve.z[i] - pzi * half,
                        curve.x[j] - pxj * half, curve.y[j], curve.z[j] - pzj * half,
                        curve.x[j] - pxj * half, curve.y[j] + h, curve.z[j] - pzj * half,
                        curve.x[i] - pxi * half, curve.y[i] + h, curve.z[i] - pzi * half);
            }

            // Anfangs- und Endkappe
            int last = curve.count - 1;
            float spx = -curve.tz[0];
            float spz = curve.tx[0];
            float epx = -curve.tz[last];
            float epz = curve.tx[last];
            for (int k = 0; k < width; k++) {
                float a0 = -half + k;
                float a1 = a0 + 1;
                quad(c, pose, light, -curve.tx[0], 0, -curve.tz[0], u0, u1, v0, vSide,
                        curve.x[0] + spx * a0, curve.y[0], curve.z[0] + spz * a0,
                        curve.x[0] + spx * a1, curve.y[0], curve.z[0] + spz * a1,
                        curve.x[0] + spx * a1, curve.y[0] + h, curve.z[0] + spz * a1,
                        curve.x[0] + spx * a0, curve.y[0] + h, curve.z[0] + spz * a0);
                quad(c, pose, light, curve.tx[last], 0, curve.tz[last], u0, u1, v0, vSide,
                        curve.x[last] + epx * a0, curve.y[last], curve.z[last] + epz * a0,
                        curve.x[last] + epx * a1, curve.y[last], curve.z[last] + epz * a1,
                        curve.x[last] + epx * a1, curve.y[last] + h, curve.z[last] + epz * a1,
                        curve.x[last] + epx * a0, curve.y[last] + h, curve.z[last] + epz * a0);
            }
        }

        // Markierungen (zweiter Durchgang, mit Transparenz)
        boolean anyMarking = false;
        for (RoadNodeBlockEntity.Road road : be.getRoads()) {
            if (!road.markings.isEmpty()) {
                anyMarking = true;
            }
        }
        if (anyMarking) {
            VertexConsumer mc = buffer.getBuffer(RenderType.cutout());
            for (RoadNodeBlockEntity.Road road : be.getRoads()) {
                RoadCurve curve = road.curve(pos);
                for (RoadNodeBlockEntity.Marking m : road.markings) {
                    drawArrow(mc, pose, light, curve, atlas.apply(m.type().texture()), m);
                }
            }
        }
    }

    // Zeichnet eine Markierung als Streifen, der der Kurve und Steigung folgt
    private static void drawArrow(VertexConsumer c, PoseStack.Pose pose, int light, RoadCurve curve,
                                  TextureAtlasSprite sprite, RoadNodeBlockEntity.Marking m) {
        float w = sprite.contents().width() / (float) MarkingType.PIXELS_PER_BLOCK;
        float len = sprite.contents().height() / (float) MarkingType.PIXELS_PER_BLOCK;
        double sc = m.s();
        if (curve.length > len) {
            sc = Math.max(len / 2.0, Math.min(curve.length - len / 2.0, sc));
        }
        int slices = Math.max(1, (int) Math.ceil(len * 4));
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        float lift = HEIGHT + 0.01f;
        float dirSign = m.reverse() ? -1f : 1f;

        float[] p = new float[5];
        float prevLx = 0;
        float prevLz = 0;
        float prevRx = 0;
        float prevRz = 0;
        float prevY = 0;
        float prevV = 0;

        for (int k = 0; k <= slices; k++) {
            float frac = (float) k / slices;
            double along = frac * len;
            double s = m.reverse() ? sc + len / 2.0 - along : sc - len / 2.0 + along;
            curve.at(s, p);

            float rx = -p[4] * dirSign;
            float rz = p[3] * dirSign;
            float bx = p[0] + (-p[4]) * (float) m.lat();
            float bz = p[2] + p[3] * (float) m.lat();
            float y = p[1] + lift;

            float lx = bx - rx * w / 2f;
            float lz = bz - rz * w / 2f;
            float rrx = bx + rx * w / 2f;
            float rrz = bz + rz * w / 2f;
            float v = v1 - (v1 - v0) * frac;

            if (k > 0) {
                quad(c, pose, light, 0, 1, 0, u0, u1, prevV, v,
                        prevLx, prevY, prevLz,
                        prevRx, prevY, prevRz,
                        rrx, y, rrz,
                        lx, y, lz);
            }
            prevLx = lx;
            prevLz = lz;
            prevRx = rrx;
            prevRz = rrz;
            prevY = y;
            prevV = v;
        }
    }

    // Zeichnet ein Viereck von vorn und von hinten, damit die Reihenfolge der Ecken egal ist.
    private static void quad(VertexConsumer c, PoseStack.Pose pose, int light,
                             float nx, float ny, float nz,
                             float u0, float u1, float v0, float v1,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(c, pose, x0, y0, z0, u0, v0, light, nx, ny, nz);
        vertex(c, pose, x1, y1, z1, u1, v0, light, nx, ny, nz);
        vertex(c, pose, x2, y2, z2, u1, v1, light, nx, ny, nz);
        vertex(c, pose, x3, y3, z3, u0, v1, light, nx, ny, nz);
        vertex(c, pose, x3, y3, z3, u0, v1, light, -nx, -ny, -nz);
        vertex(c, pose, x2, y2, z2, u1, v1, light, -nx, -ny, -nz);
        vertex(c, pose, x1, y1, z1, u1, v0, light, -nx, -ny, -nz);
        vertex(c, pose, x0, y0, z0, u0, v0, light, -nx, -ny, -nz);
    }

    private static void quadRot(VertexConsumer c, PoseStack.Pose pose, int light, int rot,
                                float nx, float ny, float nz,
                                float u0, float u1, float v0, float v1,
                                float x0, float y0, float z0, float x1, float y1, float z1,
                                float x2, float y2, float z2, float x3, float y3, float z3) {
        int i0 = rot & 3;
        int i1 = (rot + 1) & 3;
        int i2 = (rot + 2) & 3;
        int i3 = (rot + 3) & 3;
        vertex(c, pose, x0, y0, z0, cornerU(i0, u0, u1), cornerV(i0, v0, v1), light, nx, ny, nz);
        vertex(c, pose, x1, y1, z1, cornerU(i1, u0, u1), cornerV(i1, v0, v1), light, nx, ny, nz);
        vertex(c, pose, x2, y2, z2, cornerU(i2, u0, u1), cornerV(i2, v0, v1), light, nx, ny, nz);
        vertex(c, pose, x3, y3, z3, cornerU(i3, u0, u1), cornerV(i3, v0, v1), light, nx, ny, nz);
        vertex(c, pose, x3, y3, z3, cornerU(i3, u0, u1), cornerV(i3, v0, v1), light, -nx, -ny, -nz);
        vertex(c, pose, x2, y2, z2, cornerU(i2, u0, u1), cornerV(i2, v0, v1), light, -nx, -ny, -nz);
        vertex(c, pose, x1, y1, z1, cornerU(i1, u0, u1), cornerV(i1, v0, v1), light, -nx, -ny, -nz);
        vertex(c, pose, x0, y0, z0, cornerU(i0, u0, u1), cornerV(i0, v0, v1), light, -nx, -ny, -nz);
    }

    private static float cornerU(int idx, float u0, float u1) {
        return (idx == 1 || idx == 2) ? u1 : u0;
    }

    private static float cornerV(int idx, float v0, float v1) {
        return idx >= 2 ? v1 : v0;
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
