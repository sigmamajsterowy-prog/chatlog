package pl.chatlog;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class Hud {
    private Hud() {}

    private static final int LIFE = 5000;
    private static final int W = 190, H = 30;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || ChatLog.TOASTS.isEmpty()) return;

        long now = System.currentTimeMillis();
        ChatLog.TOASTS.removeIf(t -> now - t.born > LIFE);

        int sw = ctx.getScaledWindowWidth();
        int i = 0;
        for (ChatLog.Toast t : ChatLog.TOASTS) {
            long age = now - t.born;
            float in = Math.min(1f, age / 280f);
            float ease = 1f - (1f - in) * (1f - in) * (1f - in);
            float fade = age > LIFE - 400 ? Math.max(0f, (LIFE - age) / 400f) : 1f;
            if (fade * 255 < 10) { i++; continue; }

            int x = sw - W - 8 + Math.round((1f - ease) * (W + 12));
            int y = 8 + i * (H + 4);

            Gfx.rrect(ctx, x, y, W, H, 6, Gfx.mul(0xE60A1030, fade));
            Gfx.rline(ctx, x, y, W, H, 6, Gfx.mul(0x887C9CFF, fade));
            ctx.fill(x + 1, y + 5, x + 3, y + H - 5, Gfx.mul(0xFFB06CFF, fade));

            ctx.drawTextWithShadow(mc.textRenderer, t.sender,
                    x + 9, y + 5, Gfx.mul(0xFFB06CFF, fade));
            String msg = mc.textRenderer.trimToWidth(t.text, W - 18);
            ctx.drawTextWithShadow(mc.textRenderer, msg, x + 9, y + 16, Gfx.mul(0xFFE8EDFA, fade));
            i++;
        }
    }
}
