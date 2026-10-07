package net.phoenixvine.wiki.client.rich;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public interface RichBlock {

    List<RichSpan> spans();

    record Heading(int level, List<RichSpan> spans, boolean collapsible) implements RichBlock {

        public Heading(int level, List<RichSpan> spans) {
            this(level, spans, true);
        }
    }

    record Paragraph(List<RichSpan> spans) implements RichBlock {}

    record ListItem(String marker, int indent, List<RichSpan> spans) implements RichBlock {}

    record Checklist(String checkKey, boolean checkedDefault, int indent, List<RichSpan> spans) implements RichBlock {}

    record Quote(List<RichSpan> spans) implements RichBlock {}

    record CodeBlock(String lang, String code) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Callout(String type, String title, List<RichBlock> children) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Details(String expandKey, String title, List<RichBlock> children, boolean fakeLoading)
            implements RichBlock {

        public Details(String expandKey, String title, List<RichBlock> children) {
            this(expandKey, title, children, false);
        }

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record CollapsibleSection(int level, List<RichSpan> headingSpans, String collapseKey,
                              List<RichBlock> children) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return headingSpans;
        }
    }

    record ScaleDirective(float multiplier) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Table(List<List<RichSpan>> header, List<List<List<RichSpan>>> rows) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Rule() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Blank() implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }

    record Hotspot(int x, int y, String tooltip) {}

    record HotspotImage(ResourceLocation image, int width, int height,
                        List<Hotspot> hotspots) implements RichBlock {

        @Override
        public List<RichSpan> spans() {
            return List.of();
        }
    }
}
