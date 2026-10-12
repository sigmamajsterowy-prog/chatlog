package pl.chatlog;

import net.minecraft.client.gui.DrawContext;

public final class Gfx {
    private Gfx() {}

    public static int mul(int col, float a) {
        float k = Math.max(0f, Math.min(1f, a));
        int al = (int) (((col >>> 24) & 0xFF) * k);
        return (al << 24) | (col & 0xFFFFFF);
    }

    public static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, col); return; }
        c.fill(x, y + r, x + w, y + h - r, col);
        c.fill(x + r, y, x + w - r, y + r, col);
        c.fill(x + r, y + h - r, x + w - r, y + h, col);
        for (int i = 0; i < r; i++) {
            double cy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - cy * cy));
            c.fill(x + inset, y + i, x + r, y + i + 1, col);
            c.fill(x + w - r, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + r, y + h - i, col);
            c.fill(x + w - r, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    public static void rline(DrawContext c, int x, int y, int w, int h, int r, int col) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        c.fill(x + r, y, x + w - r, y + 1, col);
        c.fill(x + r, y + h - 1, x + w - r, y + h, col);
        c.fill(x, y + r, x + 1, y + h - r, col);
        c.fill(x + w - 1, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double cy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - cy * cy));
            c.fill(x + inset, y + i, x + inset + 1, y + i + 1, col);
            c.fill(x + w - inset - 1, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + inset + 1, y + h - i, col);
            c.fill(x + w - inset - 1, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }
}
