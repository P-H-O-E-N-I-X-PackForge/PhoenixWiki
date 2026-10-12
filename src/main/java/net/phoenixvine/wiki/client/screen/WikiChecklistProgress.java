package net.phoenixvine.wiki.client.screen;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class WikiChecklistProgress {

    private static final Map<String, Boolean> STATE = new HashMap<>();
    private static final Set<String> loadedNamespaces = new HashSet<>();
    private static Path configRoot;

    private WikiChecklistProgress() {}

    public static void init(Path gameDir) {
        configRoot = gameDir.resolve("config");
    }

    public static boolean isInitialized() {
        return configRoot != null;
    }

    public static Boolean getOverride(String namespace, String pageId, String checkKey) {
        ensureLoaded(namespace);
        return STATE.get(stateKey(namespace, pageId, checkKey));
    }

    public static void setChecked(String namespace, String pageId, String checkKey, boolean checked) {
        ensureLoaded(namespace);
        STATE.put(stateKey(namespace, pageId, checkKey), checked);
        save(namespace);
    }

    private static String stateKey(String namespace, String pageId, String checkKey) {
        return namespace + "#" + pageId + "#" + checkKey;
    }

    private static void ensureLoaded(String namespace) {
        if (configRoot == null || loadedNamespaces.contains(namespace)) return;
        loadedNamespaces.add(namespace);
        Path file = fileFor(namespace);
        if (!Files.exists(file)) return;

        try (DataInputStream input = new DataInputStream(Files.newInputStream(file))) {
            // Updated in 1.21.1: Accepts DataInputStream + NbtAccounter
            CompoundTag tag = NbtIo.read(input, NbtAccounter.unlimitedHeap());
            if (tag == null) return;
            for (String key : tag.getAllKeys()) {
                STATE.put(namespace + "#" + key, tag.getBoolean(key));
            }
        } catch (IOException ignored) {}
    }

    private static void save(String namespace) {
        if (configRoot == null) return;
        String prefix = namespace + "#";
        CompoundTag tag = new CompoundTag();
        STATE.forEach((key, value) -> {
            if (key.startsWith(prefix)) tag.putBoolean(key.substring(prefix.length()), value);
        });
        try {
            Path file = fileFor(namespace);
            Files.createDirectories(file.getParent());
            NbtIo.write(tag, file);
        } catch (IOException ignored) {}
    }

    private static Path fileFor(String namespace) {
        return configRoot.resolve(namespace).resolve("wiki_checklist_progress.dat");
    }
}
