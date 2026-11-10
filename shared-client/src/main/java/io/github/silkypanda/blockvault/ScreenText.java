package io.github.silkypanda.blockvault;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Pixel-measured text layout helpers for narrow game windows. */
final class ScreenText {
    private static final int ENTER_KEY = 257;
    private static final int KEYPAD_ENTER_KEY = 335;
    private static final int ESCAPE_KEY = 256;

    private ScreenText() { }

    static boolean isEnter(KeyEvent event) {
        return event.key() == ENTER_KEY || event.key() == KEYPAD_ENTER_KEY;
    }

    static boolean isEscape(KeyEvent event) {
        return event.key() == ESCAPE_KEY;
    }

    static int drawCenteredWrapped(GuiGraphicsExtractor graphics, Font font, Component text,
                                   int centerX, int topY, int maxWidth, int color, int lineHeight) {
        List<String> lines = wrap(font, text.getString(), maxWidth);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            graphics.text(font, Component.literal(line), centerX - font.width(line) / 2,
                    topY + i * lineHeight, color);
        }
        return topY + lines.size() * lineHeight;
    }

    static String clip(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + ellipsis) > maxWidth) {
            end = value.offsetByCodePoints(end, -1);
        }
        return end == 0 ? "" : value.substring(0, end) + ellipsis;
    }

    private static List<String> wrap(Font font, String value, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            } else {
                if (!line.isEmpty()) line.append(' ');
                line.append(word);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
