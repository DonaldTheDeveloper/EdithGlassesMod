package com.example.edithglasses.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * House plans in local coordinates: x to the right, z towards the front door side, y up, y = 0 is the floor layer.
 * Every plan keeps the whole building within about 4 blocks of height above the floor so a player standing inside can
 * reach all of it without scaffolding.
 */
public final class Blueprint {
    public enum Role { FLOOR, PILLAR, WALL, GLASS, ROOF, TABLE, FURNACE, CHEST, CARPET, LIGHT, DOOR }

    public record Cell(int x, int y, int z, Role role) {
    }

    public final String id, label;
    public final int w, d;
    public final List<Cell> cells;

    private Blueprint(String id, String label, int w, int d, List<Cell> cells) {
        this.id = id;
        this.label = label;
        this.w = w;
        this.d = d;
        this.cells = cells;
    }

    // ------------------------------------------------------------------ tiny drawing kit

    private static final class Draw {
        final Map<Long, Role> map = new LinkedHashMap<>();

        static long k(int x, int y, int z) {
            return ((long) (x + 64) << 40) | ((long) (y + 64) << 20) | (z + 64);
        }

        void set(int x, int y, int z, Role r) {
            map.put(k(x, y, z), r);
        }

        void clear(int x, int y, int z) {
            map.remove(k(x, y, z));
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, Role r) {
            for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) set(x, y, z, r);
        }

        void ring(int w, int d, int y0, int y1, Role r) {
            for (int y = y0; y <= y1; y++) {
                for (int x = 0; x < w; x++) { set(x, y, 0, r); set(x, y, d - 1, r); }
                for (int z = 0; z < d; z++) { set(0, y, z, r); set(w - 1, y, z, r); }
            }
        }

