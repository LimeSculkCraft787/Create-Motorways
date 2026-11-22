package com.sculkcrafterthefirst.motorways;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RoadNodeBlockEntity extends BlockEntity {

    // Richtung vom Knoten in den Straßenkörper hinein, Breite und seitlicher Versatz am Knoten
    public record Arm(double dx, double dz, int width, double offset) {
    }

    // Eine Markierung auf einer Straße: Bogenlänge, Seitenabstand von der Mitte, Richtung umgekehrt?
    public record Marking(MarkingType type, double s, double lat, boolean reverse) {
    }

    // Eine Linie entlang der Straße von Bogenlänge s0 bis s1, seitlich um lat von der Mitte versetzt
    // (0 = Mittellinie). phase: Position im Strichmuster am Musterursprung
    // (bei reverse = true liegt dieser bei s1, sonst bei s0)
    public record Line(LineType type, double s0, double s1, double phase, boolean reverse, double lat) {
        // Teilstück von a bis b, das Strichmuster bleibt an derselben Stelle
        public Line sub(double a, double b) {
            double ph = reverse ? phase + (s1 - b) : phase + (a - s0);
            return new Line(type, a, b, ph, reverse, lat);
        }
    }

    // Eine Straße, die an diesem Knoten beginnt
    public static class Road {
        public final BlockPos target;
        public final int width;
        public final double sdx;
        public final double sdz;
        public final double edx;
        public final double edz;
        public final double so;
        public final double eo;
        public final List<Marking> markings = new ArrayList<>();
        public final List<Line> lines = new ArrayList<>();
        private RoadCurve curve;

        public Road(BlockPos target, int width, double sdx, double sdz, double edx, double edz,
                    double so, double eo) {
            this.target = target;
            this.width = width;
            this.sdx = sdx;
            this.sdz = sdz;
            this.edx = edx;
            this.edz = edz;
            this.so = so;
            this.eo = eo;
        }

        public RoadCurve curve(BlockPos start) {
            if (curve == null) {
                curve = new RoadCurve(
                        target.getX() - start.getX(),
                        target.getY() - start.getY(),
                        target.getZ() - start.getZ(),
                        sdx, sdz, edx, edz, so, eo);
            }
            return curve;
        }

        public double[] startDir(BlockPos start) {
            return unit(sdx, sdz, target.getX() - start.getX(), target.getZ() - start.getZ());
        }

        public double[] endDir(BlockPos start) {
            return unit(edx, edz, target.getX() - start.getX(), target.getZ() - start.getZ());
        }

        private static double[] unit(double x, double z, double fx, double fz) {
            double l = Math.hypot(x, z);
            if (l < 1e-6) {
                l = Math.hypot(fx, fz);
                if (l < 1e-6) {
                    return new double[] { 1, 0 };
                }
                return new double[] { fx / l, fz / l };
            }
            return new double[] { x / l, z / l };
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("target", target.asLong());
            t.putInt("width", width);
            t.putDouble("sdx", sdx);
            t.putDouble("sdz", sdz);
            t.putDouble("edx", edx);
            t.putDouble("edz", edz);
            t.putDouble("so", so);
            t.putDouble("eo", eo);

            ListTag marks = new ListTag();
            for (Marking m : markings) {
                CompoundTag mt = new CompoundTag();
                mt.putString("type", m.type().id);
                mt.putDouble("s", m.s());
                mt.putDouble("lat", m.lat());
                mt.putBoolean("rev", m.reverse());
                marks.add(mt);
            }
            t.put("marks", marks);

            ListTag lineList = new ListTag();
            for (Line l : lines) {
                CompoundTag lt = new CompoundTag();
                lt.putString("type", l.type().id);
                lt.putDouble("s0", l.s0());
                lt.putDouble("s1", l.s1());
                lt.putDouble("phase", l.phase());
                lt.putBoolean("rev", l.reverse());
                lt.putDouble("lat", l.lat());
                lineList.add(lt);
            }
            t.put("lines", lineList);
            return t;
        }

        static Road load(CompoundTag t) {
            Road road = new Road(BlockPos.of(t.getLong("target")), t.getInt("width"),
                    t.getDouble("sdx"), t.getDouble("sdz"), t.getDouble("edx"), t.getDouble("edz"),
                    t.getDouble("so"), t.getDouble("eo"));

            ListTag marks = t.getList("marks", Tag.TAG_COMPOUND);
            for (int i = 0; i < marks.size(); i++) {
                CompoundTag mt = marks.getCompound(i);
                MarkingType type = MarkingType.byId(mt.getString("type"));
                if (type != null) {
                    road.markings.add(new Marking(type, mt.getDouble("s"), mt.getDouble("lat"),
                            mt.getBoolean("rev")));
                }
            }

            ListTag lineList = t.getList("lines", Tag.TAG_COMPOUND);
            for (int i = 0; i < lineList.size(); i++) {
                CompoundTag lt = lineList.getCompound(i);
                LineType lineType = LineType.byId(lt.getString("type"));
                if (lineType != null) {
                    road.lines.add(new Line(lineType, lt.getDouble("s0"), lt.getDouble("s1"),
                            lt.getDouble("phase"), lt.getBoolean("rev"), lt.getDouble("lat")));
                }
            }
            return road;
        }
    }

    private final List<Road> roads = new ArrayList<>();
    private final List<BlockPos> incoming = new ArrayList<>();
    private boolean removing;

    public RoadNodeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROAD_NODE.get(), pos, state);
    }

    public List<Road> getRoads() {
        return roads;
    }

    public List<BlockPos> getIncoming() {
        return incoming;
    }

    public boolean isEmpty() {
        return roads.isEmpty() && incoming.isEmpty();
    }

    public boolean hasRoadTo(BlockPos pos) {
        for (Road r : roads) {
            if (r.target.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    // Alle Anschlüsse an diesem Knoten
    public List<Arm> getArms() {
        List<Arm> arms = new ArrayList<>();
        for (Road r : roads) {
            double[] d = r.startDir(worldPosition);
            arms.add(new Arm(d[0], d[1], r.width, r.so));
        }
        if (level != null) {
            for (BlockPos s : incoming) {
                if (level.getBlockEntity(s) instanceof RoadNodeBlockEntity sb) {
                    for (Road r : sb.roads) {
                        if (r.target.equals(worldPosition)) {
                            double[] e = r.endDir(s);
                            arms.add(new Arm(-e[0], -e[1], r.width, r.eo));
                        }
                    }
                }
            }
        }
        return arms;
    }

    public void addRoad(BlockPos target, int width, double sdx, double sdz, double edx, double edz,
                        double so, double eo) {
        roads.add(new Road(target, width, sdx, sdz, edx, edz, so, eo));
        sync();
    }

    public void addIncoming(BlockPos start) {
        if (!incoming.contains(start)) {
            incoming.add(start);
        }
        sync();
    }

    public Road removeRoadTo(BlockPos target) {
        Iterator<Road> it = roads.iterator();
        while (it.hasNext()) {
            Road r = it.next();
            if (r.target.equals(target)) {
                it.remove();
                return r;
            }
        }
        return null;
    }

    // Nach dem Ändern von Markierungen aufrufen
    public void markingsChanged() {
        sync();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        RoadAreas.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            RoadAreas.unregister(level, worldPosition);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            RoadAreas.markDirty(level);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Wird aufgerufen, wenn dieser Knoten abgebaut wird
    public void breakNode() {
        if (removing || level == null || level.isClientSide()) {
            return;
        }
        removing = true;
        Set<BlockPos> touched = new HashSet<>();

        for (Road r : new ArrayList<>(roads)) {
            RoadCollision.remove(level, worldPosition, r.curve(worldPosition), r.width);
            if (level.getBlockEntity(r.target) instanceof RoadNodeBlockEntity ob) {
                ob.incoming.remove(worldPosition);
                ob.sync();
                touched.add(r.target);
            }
        }
        roads.clear();

        for (BlockPos startPos : new ArrayList<>(incoming)) {
            if (level.getBlockEntity(startPos) instanceof RoadNodeBlockEntity sb) {
                Road r = sb.removeRoadTo(worldPosition);
                if (r != null) {
                    RoadCollision.remove(level, startPos, r.curve(startPos), r.width);
                    sb.sync();
                    touched.add(startPos);
                }
            }
        }
        incoming.clear();

        for (BlockPos p : touched) {
            if (level.getBlockEntity(p) instanceof RoadNodeBlockEntity nb && nb.isEmpty()) {
                level.removeBlock(p, false);
            }
        }
        for (BlockPos p : touched) {
            if (level.getBlockState(p).is(ModBlocks.ROAD_NODE.get())) {
                RoadCollision.rebuildAt(level, p);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Road r : roads) {
            list.add(r.save());
        }
        tag.put("roads", list);
        long[] inc = new long[incoming.size()];
        for (int i = 0; i < inc.length; i++) {
            inc[i] = incoming.get(i).asLong();
        }
        tag.putLongArray("incoming", inc);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        roads.clear();
        incoming.clear();
        if (tag.contains("roads")) {
            ListTag list = tag.getList("roads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                roads.add(Road.load(list.getCompound(i)));
            }
            for (long l : tag.getLongArray("incoming")) {
                incoming.add(BlockPos.of(l));
            }
        } else if (tag.contains("target")) {
            // altes Format (Start-Knoten)
            roads.add(new Road(BlockPos.of(tag.getLong("target")), tag.getInt("width"),
                    tag.getDouble("sdx"), tag.getDouble("sdz"),
                    tag.getDouble("edx"), tag.getDouble("edz"), 0, 0));
        } else if (tag.contains("partner")) {
            // altes Format (End-Knoten)
            incoming.add(BlockPos.of(tag.getLong("partner")));
        }
        if (level != null) {
            RoadAreas.markDirty(level);
        }
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
