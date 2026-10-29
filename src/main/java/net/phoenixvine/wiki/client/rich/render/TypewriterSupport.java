package net.phoenixvine.wiki.client.rich.render;

import net.minecraftforge.fml.loading.FMLPaths;
import net.phoenixvine.wiki.client.rich.RichSpan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TypewriterSupport {

    private TypewriterSupport() {}

    private static final Pattern EFFECT_TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9_]*)[^<>\\n]*>");
    private static final String CLICK_PLACEHOLDER = "§8▸ click to reveal";
    private static final long RESTART_AFTER_UNSEEN_MS = 1500;
    private static final long PRUNE_AFTER_UNSEEN_MS = 60_000;

    private static final class State {

        long startMs = -1;
        long lastSeenMs;
        boolean finished;
    }

    private static final Map<String, State> STATES = new HashMap<>();

    private static final java.util.Set<String> PLAYED = new java.util.HashSet<>();
    private static boolean playedLoaded = false;

    private static java.nio.file.Path playedFile() {
        return FMLPaths.CONFIGDIR.get().resolve("phoenix_wiki")
                .resolve("typewriter_seen.txt");
    }

    private static void loadPlayed() {
        if (playedLoaded) return;
        playedLoaded = true;
        try {
            java.nio.file.Path file = playedFile();
            if (java.nio.file.Files.exists(file)) {
                PLAYED.addAll(java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (java.io.IOException ignored) {}
    }

    private static void markPlayed(String key) {
        loadPlayed();
        if (!PLAYED.add(key)) return;
        try {
            java.nio.file.Path file = playedFile();
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, key + System.lineSeparator(),
                    java.nio.charset.StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException ignored) {}
    }
    private static int framesSincePrune = 0;

    public record Frame(String text, boolean awaitingClick, String key) {}

    public static Frame frame(RichSpan.Text span) {
        RichSpan.Typewriter tw = span.typewriter();
        String key = tw.group().id();
        long now = System.currentTimeMillis();
        prune(now);

        loadPlayed();
        if (!tw.repeat() && PLAYED.contains(key)) return new Frame(span.text(), false, key);

        State state = STATES.computeIfAbsent(key, k -> new State());
        if (now - state.lastSeenMs > RESTART_AFTER_UNSEEN_MS) {
            state.startMs = -1;
            state.finished = false;
        }
        state.lastSeenMs = now;

        if (state.finished) {
            markPlayed(key);
            return new Frame(span.text(), false, key);
        }

        if (state.startMs < 0) {
            if (tw.onClick()) {
                return new Frame(tw.offset() == 0 ? CLICK_PLACEHOLDER : "", true, key);
            }
            state.startMs = now;
        }

        int groupVisible = (int) ((now - state.startMs) / 1000f * Math.max(1f, tw.charsPerSecond()));
        if (groupVisible >= tw.group().total()) {
            state.finished = true;
            markPlayed(key);
            return new Frame(span.text(), false, key);
        }

        int visible = groupVisible - tw.offset();
        if (visible <= 0) return new Frame("", false, key);
        return new Frame(reveal(span.text(), visible), false, key);
    }

    public static void start(String key) {
        State state = STATES.get(key);
        if (state != null && state.startMs < 0) state.startMs = System.currentTimeMillis();
    }

    private static void prune(long now) {
        if (++framesSincePrune < 300) return;
        framesSincePrune = 0;
        STATES.values().removeIf(s -> now - s.lastSeenMs > PRUNE_AFTER_UNSEEN_MS);
    }

    public static int visibleLength(String text) {
        int count = 0;
        int i = 0;
        while (i < text.length()) {
            int skip = zeroWidthLength(text, i);
            if (skip > 0) {
                i += skip;
                continue;
            }
            i += Character.charCount(text.codePointAt(i));
            count++;
        }
        return count;
    }

    static String reveal(String text, int visible) {
        StringBuilder out = new StringBuilder();
        List<String> openTags = new ArrayList<>();
        int shown = 0;
        int i = 0;
        while (i < text.length()) {
            int skip = zeroWidthLength(text, i);
            if (skip > 0) {
                String piece = text.substring(i, i + skip);
                out.append(piece);
                trackTag(piece, openTags);
                i += skip;
                continue;
            }
            if (shown >= visible) break;
            int len = Character.charCount(text.codePointAt(i));
            out.append(text, i, i + len);
            i += len;
            shown++;
        }
        for (int t = openTags.size() - 1; t >= 0; t--) out.append("</").append(openTags.get(t)).append('>');
        return out.toString();
    }

    private static int zeroWidthLength(String text, int i) {
        char c = text.charAt(i);
        if (c == '§' && i + 1 < text.length()) return 2;
        if (c == '<') {
            Matcher m = EFFECT_TAG.matcher(text);
            m.region(i, text.length());
            if (m.lookingAt()) return m.end() - i;
        }
        return 0;
    }

    private static void trackTag(String piece, List<String> openTags) {
        if (piece.charAt(0) != '<') return;
        Matcher m = EFFECT_TAG.matcher(piece);
        if (!m.matches()) return;
        String name = m.group(2);
        if (!m.group(1).isEmpty()) {
            for (int i = openTags.size() - 1; i >= 0; i--) {
                if (openTags.get(i).equals(name)) {
                    openTags.remove(i);
                    break;
                }
            }
        } else {
            openTags.add(name);
        }
    }
}