        List<Cell> build() {
            List<Cell> out = new ArrayList<>();
            for (Map.Entry<Long, Role> e : map.entrySet()) {
                long v = e.getKey();
                out.add(new Cell((int) ((v >> 40) & 0xFFFFF) - 64, (int) ((v >> 20) & 0xFFFFF) - 64, (int) (v & 0xFFFFF) - 64, e.getValue()));
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ the three houses

    /** 5 x 5, one room: a door, two windows, a flat roof, a crafting table, a furnace, a chest and a torch. */
    public static Blueprint starter() {
        Draw g = new Draw();
        int w = 5, d = 5;
        g.fill(0, 0, 0, w - 1, 0, d - 1, Role.FLOOR);
        g.ring(w, d, 1, 3, Role.WALL);
        g.clear(2, 1, d - 1);
        g.clear(2, 2, d - 1);
        g.set(0, 2, 2, Role.GLASS);
        g.set(w - 1, 2, 2, Role.GLASS);
        g.fill(0, 4, 0, w - 1, 4, d - 1, Role.ROOF);
        g.set(1, 1, 1, Role.LIGHT);
        g.set(3, 1, 1, Role.CHEST);
        g.set(1, 1, 3, Role.TABLE);
        g.set(3, 1, 3, Role.FURNACE);
        g.set(2, 1, d - 1, Role.DOOR);
        return new Blueprint("starter", "Starter house", w, d, order(g.build()));
    }

    /** 7 x 9: corner pillars, six windows, a two step roof, a carpeted room, crafting, furnace, two chests and four torches. */
    public static Blueprint normal() {
        Draw g = new Draw();
        int w = 7, d = 9;
        g.fill(0, 0, 0, w - 1, 0, d - 1, Role.FLOOR);
        g.ring(w, d, 1, 4, Role.WALL);
        for (int y = 1; y <= 4; y++) {
            g.set(0, y, 0, Role.PILLAR); g.set(w - 1, y, 0, Role.PILLAR);
            g.set(0, y, d - 1, Role.PILLAR); g.set(w - 1, y, d - 1, Role.PILLAR);
        }
        g.clear(3, 1, d - 1);
        g.clear(3, 2, d - 1);
        for (int y = 2; y <= 3; y++) {
            g.set(0, y, 2, Role.GLASS); g.set(0, y, 6, Role.GLASS);
            g.set(w - 1, y, 2, Role.GLASS); g.set(w - 1, y, 6, Role.GLASS);
            g.set(1, y, d - 1, Role.GLASS); g.set(w - 2, y, d - 1, Role.GLASS);
            g.set(3, y, 0, Role.GLASS);
        }
        g.fill(0, 5, 0, w - 1, 5, d - 1, Role.ROOF);
        g.fill(1, 6, 1, w - 2, 6, d - 2, Role.ROOF);
        g.fill(2, 1, 2, 4, 1, 6, Role.CARPET);
        g.set(1, 1, 1, Role.TABLE);
        g.set(2, 1, 1, Role.FURNACE);
        g.set(5, 1, 1, Role.CHEST);
        g.set(5, 1, 3, Role.CHEST);
        g.set(1, 1, 7, Role.LIGHT); g.set(5, 1, 7, Role.LIGHT); g.set(1, 1, 4, Role.LIGHT); g.set(5, 1, 5, Role.LIGHT);
        g.set(3, 1, d - 1, Role.DOOR);
        return new Blueprint("normal", "Normal house", w, d, order(g.build()));
    }

    /** 11 x 13: pillars all round, big windows, a divider wall with a wide opening, skylights, a carpeted hall, lanterns, furniture. */
    public static Blueprint luxury() {
        Draw g = new Draw();
        int w = 11, d = 13;
        g.fill(0, 0, 0, w - 1, 0, d - 1, Role.FLOOR);
        g.ring(w, d, 1, 4, Role.WALL);
        for (int y = 1; y <= 4; y++) {
            for (int x : new int[]{0, 5, 10}) { g.set(x, y, 0, Role.PILLAR); g.set(x, y, d - 1, Role.PILLAR); }
            for (int z : new int[]{6}) { g.set(0, y, z, Role.PILLAR); g.set(w - 1, y, z, Role.PILLAR); }
        }
        // divider wall with a wide opening
        for (int y = 1; y <= 4; y++) for (int x = 1; x <= w - 2; x++) g.set(x, y, 6, Role.WALL);
        for (int y = 1; y <= 3; y++) for (int x = 4; x <= 6; x++) g.clear(x, y, 6);
        // door and big windows
        g.clear(5, 1, d - 1);
        g.clear(5, 2, d - 1);
        for (int y = 2; y <= 3; y++) {
            for (int x : new int[]{2, 3, 7, 8}) { g.set(x, y, d - 1, Role.GLASS); g.set(x, y, 0, Role.GLASS); }
            for (int z : new int[]{2, 3, 9, 10}) { g.set(0, y, z, Role.GLASS); g.set(w - 1, y, z, Role.GLASS); }
        }
        g.fill(0, 5, 0, w - 1, 5, d - 1, Role.ROOF);
        g.fill(1, 6, 1, w - 2, 6, d - 2, Role.ROOF);
        g.set(5, 5, 3, Role.GLASS);
        g.set(5, 5, 9, Role.GLASS);
        // interior
        g.fill(2, 1, 7, 8, 1, 11, Role.CARPET);
        g.set(1, 1, 1, Role.TABLE);
        g.set(2, 1, 1, Role.FURNACE);
        g.set(3, 1, 1, Role.FURNACE);
        g.set(8, 1, 1, Role.CHEST);
        g.set(9, 1, 1, Role.CHEST);
        g.set(9, 1, 3, Role.CHEST);
        g.set(1, 1, 5, Role.LIGHT); g.set(9, 1, 5, Role.LIGHT); g.set(1, 1, 11, Role.LIGHT);
        g.set(9, 1, 11, Role.LIGHT); g.set(5, 1, 2, Role.LIGHT); g.set(5, 1, 8, Role.LIGHT);
        g.set(5, 1, d - 1, Role.DOOR);
        return new Blueprint("luxury", "Luxury house", w, d, order(g.build()));
    }

    public static Blueprint byName(String name) {
        return switch (name.toLowerCase()) {
            case "starter", "small", "basic" -> starter();
            case "normal", "medium", "regular" -> normal();
            case "luxury", "big", "large", "mansion" -> luxury();
            default -> null;
        };
    }

    // ------------------------------------------------------------------ build order

    private static int phase(Cell c) {
        if (c.role() == Role.GLASS && c.y() >= 5) return 2;      // skylights sit in the roof
        return switch (c.role()) {
            case FLOOR -> 0;
            case PILLAR, WALL, GLASS -> 1;
            case ROOF -> 2;
            case TABLE, FURNACE, CHEST, CARPET, LIGHT -> 3;
            case DOOR -> 4;
        };
    }

    /** Floor first, then walls bottom up, then the roof from the edges inwards (so every block has a neighbour), then furniture, then the door. */
    private static List<Cell> order(List<Cell> in) {
        int maxX = 0, maxZ = 0;
        for (Cell c : in) { maxX = Math.max(maxX, c.x()); maxZ = Math.max(maxZ, c.z()); }
        final int mx = maxX, mz = maxZ;
        List<Cell> out = new ArrayList<>(in);
        out.sort(Comparator.<Cell>comparingInt(c -> phase(c))
            .thenComparingInt(c -> phase(c) == 2 ? c.y() : c.y())
            .thenComparingInt(c -> {
                int edge = Math.min(Math.min(c.x(), mx - c.x()), Math.min(c.z(), mz - c.z()));
                return phase(c) == 0 || phase(c) == 2 ? edge : 0;      // floor and roof: from the edge inwards
            })
            .thenComparingInt(Cell::x).thenComparingInt(Cell::z));
        return out;
    }

    /** The cells of one role, counted (materials needed). */
    public Map<Role, Integer> counts() {
        Map<Role, Integer> m = new LinkedHashMap<>();
        for (Cell c : cells) m.merge(c.role(), 1, Integer::sum);
        return m;
    }

    /** World offset of a local cell for a rotation of 0..3 quarter turns: {dx, dz}. */
    public int[] rotate(int x, int z, int rot) {
        return switch (rot & 3) {
            case 1 -> new int[]{d - 1 - z, x};
            case 2 -> new int[]{w - 1 - x, d - 1 - z};
            case 3 -> new int[]{z, w - 1 - x};
            default -> new int[]{x, z};
        };
    }

    public int width(int rot) {
        return (rot & 1) == 0 ? w : d;
    }

    public int depth(int rot) {
        return (rot & 1) == 0 ? d : w;
    }
}
