package com.ticket.model;

import java.util.List;

/** Shared sticker definitions used by validation, persistence and both workbenches. */
public final class StickerCatalog {
    public record Sticker(String code, String emoji, String label) {
        @Override
        public String toString() {
            return emoji + "  " + label;
        }
    }

    private static final List<Sticker> STICKERS = List.of(
        new Sticker("SMILE", "😊", "微笑"),
        new Sticker("LAUGH", "😂", "大笑"),
        new Sticker("WINK", "😉", "眨眼"),
        new Sticker("THINKING", "🤔", "思考"),
        new Sticker("SAD", "😢", "难过"),
        new Sticker("ANGRY", "😠", "生气"),
        new Sticker("HEART", "❤️", "爱心"),
        new Sticker("THUMBS_UP", "👍", "点赞"),
        new Sticker("ACKNOWLEDGED", "👌", "收到"),
        new Sticker("THANKS", "🙏", "谢谢"),
        new Sticker("CLAP", "👏", "鼓掌"),
        new Sticker("GREAT", "🎉", "太棒了"),
        new Sticker("CHEER", "💪", "加油"),
        new Sticker("SORRY", "🙇", "抱歉"),
        new Sticker("CHECKING", "🔎", "正在处理"),
        new Sticker("SOLVED", "✅", "已解决")
    );

    private StickerCatalog() {
    }

    public static List<Sticker> all() {
        return STICKERS;
    }

    public static Sticker find(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return STICKERS.stream()
            .filter(sticker -> sticker.code().equals(code))
            .findFirst()
            .orElse(null);
    }

    public static String display(String code) {
        Sticker sticker = find(code);
        return sticker == null ? "" : sticker.emoji();
    }
}
