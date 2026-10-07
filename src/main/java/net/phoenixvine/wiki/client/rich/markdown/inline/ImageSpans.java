package net.phoenixvine.wiki.client.rich.markdown.inline;

import net.minecraft.resources.ResourceLocation;
import net.phoenixvine.wiki.client.rich.RichSpan;

import java.util.List;

public final class ImageSpans {
    private ImageSpans() {}

    public static void addImage(List<RichSpan> out, String rlPart) {
        int w = 48, h = 48;
        float rotation = 0f;
        String[] parts = rlPart.split(",");
        rlPart = parts[0].trim();
        int positional = 0;
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                positional++;
                continue;
            }
            String key;
            String value;
            int eq = part.indexOf('=');
            if (eq > 0) {
                key = part.substring(0, eq).trim().toLowerCase();
                value = part.substring(eq + 1).trim();
            } else {
                key = switch (positional++) {
                    case 0 -> "w";
                    case 1 -> "h";
                    case 2 -> "rot";
                    default -> "";
                };
                value = part;
            }
            try {
                switch (key) {
                    case "w", "width" -> w = Integer.parseInt(value);
                    case "h", "height" -> h = Integer.parseInt(value);
                    case "r", "rot", "rotate", "rotation" -> rotation = Float.parseFloat(value);
                    default -> {}
                }
            } catch (NumberFormatException ignored) {}
        }
        try {
            out.add(new RichSpan.Image(ResourceLocation.parse(rlPart), w, h, rotation));
        } catch (Exception ignored) {}
    }
}
