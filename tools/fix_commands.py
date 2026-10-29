import os, re
p = os.path.expanduser("~/EdithGlassesMod/src/main/java/com/example/edithglasses/EdithGlassesClient.java")
lines = open(p, encoding="utf-8").read().split("\n")

start = next(i for i, l in enumerate(lines) if 'literal("vision")' in l)
end = next(i for i, l in enumerate(lines) if i > start and l.strip().startswith('.then(ClientCommands.literal("xray")') and "executes(c -> { setXray(!" not in l and 'setThermal(false); setXray(true)' not in l)
print("replacing lines", start + 1, "to", end)

new = r'''                .then(ClientCommands.literal("vision")
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
                .then(ClientCommands.literal("track")
                    .then(ClientCommands.literal("off").executes(c -> { com.example.edithglasses.feature.Tracker.set(""); say("Tracking off."); return 1; }))
                    .then(ClientCommands.literal("status").executes(c -> { say(com.example.edithglasses.feature.Tracker.status(Minecraft.getInstance())); return 1; }))
                    .then(ClientCommands.argument("player", StringArgumentType.word()).executes(c -> {
                        com.example.edithglasses.feature.Tracker.set(StringArgumentType.getString(c, "player"));
                        say("Tracking " + StringArgumentType.getString(c, "player") + ". They show on the HUD while inside your render distance.");
                        return 1;
                    })))'''.split("\n")
lines[start:end] = new
open(p, "w", encoding="utf-8").write("\n".join(lines))
print("fixed")
