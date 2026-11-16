package com.sculkcrafterthefirst.motorways;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public class LineItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final LineType type;

    public LineItem(Properties properties, LineType type) {
        super(properties);
        this.type = type;
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
        return new InteractionResultHolder<>(handle(level, player, stack), stack);
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
        CompoundTag t = new CompoundTag();
        t.putLong("LineNode", nodePos.asLong());
        t.putLong("LineTarget", target.asLong());
        t.putDouble("LineS", s);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    private void run(Level level, Player player, ItemStack stack) {
        if (player.isShiftKeyDown()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            MarkingTools.msg(player, "Linienanfang gelöscht");
            return;
        }

        RoadLocator.Hit road = MarkingTools.pick(level, player);
        if (road == null) {
            return;
        }
        if (road.road().width != RoadPlanner.DOUBLE) {
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
            MarkingTools.msg(player, "Linienanfang gesetzt");
            return;
        }
        double s0 = tag.getDouble("LineS");

        // Ende auf derselben Straße
        if (startRoad == road.road()) {
            double a = Math.min(s0, s);
            double b = Math.max(s0, s);
            if (b - a < 1.0 / ppb) {
                MarkingTools.msg(player, "Zu kurz");
                return;
            }
            MarkingTools.addLine(road.road().lines, new RoadNodeBlockEntity.Line(type, a, b, 0, s < s0));
            road.node().markingsChanged();
            stack.remove(DataComponents.CUSTOM_DATA);
            MarkingTools.msg(player, "Linie gesetzt, Länge " + String.format("%.2f", b - a) + " Blöcke");
            return;
        }

        // Ende auf einer anderen Straße: Weg über verbundene Stücke suchen
        RoadGraph.Route route = RoadGraph.find(level, startNode, startRoad, s0, road.node(), road.road(), s);
        if (route == null) {
            setStart(stack, nodePos, target, s);
            MarkingTools.msg(player, "Nicht verbunden - neuer Linienanfang gesetzt");
            return;
        }
        double total = RoadGraph.apply(type, route, startNode, startRoad, s0, road.node(), road.road(), s);
        stack.remove(DataComponents.CUSTOM_DATA);
        MarkingTools.msg(player, "Linie gesetzt über " + (route.legs().size() + 2)
                + " Straßenstücke, Länge " + String.format("%.1f", total) + " Blöcke");
    }
}
