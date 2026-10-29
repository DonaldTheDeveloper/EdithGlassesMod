package com.example.edithglasses.ai;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Finds ffmpeg / ffplay even when the launcher that started Minecraft has an old PATH. */
public final class Tools {
    private static final java.util.Map<String, String> CACHE = new java.util.HashMap<>();

    private Tools() {
    }

    public static synchronized String find(String name) {
        if (CACHE.containsKey(name)) return CACHE.get(name);
        String found = search(name);
        CACHE.put(name, found);
        return found;
    }

    private static String search(String name) {
        String exe = name + ".exe";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir.trim().replace("\"", ""), exe);
                if (f.isFile()) return f.getAbsolutePath();
            }
        }
        String local = System.getenv("LOCALAPPDATA");
        String home = System.getProperty("user.home");
        String[] roots = {
            local == null ? null : local + "\\Microsoft\\WinGet\\Packages",
            local == null ? null : local + "\\Microsoft\\WinGet\\Links",
            "C:\\ffmpeg\\bin", "C:\\Program Files\\ffmpeg\\bin",
            home + "\\scoop\\shims", "C:\\ProgramData\\chocolatey\\bin"
        };
        for (String r : roots) {
            if (r == null) continue;
            Path root = Paths.get(r);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> s = Files.walk(root, 6)) {
                var hit = s.filter(p -> p.getFileName().toString().equalsIgnoreCase(exe)).findFirst();
                if (hit.isPresent()) return hit.get().toString();
            } catch (IOException ignored) {
            }
        }
        return null;
    }
}
