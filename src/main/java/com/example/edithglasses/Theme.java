package com.example.edithglasses;

import net.minecraft.client.Minecraft;

/** Is the EDITH menu theme (a built-in, switchable resource pack) active right now? */
public final class Theme {
    private static boolean active = false;
    private static int tick = 0;

    private Theme() {
    }

    public static boolean active() {
        return active;
    }

    public static void tick(Minecraft mc) {
        if (tick++ % 20 != 0) return;
        try {
            active = mc.getResourcePackRepository().getSelectedIds().stream().anyMatch(id -> id.contains("edith_menu"));
        } catch (Exception e) {
            active = false;
        }
    }
}
