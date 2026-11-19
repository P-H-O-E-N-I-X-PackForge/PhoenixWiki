package net.phoenixvine.wiki.api.gate;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.phoenixvine.wiki.PhoenixWiki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Feature gates written by hand: {@code config/phoenix_gates.json}, read again whenever the file changes.
 * <pre>
 * { "bindings": [
 *   { "feature": "excavate:vein_mining",  "anyOf": ["conflux:node/phoenixconflux:px_core"] },
 *   { "feature": "essentials:teleport", "scope": "minecraft:overworld", "anyOf": ["conflux:discipline"] } ] }
 * </pre>
 * {@code feature} is the id a mod asks about ({@code excavate:vein_mining}, {@code essentials:kits}...), {@code scope} a
 * dimension id or {@code *} (the default), and {@code anyOf} gate expressions: the feature is open when any of them is. A
 * feature with no binding is open to everyone. These come on top of the bindings mods register themselves.
 */
@Mod.EventBusSubscriber(modid = PhoenixWiki.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SuiteGateConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CHECK_EVERY_TICKS = 100;
    private static long loadedModified = -1L;

    private SuiteGateConfig() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("phoenix_gates.json");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (server.getTickCount() % CHECK_EVERY_TICKS != 0 && loadedModified != -1L) return;
        reloadIfChanged();
    }

    private static void reloadIfChanged() {
        Path path = file();
        try {
            if (!Files.exists(path)) {
                JsonObject root = new JsonObject();
                root.addProperty("_about", "Feature gates. 'feature' is what a mod asks about (excavate:vein_mining, " +
                        "essentials:teleport...), 'scope' a dimension id or * , 'anyOf' gate expressions: the feature is " +
                        "open when any of them is. Example: { \"feature\": \"excavate:vein_mining\", \"anyOf\": " +
                        "[\"conflux:node/phoenixconflux:px_core\"] }");
                root.add("bindings", new JsonArray());
                Files.createDirectories(path.getParent());
                Files.writeString(path, GSON.toJson(root));
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified == loadedModified) return;
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            List<SuiteGates.Binding> read = new ArrayList<>();
            if (root.has("bindings")) {
                for (JsonElement e : root.getAsJsonArray("bindings")) {
                    JsonObject o = e.getAsJsonObject();
                    if (!o.has("feature") || !o.has("anyOf")) continue;
                    List<String> anyOf = new ArrayList<>();
                    JsonElement any = o.get("anyOf");
                    if (any.isJsonArray()) any.getAsJsonArray().forEach(x -> anyOf.add(x.getAsString()));
                    else anyOf.add(any.getAsString());
                    if (anyOf.isEmpty()) continue;
                    String scope = o.has("scope") ? o.get("scope").getAsString() : SuiteGates.ANY_SCOPE;
                    read.add(new SuiteGates.Binding(o.get("feature").getAsString(),
                            scope.isBlank() ? SuiteGates.ANY_SCOPE : scope, anyOf));
                }
            }
            SuiteGates.setConfigBindings(read);
            loadedModified = modified;
            PhoenixWiki.LOGGER.info("[SuiteGates] {} binding(s) from phoenix_gates.json", read.size());
        } catch (Exception e) {
            PhoenixWiki.LOGGER.warn("[SuiteGates] could not read phoenix_gates.json: {}", e.toString());
            if (loadedModified == -1L) loadedModified = 0L;
        }
    }
}
