package com.sculkcrafterthefirst.motorways;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class LineItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LineType type;
    private final boolean edge;

    // edge = false: Mittellinie (nur Zweispur), edge = true: Randlinien (beide Seiten, links oder rechts)
    public LineItem(Properties properties, LineType type, boolean edge) {
        super(properties);
        this.type = type;
        this.edge = edge;
    }

    public boolean isEdge() {
        return edge;
    }

    // Seitenmodus der Randlinien: 0 = beide Seiten, 1 = nur links, 2 = nur rechts
    public static int getSide(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt("Side");
    }

    private static String sideName(int side) {
        if (side == 1) {
            return "nur links";
        }
        if (side == 2) {
            return "nur rechts";
        }
        return "beide Seiten";
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        if (edge) {
            tooltip.add(Component.literal("Seite: " + sideName(getSide(stack))));
            tooltip.add(Component.literal("In den Himmel schauen + Rechtsklick: Seite wechseln"));
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return handle(context.getLevel(), player, context.getItemInHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        // Randlinien: Zielt man auf keine Straße, wechselt der Rechtsklick die Seite
        if (edge && !roadInReach(level, player)) {
            int side = (getSide(stack) + 1) % 3;
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            tag.putInt("Side", side);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            MarkingTools.msg(player, "Seite: " + sideName(side));
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        return new InteractionResultHolder<>(handle(level, player, stack), stack);
    }

    // Gibt es in Reichweite überhaupt eine Straße unter dem Fadenkreuz?
    private static boolean roadInReach(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(6.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() != HitResult.Type.MISS && RoadLocator.find(level, hit.getLocation()) != null;
    }

    private InteractionResult handle(Level level, Player player, ItemStack stack) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        try {
            run(level, player, stack);
        } catch (Exception e) {
            LOGGER.error("Fehler beim Setzen einer Linie", e);
            MarkingTools.msg(player, "Fehler: " + e);
        }
        return InteractionResult.SUCCESS;
    }

    private static void setStart(ItemStack stack, BlockPos nodePos, BlockPos target, double s) {
        CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putLong("LineNode", nodePos.asLong());
        t.putLong("LineTarget", target.asLong());
        t.putDouble("LineS", s);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    // Löscht nur den gemerkten Anfang, der Seitenmodus bleibt erhalten
    private static void clearStart(ItemStack stack) {
        CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.remove("LineNode");
        t.remove("LineTarget");
        t.remove("LineS");
        if (t.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        }
    }

    // Seitenabstände der Linien aus Sicht der Zeichenrichtung (positiv = rechts, negativ = links)
    public static double[] lats(boolean edge, int roadWidth, int side) {
        if (!edge) {
            return new double[] { 0.0 };
        }
        double ppb = Config.PIXELS_PER_BLOCK.get();
        double w = Config.EDGE_LINE_WIDTH_PIXELS.get() / ppb;
        double inset = Config.EDGE_INSET_PIXELS.get() / ppb;
        double lat = Math.max(0.05, roadWidth / 2.0 - inset - w / 2.0);
        if (side == 1) {
            return new double[] { -lat };
        }
        if (side == 2) {
            return new double[] { lat };
        }
        return new double[] { lat, -lat };
    }

    private void run(Level level, Player player, ItemStack stack) {
        String name = edge ? "Randlinie" : "Linie";
        if (player.isShiftKeyDown()) {
            clearStart(stack);
            MarkingTools.msg(player, name + "nanfang gelöscht");
            return;
        }

        RoadLocator.Hit road = MarkingTools.pick(level, player);
        if (road == null) {
            return;
        }
        if (!edge && road.road().width != RoadPlanner.DOUBLE) {
            MarkingTools.msg(player, "Mittellinien gibt es nur auf Zweispur-Straßen");
            return;
        }

        double ppb = Config.PIXELS_PER_BLOCK.get();
        BlockPos nodePos = road.node().getBlockPos();
        BlockPos target = road.road().target;
        double roadLen = road.road().curve(nodePos).length;
        double s = Math.round(road.s() * ppb) / ppb;
        s = Math.max(0.0, Math.min(roadLen, s));

        // Gespeicherten Anfang suchen
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        RoadNodeBlockEntity startNode = null;
        RoadNodeBlockEntity.Road startRoad = null;
        if (tag.contains("LineNode")) {
            BlockPos sp = BlockPos.of(tag.getLong("LineNode"));
            BlockPos st = BlockPos.of(tag.getLong("LineTarget"));
            if (level.hasChunkAt(sp) && level.getBlockEntity(sp) instanceof RoadNodeBlockEntity sn) {
                for (RoadNodeBlockEntity.Road r : sn.getRoads()) {
                    if (r.target.equals(st)) {
                        startNode = sn;
                        startRoad = r;
                    }
                }
            }
        }

        if (startRoad == null) {
            setStart(stack, nodePos, target, s);
            MarkingTools.msg(player, name + "nanfang gesetzt");
            return;
        }
        double s0 = tag.getDouble("LineS");
        int side = edge ? tag.getInt("Side") : 0;
        double[] lats = lats(edge, startRoad.width, side);
        String sideInfo = edge ? " (" + sideName(side) + ")" : "";

        // Ende auf derselben Straße
        if (startRoad == road.road()) {
            double a = Math.min(s0, s);
            double b = Math.max(s0, s);
            if (b - a < 1.0 / ppb) {
                MarkingTools.msg(player, "Zu kurz");
                return;
            }
            boolean backwards = s < s0;
            for (double latTravel : lats) {
                // Die Straße speichert den Seitenabstand in ihrer eigenen Richtung
                double latRoad = backwards ? -latTravel : latTravel;
                MarkingTools.addLine(road.road().lines,
                        new RoadNodeBlockEntity.Line(type, a, b, 0, backwards, latRoad));
            }
            road.node().markingsChanged();
            clearStart(stack);
            MarkingTools.msg(player, name + " gesetzt" + sideInfo + ", Länge "
                    + String.format("%.2f", b - a) + " Blöcke");
            return;
        }

        // Ende auf einer anderen Straße: Weg über verbundene Stücke suchen
        RoadGraph.Route route = RoadGraph.find(level, startNode, startRoad, s0, road.node(), road.road(), s);
        if (route == null) {
            setStart(stack, nodePos, target, s);
            MarkingTools.msg(player, "Nicht verbunden - neuer " + name + "nanfang gesetzt");
            return;
        }
        double total = 0;
        for (double latTravel : lats) {
            total = RoadGraph.apply(type, route, startNode, startRoad, s0, road.node(), road.road(), s,
                    latTravel);
        }
        clearStart(stack);
        MarkingTools.msg(player, name + " gesetzt" + sideInfo + " über " + (route.legs().size() + 2)
                + " Straßenstücke, Länge " + String.format("%.1f", total) + " Blöcke");
    }
}
