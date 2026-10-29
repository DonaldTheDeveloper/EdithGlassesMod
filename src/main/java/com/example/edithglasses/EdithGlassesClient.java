package com.example.edithglasses;

import com.example.edithglasses.ai.Assistant;
import com.example.edithglasses.ai.GameContext;
import com.example.edithglasses.ai.Keys;
import com.example.edithglasses.ai.Models;
import com.example.edithglasses.ai.Providers;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public class EdithGlassesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // optional built-in resource pack: dark holographic menus (switch it off in Options > Resource Packs)
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("edithglasses").ifPresent(mod ->
            net.fabricmc.fabric.api.resource.v1.ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath("edithglasses", "edith_menu"), mod, Component.literal("EDITH Menu Theme"),
                net.fabricmc.fabric.api.resource.v1.pack.PackActivationType.DEFAULT_ENABLED));
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("edithglasses", "main"));
        KeyMapping ptt = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.ptt", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, category));

        KeyMapping confirmKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.confirm", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, category));
        KeyMapping cancelKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.cancel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));



        
        KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, category));
        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Guard::pre);
        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.Builder::pre);
        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.CameraMode::pre);
        KeyMapping cameraKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.edithglasses.camera", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, category));
        ClientTickEvents.START_CLIENT_TICK.register(com.example.edithglasses.feature.AutoHeal::pre);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            Theme.tick(mc);
            while (menuKey.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new EdithMenuScreen(null));
            com.example.edithglasses.feature.Clutch.tick(mc);
            com.example.edithglasses.feature.HoloMap.tick(mc);
            com.example.edithglasses.feature.Memory.tick(mc);
            com.example.edithglasses.feature.Builder.tick(mc);
            com.example.edithglasses.feature.CameraMode.tick(mc);
            while (cameraKey.consumeClick()) if (mc.gui.screen() == null) say(com.example.edithglasses.feature.CameraMode.toggle(mc));
            com.example.edithglasses.feature.Intro.tick(mc);
            com.example.edithglasses.feature.Guard.tick(mc);
            com.example.edithglasses.feature.AutoHeal.tick(mc);
            com.example.edithglasses.feature.Tracker.tick(mc);
            com.example.edithglasses.feature.Alerts.tick(mc);
            com.example.edithglasses.feature.OreScan.tick(mc);
            com.example.edithglasses.feature.Recon.tick(mc);
            com.example.edithglasses.feature.Photo.tick(mc);
            // instant confirm / cancel of whatever EDITH is waiting on (no need to say it)
            while (confirmKey.consumeClick()) { if (Assistant.hasPending()) Assistant.confirm(mc); else if (com.example.edithglasses.feature.Builder.previewing()) say(com.example.edithglasses.feature.Builder.confirm(mc)); }
            while (cancelKey.consumeClick()) { if (com.example.edithglasses.feature.Builder.busy()) { if (System.getProperty("edith.dbg") == null) com.example.edithglasses.feature.Builder.cancel(mc, "you pressed N"); } else if (Assistant.hasPending()) Assistant.cancel(mc); else Assistant.stopSpeaking(); }
            Glasses.tick(mc);
            Assistant.tick(mc, ptt);
        });
        // chat with EDITH: "edith, what is around me?" is not sent to the server, it goes to the assistant
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            String prefix = EdithConfig.get().chatPrefix;
            if (prefix == null || prefix.isBlank()) return true;
            String m = message.trim();
            if (m.length() <= prefix.length() || !m.regionMatches(true, 0, prefix, 0, prefix.length())) return true;
            Minecraft mc = Minecraft.getInstance();
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("You: " + m.substring(prefix.length()).trim()).withStyle(ChatFormatting.GRAY));
            Assistant.ask(mc, m.substring(prefix.length()).trim());
            return false;
        });
        // hide the command feedback spam (particles, sounds, summons) while a drone strike is running
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> {
            if (com.example.edithglasses.ai.Strike.phase.isEmpty()) return true;
            String m = msg.getString();
            return !(m.startsWith("Displaying particle") || m.startsWith("Played sound") || m.startsWith("Summoned new")
                || m.startsWith("Teleported") || m.startsWith("Killed"));
        });
        ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
            if (overlay) return;
            GameContext.remember(msg.getString());
            com.example.edithglasses.feature.Subtitles.onChat(Minecraft.getInstance(), msg.getString());
            com.example.edithglasses.feature.Duel.onChat(Minecraft.getInstance(), msg.getString());
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            // /wear: put the glasses (spyglass in your hand) on your head
            dispatcher.register(ClientCommands.literal("wear").executes(c -> { wear(); return 1; })
                .then(ClientCommands.literal("off").executes(c -> { unwear(); return 1; })));
            dispatcher.register(ClientCommands.literal("unwear").executes(c -> { unwear(); return 1; }));
            dispatcher.register(ClientCommands.literal("edith")
                .then(ClientCommands.literal("menu").executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.schedule(() -> mc.gui.setScreen(new EdithMenuScreen(null))); return 1; }))
                .then(ClientCommands.literal("hud")
                    .then(ClientCommands.literal("always").executes(c -> { setHud("always"); return 1; }))
                    .then(ClientCommands.literal("zoom").executes(c -> { setHud("zoom"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setHud("off"); return 1; })))
                .then(ClientCommands.literal("hatcommand")
                    .then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                        EdithConfig.get().hatCommand = StringArgumentType.getString(c, "name");
                        EdithConfig.save();
                        say("/wear will now run /" + EdithConfig.get().hatCommand);
                        return 1;
                    })))
                // --- assistant ---
                .then(ClientCommands.literal("ask")
                    .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                        Assistant.ask(Minecraft.getInstance(), StringArgumentType.getString(c, "text"));
                        return 1;
                    })))
                .then(ClientCommands.literal("confirm").executes(c -> { Assistant.confirm(Minecraft.getInstance()); return 1; }))
                .then(ClientCommands.literal("cancel").executes(c -> { Assistant.cancel(Minecraft.getInstance()); return 1; }))
                .then(ClientCommands.literal("voice")
                    .then(ClientCommands.literal("on").executes(c -> { setSpeak(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setSpeak(false); return 1; })))
                .then(ClientCommands.literal("mics").executes(c -> {
                    say("Microphones: " + com.example.edithglasses.ai.Audio.micNames()
                        + " | last level " + String.format("%.4f", com.example.edithglasses.ai.Audio.lastRms)
                        + " | using: " + (EdithConfig.get().micDevice.isBlank() ? "system default" : EdithConfig.get().micDevice));
                    return 1;
                })
                    .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                        EdithConfig.get().micDevice = StringArgumentType.getString(c, "name");
                        EdithConfig.save();
                        say("Microphone set to: " + EdithConfig.get().micDevice);
                        return 1;
                    })))
                .then(ClientCommands.literal("video")
                    .then(ClientCommands.argument("path", StringArgumentType.greedyString()).executes(c -> {
                        EdithConfig.get().droneVideo = StringArgumentType.getString(c, "path").replace("\"", "");
                        EdithConfig.save();
                        say("Drone clip set to: " + EdithConfig.get().droneVideo);
                        return 1;
                    })))
                .then(ClientCommands.literal("night")
                    .executes(c -> { setNight(!EdithConfig.get().nightVision); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setNight(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setNight(false); return 1; })))
                .then(ClientCommands.literal("thermal")
                    .executes(c -> { setThermal(!EdithConfig.get().thermal); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setThermal(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setThermal(false); return 1; })))
                .then(ClientCommands.literal("vision")
                    .then(ClientCommands.literal("both").executes(c -> { setThermal(true); setXray(true); return 1; }))
                    .then(ClientCommands.literal("thermal").executes(c -> { setThermal(true); setXray(false); return 1; }))
                    .then(ClientCommands.literal("xray").executes(c -> { setThermal(false); setXray(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setThermal(false); setXray(false); return 1; })))
                .then(ClientCommands.literal("guard")
                    .executes(c -> { setGuard(!EdithConfig.get().guard); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setGuard(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setGuard(false); return 1; }))
                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Guard.describe()); return 1; }))
                    .then(ClientCommands.literal("fight")
                        .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().guardFight = true; EdithConfig.save(); say("Sentinel will fight hostile mobs that reach you (never players)."); return 1; }))
                        .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().guardFight = false; EdithConfig.save(); say("Sentinel fighting OFF"); return 1; })))
                    .then(ClientCommands.literal("afk").then(ClientCommands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3600)).executes(c -> {
                        EdithConfig.get().guardAfkSeconds = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds");
                        EdithConfig.save();
                        say(com.example.edithglasses.feature.Guard.describe());
                        return 1;
                    })))
                    .then(ClientCommands.literal("friend")
                        .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                            String n = StringArgumentType.getString(c, "name");
                            EdithConfig.get().guardFriends = (EdithConfig.get().guardFriends == null || EdithConfig.get().guardFriends.isBlank() ? "" : EdithConfig.get().guardFriends + ",") + n;
                            EdithConfig.save();
                            say(com.example.edithglasses.feature.Guard.describe());
                            return 1;
                        })))
                        .then(ClientCommands.literal("clear").executes(c -> { EdithConfig.get().guardFriends = ""; EdithConfig.save(); say("Sentinel friends cleared"); return 1; }))))
                .then(ClientCommands.literal("duel")
                    .then(ClientCommands.literal("confirm").executes(c -> { say(com.example.edithglasses.feature.Duel.confirm(Minecraft.getInstance())); return 1; }))
                    .then(ClientCommands.literal("stop").executes(c -> { com.example.edithglasses.feature.Duel.stop(Minecraft.getInstance(), "you stopped it."); return 1; }))
                    .then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.Duel.request(StringArgumentType.getString(c, "player"))); return 1; })))
                .then(ClientCommands.literal("map")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().holoMap = true; EdithConfig.save(); say("Hologram map ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().holoMap = false; EdithConfig.save(); say("Hologram map OFF"); return 1; }))
                    .then(ClientCommands.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(8, 28)).executes(c -> { EdithConfig.get().holoRadius = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "radius"); EdithConfig.save(); say("Hologram map radius: " + EdithConfig.get().holoRadius); return 1; })))
                .then(ClientCommands.literal("face")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().faceId = true; EdithConfig.save(); say("Facial recognition ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().faceId = false; EdithConfig.save(); say("Facial recognition OFF"); return 1; })))
                .then(ClientCommands.literal("friend")
                    .then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.FaceId.add(StringArgumentType.getString(c, "name"))); return 1; })))
                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.FaceId.remove(StringArgumentType.getString(c, "name"))); return 1; })))
                    .then(ClientCommands.literal("list").executes(c -> { say(com.example.edithglasses.feature.FaceId.list()); return 1; }))
                    .then(ClientCommands.literal("clear").executes(c -> { com.example.edithglasses.feature.FaceId.clear(); say("Friends cleared"); return 1; }))
                    .then(ClientCommands.literal("target").executes(c -> {
                        if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player pl) say(com.example.edithglasses.feature.FaceId.add(pl.getName().getString()));
                        else say("Look at a player first.");
                        return 1; })))
                .then(ClientCommands.literal("nickname")
                    .then(ClientCommands.argument("names", StringArgumentType.greedyString()).executes(c -> {
                        String n = StringArgumentType.getString(c, "names").trim();
                        EdithConfig.get().nicknames = n.equalsIgnoreCase("off") ? "" : n;
                        EdithConfig.save();
                        com.example.edithglasses.feature.Intro.resetName();
                        say(n.equalsIgnoreCase("off") ? "EDITH will use your player name." : "EDITH will call you: " + n + " (separate several with commas).");
                        return 1; })))
                .then(ClientCommands.literal("layout")
                    .executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.schedule(() -> mc.gui.setScreen(new HudEditScreen(null))); return 1; })
                    .then(ClientCommands.literal("reset").executes(c -> { HudLayout.resetAll(); say("HUD layout reset."); return 1; })))
                .then(ClientCommands.literal("tag")
                    .then(ClientCommands.literal("list").executes(c -> { say(com.example.edithglasses.feature.FaceId.listTags()); return 1; }))
                    .then(ClientCommands.literal("remove").then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.FaceId.removeTag(StringArgumentType.getString(c, "player"))); return 1; })))
                    .then(ClientCommands.literal("set").then(ClientCommands.argument("player", StringArgumentType.word()).then(ClientCommands.argument("tag", StringArgumentType.greedyString()).executes(c -> {
                        String p = StringArgumentType.getString(c, "player"), t = StringArgumentType.getString(c, "tag");
                        com.example.edithglasses.feature.FaceId.setTag(p, t, "");
                        say(p + " is now tagged " + t.toUpperCase() + "."); return 1; }))))
                    .then(ClientCommands.literal("color").then(ClientCommands.argument("player", StringArgumentType.word()).then(ClientCommands.argument("color", StringArgumentType.word()).executes(c -> {
                        String p = StringArgumentType.getString(c, "player"), col = StringArgumentType.getString(c, "color").toLowerCase();
                        String[] t = com.example.edithglasses.feature.FaceId.tagOf(p);
                        if (t == null) { say(p + " has no tag yet. Use /edith tag set first."); return 1; }
                        com.example.edithglasses.feature.FaceId.setTag(p, t[0], col.equals("auto") ? "" : col);
                        say("Tag colour of " + p + ": " + col + " (red, green, amber, blue, white, purple, orange or auto)."); return 1; }))))
                    .then(ClientCommands.literal("target").then(ClientCommands.argument("tag", StringArgumentType.greedyString()).executes(c -> {
                        if (com.example.edithglasses.Glasses.target() instanceof net.minecraft.world.entity.player.Player pl) {
                            String t = StringArgumentType.getString(c, "tag");
                            com.example.edithglasses.feature.FaceId.setTag(pl.getName().getString(), t, "");
                            say(pl.getName().getString() + " is now tagged " + t.toUpperCase() + ".");
                        } else say("Look at a player first.");
                        return 1; }))))
                .then(ClientCommands.literal("remember").then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Memory.addNote(Minecraft.getInstance(), StringArgumentType.getString(c, "text"))); return 1; })))
                .then(ClientCommands.literal("forget").then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Memory.forget(StringArgumentType.getString(c, "text"))); return 1; })))
                .then(ClientCommands.literal("find").then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Memory.findText(Minecraft.getInstance(), StringArgumentType.getString(c, "item"))); return 1; })))
                .then(ClientCommands.literal("memory").executes(c -> { say(com.example.edithglasses.feature.Memory.listNotes() + "  (" + com.example.edithglasses.feature.Memory.boxCount() + " containers remembered)"); return 1; }))
                .then(ClientCommands.literal("analyse").executes(c -> { Minecraft mc = Minecraft.getInstance(); say(com.example.edithglasses.feature.Analyse.speech(mc, com.example.edithglasses.Glasses.target())); return 1; }))
                .then(ClientCommands.literal("stop").executes(c -> { Assistant.stopSpeaking(); return 1; }))
                .then(ClientCommands.literal("build")
                    .executes(c -> { say("Build a house: /edith build starter, normal or luxury (add a wood or wall: oak, spruce, birch, dark, brick, stone, cobble). Then Y builds it, N stops it."); return 1; })
                    .then(ClientCommands.literal("confirm").executes(c -> { say(com.example.edithglasses.feature.Builder.confirm(Minecraft.getInstance())); return 1; }))
                    .then(ClientCommands.literal("cancel").executes(c -> { com.example.edithglasses.feature.Builder.cancel(Minecraft.getInstance(), "cancelled"); return 1; }))
                    .then(ClientCommands.literal("rotate").executes(c -> { say(com.example.edithglasses.feature.Builder.rotate()); return 1; }))
                    .then(ClientCommands.argument("house", StringArgumentType.word()).executes(c -> { say(com.example.edithglasses.feature.Builder.preview(Minecraft.getInstance(), StringArgumentType.getString(c, "house"), "")); return 1; })
                        .then(ClientCommands.argument("style", StringArgumentType.greedyString()).executes(c -> { say(com.example.edithglasses.feature.Builder.preview(Minecraft.getInstance(), StringArgumentType.getString(c, "house"), StringArgumentType.getString(c, "style"))); return 1; }))))
                .then(ClientCommands.literal("camera")
                    .executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> say(com.example.edithglasses.feature.CameraMode.toggle(mc))); return 1; })
                    .then(ClientCommands.literal("shoot").executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> com.example.edithglasses.feature.CameraMode.shutter(mc)); return 1; })))
                .then(ClientCommands.literal("intro").executes(c -> { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> com.example.edithglasses.feature.Intro.play(mc)); return 1; }))
                .then(ClientCommands.literal("clutch")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().clutch = true; EdithConfig.save(); say("Water bucket clutch ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().clutch = false; EdithConfig.save(); say("Water bucket clutch OFF"); return 1; })))
                .then(ClientCommands.literal("heal")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().autoHeal = true; EdithConfig.save(); say("Auto heal ON at " + EdithConfig.get().autoHealAt / 2 + " hearts"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().autoHeal = false; EdithConfig.save(); say("Auto heal OFF"); return 1; }))
                    .then(ClientCommands.literal("at").then(ClientCommands.argument("hearts", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(1f, 10f)).executes(c -> {
                        EdithConfig.get().autoHealAt = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "hearts") * 2f;
                        EdithConfig.save();
                        say("Auto heal at " + EdithConfig.get().autoHealAt / 2 + " hearts");
                        return 1;
                    }))))
                .then(ClientCommands.literal("track")
                    .then(ClientCommands.literal("off").executes(c -> { com.example.edithglasses.feature.Tracker.set(""); say("Tracking off."); return 1; }))
                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Tracker.status(Minecraft.getInstance())); return 1; }))
                    .then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
                        com.example.edithglasses.feature.Tracker.set(StringArgumentType.getString(c, "player"));
                        say("Tracking " + StringArgumentType.getString(c, "player") + ". They show on the HUD while inside your render distance.");
                        return 1;
                    })))
                .then(ClientCommands.literal("xray")
                    .executes(c -> { setXray(!EdithConfig.get().xray); return 1; })
                    .then(ClientCommands.literal("on").executes(c -> { setXray(true); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { setXray(false); return 1; })))
                .then(ClientCommands.literal("photo").executes(c -> { com.example.edithglasses.feature.Photo.request(); return 1; }))
                .then(ClientCommands.literal("recon").executes(c -> { com.example.edithglasses.feature.Recon.toggle(Minecraft.getInstance()); return 1; }))
                .then(ClientCommands.literal("alerts")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().alerts = true; EdithConfig.save(); say("Threat alerts ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().alerts = false; EdithConfig.save(); say("Threat alerts OFF"); return 1; })))
                .then(ClientCommands.literal("subtitles")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().subtitles = true; EdithConfig.save(); say("Translation subtitles ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().subtitles = false; EdithConfig.save(); say("Translation subtitles OFF"); return 1; }))
                    .then(ClientCommands.literal("speak").executes(c -> {
                        EdithConfig.get().speakTranslations = !EdithConfig.get().speakTranslations; EdithConfig.save();
                        say("Reading translations aloud: " + (EdithConfig.get().speakTranslations ? "ON" : "OFF")); return 1; })))
                .then(ClientCommands.literal("waypoint")
                    .then(ClientCommands.literal("set").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                        say(com.example.edithglasses.feature.Waypoints.set(Minecraft.getInstance(), StringArgumentType.getString(c, "name"))); return 1; })))
                    .then(ClientCommands.literal("delete").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                        say(com.example.edithglasses.feature.Waypoints.delete(StringArgumentType.getString(c, "name"))); return 1; })))
                    .then(ClientCommands.literal("track").then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes(c -> {
                        say(com.example.edithglasses.feature.Waypoints.track(StringArgumentType.getString(c, "name"))); return 1; })))
                    .then(ClientCommands.literal("untrack").executes(c -> { say(com.example.edithglasses.feature.Waypoints.track("")); return 1; }))
                    .then(ClientCommands.literal("list").executes(c -> { say(com.example.edithglasses.feature.Waypoints.list(Minecraft.getInstance())); return 1; })))
                .then(ClientCommands.literal("voicename").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    EdithConfig.applyVoice(StringArgumentType.getString(c, "name"));
                    say("EDITH voice set to " + EdithConfig.get().geminiVoice + " (try Erinome, Kore, Aoede, Leda, Sulafat, Vindemiatrix, Zephyr)");
                    return 1;
                })))
                .then(ClientCommands.literal("voicefilter")
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().voiceFilter = ""; EdithConfig.save(); say("Film voice filter OFF (plain voice)"); return 1; }))
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().voiceFilter = new EdithConfig().voiceFilter; EdithConfig.save(); say("Film voice filter ON"); return 1; })))
                .then(ClientCommands.literal("voicefx")
                    .then(ClientCommands.literal("on").executes(c -> { EdithConfig.get().voiceEffect = true; EdithConfig.save(); say("Voice shimmer ON"); return 1; }))
                    .then(ClientCommands.literal("off").executes(c -> { EdithConfig.get().voiceEffect = false; EdithConfig.save(); say("Voice shimmer OFF"); return 1; })))
                .then(ClientCommands.literal("voicecache")
                    .then(ClientCommands.literal("clear").executes(c -> { com.example.edithglasses.ai.Providers.cacheClear(); say("Voice cache cleared."); return 1; }))
                    .executes(c -> { say("Voice cache: " + com.example.edithglasses.ai.Providers.cacheCount() + " saved lines (EDITH replays these instead of generating them again)."); return 1; }))
                .then(ClientCommands.literal("ai")
                    .then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                        String n = StringArgumentType.getString(c, "name").toLowerCase();
                        if (!n.equals("auto") && com.example.edithglasses.ai.Ais.find(n) == null) { say("Unknown AI. Use auto or one of: groq, gemini, openai, anthropic, openrouter, mistral, deepseek, xai, cerebras, together."); return 1; }
                        EdithConfig.get().preferredAi = n; EdithConfig.save(); say("EDITH asks " + n + " first."); return 1; })))
                .then(ClientCommands.literal("models").executes(c -> { refreshModels(); return 1; }))
                .then(ClientCommands.literal("status").executes(c -> { status(); return 1; })));
        });
    }

    private static void wear() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) return;
        boolean headHas = p.getItemBySlot(EquipmentSlot.HEAD).is(Items.SPYGLASS);
        if (!headHas && !p.getMainHandItem().is(Items.SPYGLASS)) { say("Hold the glasses (spyglass) in your main hand first."); return; }
        Glasses.setLocalWorn(true);
        String cmd = EdithConfig.get().hatCommand;
        if (!headHas && !cmd.isBlank()) {
            p.connection.sendCommand(cmd);
            say("Glasses on. Sent /" + cmd + " so other players see them too.");
        } else {
            say("Glasses on.");
        }
    }

    private static void unwear() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        Glasses.setLocalWorn(false);
        if (p != null && mc.gameMode != null && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.SPYGLASS)) {
            // shift-click the helmet slot: the glasses go back into your inventory
            mc.gameMode.handleContainerInput(p.inventoryMenu.containerId, 5, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, p);
            say("Glasses off and back in your inventory.");
        } else {
            say("Glasses off.");
        }
    }

    private static void setHud(String mode) {
        EdithConfig.get().hudMode = mode;
        EdithConfig.save();
        say("EDITH HUD: " + mode);
    }

    private static void setNight(boolean on) {
        EdithConfig.get().nightVision = on;
        EdithConfig.save();
        say("EDITH night vision: " + (on ? "ON" : "OFF"));
    }

    private static void setGuard(boolean on) {
        EdithConfig.get().guard = on;
        EdithConfig.save();
        say("EDITH Sentinel: " + (on ? "ON - you will run from players with weapons, a mace or wind charges. Automatic movement is cheating on many servers." : "OFF"));
    }

    private static void setXray(boolean on) {
        EdithConfig.get().xray = on;
        EdithConfig.save();
        say("EDITH x-ray (ores): " + (on ? "ON - many servers ban this, use single player" : "OFF"));
    }

    private static void setThermal(boolean on) {
        EdithConfig.get().thermal = on;
        EdithConfig.save();
        say("EDITH thermal vision (living things): " + (on ? "ON - many servers forbid this" : "OFF"));
    }

    private static void setSpeak(boolean on) {
        EdithConfig.get().speak = on;
        EdithConfig.save();
        say("EDITH voice replies: " + (on ? "on" : "off"));
    }

    private static void refreshModels() {
        say("Checking available models...");
        Thread t = new Thread(() -> { Models.refresh(); status(); }, "edith-models");
        t.setDaemon(true);
        t.start();
    }

    private static void status() {
        StringBuilder ks = new StringBuilder();
        for (var a : com.example.edithglasses.ai.Ais.ALL) ks.append(a.label()).append(Keys.has(a.id()) ? " yes, " : " no, ");
        say("Keys: " + ks + "(add them in the G menu > AI, or in " + Keys.path() + ")");
        say("Groq chat: " + first(Models.groqChat) + " | stt: " + first(Models.groqStt) + " | tts: " + first(Models.groqTts));
        say("Gemini chat: " + first(Models.geminiChat) + " | tts: " + first(Models.geminiTts));
        var repo = Minecraft.getInstance().getResourcePackRepository();
        var pack = repo.getPack("file/EdithGlasses-ResourcePack.zip");
        say("Resource pack: " + (pack == null ? "not found in resourcepacks folder"
            : (repo.getSelectedPacks().contains(pack) ? "enabled" : "available, not enabled") + ", " + pack.getCompatibility()));
        if (!Providers.lastChatModel.isEmpty()) say("Last reply came from: " + Providers.lastChatModel);
        if (!Models.error.isEmpty()) say("Model list problem: " + Models.error);
        if (!Providers.lastError.isEmpty()) say("Last error: " + Providers.lastError);
    }

    private static String first(java.util.List<String> l) {
        return l.isEmpty() ? "(none yet - run /edith models)" : l.get(0);
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg).withStyle(ChatFormatting.AQUA)));
    }
}
