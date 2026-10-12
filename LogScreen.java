package pl.chatlog;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LogScreen extends Screen {
    private static final int NAVY = 0xFF3B5FE0;
    private static final int NAVY_L = 0xFF7C9CFF;
    private static final int PURPLE = 0xFFB06CFF;
    private static final int PANEL = 0xE0070B1F;
    private static final int TEXT = 0xFFE8EDFA;
    private static final int MUTED = 0xFF8791AB;
    private static final int PW = 430;
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");

    private static class SenderRow {
        String name;
        int count;
        boolean watched;
    }

    private static class Row {
        ChatLog.Msg m;
        List<OrderedText> lines;
        int y, h;
    }

    private static class Ender {
        float x, phase, speed;
        int dir;
        long nextTp;
    }

    private static class Part {
        float x, y, vx, vy, life;
    }

    private final Random rnd = new Random();
    private final List<SenderRow> senders = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<Ender> enders = new ArrayList<>();
    private final List<Part> parts = new ArrayList<>();

    private TextFieldWidget nickField;
    private String selected = null;
    private boolean dirty = true;
    private int lastSize = -1;

    private int baseTop, left, top, ph;
    private int bodyY, bodyH, listX, listW, msgX, msgW, fieldX, fieldY, fieldW, addX, beepX, popX;
    private float openT = 0f, alpha = 0f;
    private long lastNs = 0;

    private float listScroll;
    private int listMax;
    private float scroll, target;
    private int msgMax, contentH;
    private boolean stick = true, dragBar = false;

    public LogScreen() {
        super(Text.literal("ChatLog"));
    }

    // ---------- uklad ----------

    private void layout(int oy) {
        ph = Math.max(220, Math.min(height - 16, 330));
        left = width / 2 - PW / 2;
        baseTop = height / 2 - ph / 2;
        top = baseTop + oy;
        bodyY = top + 76;
        bodyH = ph - 76 - 40;
        listX = left + 12;
        listW = 128;
        msgX = left + 150;
        msgW = PW - 12 - 150;
        fieldX = left + 12;
        fieldY = top + 42;
        fieldW = 168;
        addX = left + 186;
        beepX = left + 270;
        popX = left + 346;
    }

    @Override
    protected void init() {
        String prev = nickField == null ? "" : nickField.getText();
        layout(0);
        nickField = new TextFieldWidget(textRenderer, fieldX, fieldY, fieldW, 10, Text.empty());
        nickField.setDrawsBackground(false);
        nickField.setEditableColor(TEXT);
        nickField.setMaxLength(16);
        nickField.setTextPredicate(s -> s.matches("[A-Za-z0-9_]*"));
        nickField.setText(prev);
        addSelectableChild(nickField);

        if (enders.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                Ender e = new Ender();
                e.x = 30 + rnd.nextInt(PW - 60);
                e.dir = rnd.nextBoolean() ? 1 : -1;
                e.speed = 12 + rnd.nextInt(14);
                e.phase = rnd.nextFloat() * 6f;
                e.nextTp = System.currentTimeMillis() + 1500 + rnd.nextInt(4000);
                enders.add(e);
            }
        }
        dirty = true;
    }

    // ---------- dane ----------

    private boolean sameSender(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private void rebuild() {
        ChatConfig cfg = ChatConfig.get();
        boolean wasBottom = target >= msgMax - 2;
        List<ChatLog.Msg> all = ChatLog.MSGS;

        // gracze
        Map<String, int[]> cnt = new LinkedHashMap<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            cnt.computeIfAbsent(all.get(i).sender, k -> new int[1])[0]++;
        }
        senders.clear();
        List<String> used = new ArrayList<>();
        for (String w : cfg.watch) {
            String real = w;
            for (String k : cnt.keySet()) if (k.equalsIgnoreCase(w)) real = k;
            SenderRow r = new SenderRow();
            r.name = real;
            r.count = cnt.containsKey(real) ? cnt.get(real)[0] : 0;
            r.watched = true;
            senders.add(r);
            used.add(real.toLowerCase());
        }
        for (Map.Entry<String, int[]> e : cnt.entrySet()) {
            if (used.contains(e.getKey().toLowerCase())) continue;
            SenderRow r = new SenderRow();
            r.name = e.getKey();
            r.count = e.getValue()[0];
            r.watched = false;
            senders.add(r);
        }
        listMax = Math.max(0, (senders.size() + 1) * 24 - bodyH);
        listScroll = Math.min(listScroll, listMax);

        // wiadomosci
        List<ChatLog.Msg> f = new ArrayList<>();
        for (int i = Math.max(0, all.size() - 1500); i < all.size(); i++) {
            ChatLog.Msg m = all.get(i);
            if (selected == null || sameSender(selected, m.sender)) f.add(m);
        }
        if (f.size() > 400) f = f.subList(f.size() - 400, f.size());

        rows.clear();
        int y = 0;
        int wrapW = msgW - 8 - 16;
        for (ChatLog.Msg m : f) {
            Row r = new Row();
            r.m = m;
            r.lines = textRenderer.wrapLines(Text.literal(m.text), wrapW);
            r.h = 14 + r.lines.size() * 10 + 4;
            r.y = y;
            y += r.h + 3;
            rows.add(r);
        }
        contentH = y + 4;
        msgMax = Math.max(0, contentH - bodyH);
        if (stick || wasBottom) target = msgMax;
        target = Math.max(0, Math.min(msgMax, target));
        scroll = Math.max(0, Math.min(msgMax, scroll));

        lastSize = all.size();
        dirty = false;
    }

    private void addNick() {
        String n = nickField.getText().trim();
        if (n.isEmpty()) return;
        ChatConfig c = ChatConfig.get();
        if (!c.isWatched(n)) {
            c.watch.add(n);
            ChatConfig.save();
        }
        nickField.setText("");
        dirty = true;
    }

    // ---------- rysowanie ----------

    private int c(int col) {
        return Gfx.mul(col, alpha);
    }

    private void pill(DrawContext ctx, int x, int y, int w, int h, String label, boolean on, boolean hover) {
        Gfx.rrect(ctx, x, y, w, h, 5, c(on ? 0x553B5FE0 : 0x18FFFFFF));
        if (on || hover) Gfx.rline(ctx, x, y, w, h, 5, c(on ? 0xAA7C9CFF : 0x33FFFFFF));
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, c(on ? 0xFFFFFFFF : MUTED));
    }

    private void drawEnder(DrawContext ctx, float cx, int by, float phase, float a) {
        int x = Math.round(cx);
        int body = Gfx.mul(0xFF1C1C30, a);
        int eye = Gfx.mul(0xFFE040FB, a);
        float sw = (float) Math.sin(phase);
        for (int k = 0; k < 14; k++) {
            int off = Math.round(sw * 3f * k / 14f);
            ctx.fill(x - 2 + off, by - 14 + k, x - 1 + off, by - 13 + k, body);
            ctx.fill(x + 1 - off, by - 14 + k, x + 2 - off, by - 13 + k, body);
        }
        ctx.fill(x - 2, by - 21, x + 2, by - 14, body);
        for (int k = 0; k < 16; k++) {
            int off = Math.round(-sw * 3f * k / 16f);
            ctx.fill(x - 4 + off, by - 21 + k, x - 3 + off, by - 20 + k, body);
            ctx.fill(x + 3 - off, by - 21 + k, x + 4 - off, by - 20 + k, body);
        }
        ctx.fill(x - 3, by - 26, x + 3, by - 21, body);
        ctx.fill(x - 2, by - 24, x - 1, by - 23, eye);
        ctx.fill(x + 1, by - 24, x + 2, by - 23, eye);
    }

    private void burst(float x, float y) {
        for (int i = 0; i < 10; i++) {
            Part p = new Part();
            p.x = x;
            p.y = y + rnd.nextInt(14) - 7;
            p.vx = (rnd.nextFloat() - 0.5f) * 40f;
            p.vy = (rnd.nextFloat() - 0.7f) * 36f;
            p.life = 1f;
            parts.add(p);
        }
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);

        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (ns - lastNs) / 1e9f);
        lastNs = ns;
        openT = Math.min(1f, openT + dt / 0.25f);
        float e = 1f - (1f - openT) * (1f - openT) * (1f - openT);
        alpha = e;
        layout(Math.round((1f - e) * 16f));
        if (e < 0.05f) return;

        if (dirty || ChatLog.MSGS.size() != lastSize) rebuild();

        ChatConfig cfg = ChatConfig.get();
        long nowMs = System.currentTimeMillis();

        scroll += (target - scroll) * 0.3f;
        if (Math.abs(target - scroll) < 0.4f) scroll = target;

        // panel
        Gfx.rrect(ctx, left - 1, top - 1, PW + 2, ph + 2, 8, c(0x30000000));
        Gfx.rrect(ctx, left, top, PW, ph, 7, c(PANEL));
        ctx.fillGradient(left + 4, top + 1, left + PW - 4, top + 40, c(0x333B5FE0), c(0x003B5FE0));
        Gfx.rline(ctx, left, top, PW, ph, 7, c(0x33FFFFFF));

        // naglowek
        ctx.fill(left + 12, top + 11, left + 15, top + 25, c(NAVY_L));
        ctx.drawTextWithShadow(textRenderer, "Chat", left + 22, top + 14, c(0xFFFFFFFF));
        ctx.drawTextWithShadow(textRenderer, "Log", left + 22 + textRenderer.getWidth("Chat"), top + 14, c(NAVY_L));
        String info = ChatLog.MSGS.size() + " wiadomości";
        ctx.drawTextWithShadow(textRenderer, info, left + PW - 34 - textRenderer.getWidth(info), top + 15, c(MUTED));
        boolean closeHover = Gfx.inRect(mx, my, left + PW - 26, top + 9, 16, 16);
        Gfx.rrect(ctx, left + PW - 26, top + 9, 16, 16, 4, c(closeHover ? 0x40FFFFFF : 0x1AFFFFFF));
        ctx.drawCenteredTextWithShadow(textRenderer, "X", left + PW - 18, top + 13, c(TEXT));
        ctx.fill(left + 10, top + 34, left + PW - 10, top + 35, c(0x22FFFFFF));

        // pole z nickiem + przyciski
        boolean ff = nickField.isFocused();
        Gfx.rrect(ctx, fieldX, fieldY - 5, fieldW, 20, 5, c(ff ? 0x55000000 : 0x2A000000));
        Gfx.rline(ctx, fieldX, fieldY - 5, fieldW, 20, 5, c(ff ? NAVY_L : 0x22FFFFFF));
        nickField.setX(fieldX + 6);
        nickField.setY(fieldY);
        nickField.render(ctx, mx, my, delta);
        if (nickField.getText().isEmpty() && !ff) {
            ctx.drawTextWithShadow(textRenderer, "nick do obserwowania...", fieldX + 6, fieldY, c(0xFF59627A));
        }
        pill(ctx, addX, fieldY - 5, 78, 20, "+ Obserwuj", false, Gfx.inRect(mx, my, addX, fieldY - 5, 78, 20));
        pill(ctx, beepX, fieldY - 5, 70, 20, "Dźwięk", cfg.beep, Gfx.inRect(mx, my, beepX, fieldY - 5, 70, 20));
        pill(ctx, popX, fieldY - 5, 72, 20, "Okienko", cfg.popup, Gfx.inRect(mx, my, popX, fieldY - 5, 72, 20));

        // podpisy
        ctx.drawTextWithShadow(textRenderer, "GRACZE", listX, bodyY - 11, c(NAVY_L));
        String who = selected == null ? "WSZYSCY" : selected.toUpperCase();
        ctx.drawTextWithShadow(textRenderer, "WIADOMOŚCI: " + who, msgX, bodyY - 11, c(NAVY_L));
        String dotHint = "● obserwowany";
        ctx.drawTextWithShadow(textRenderer, dotHint, listX + listW - textRenderer.getWidth(dotHint), bodyY - 11, c(0xFF59627A));

        // ---- lista graczy ----
        ctx.enableScissor(listX, bodyY, listX + listW, bodyY + bodyH);
        int total = senders.size() + 1;
        double pulse = 0.5 + 0.5 * Math.sin(nowMs / 350.0);
        for (int i = 0; i < total; i++) {
            int ry = bodyY + i * 24 - Math.round(listScroll);
            if (ry > bodyY + bodyH || ry + 22 < bodyY) continue;
            boolean hov = Gfx.inRect(mx, my, listX, ry, listW, 22) && Gfx.inRect(mx, my, listX, bodyY, listW, bodyH);
            boolean sel;
            String name;
            int count;
            boolean watched = false;
            if (i == 0) {
                sel = selected == null;
                name = "Wszyscy";
                count = ChatLog.MSGS.size();
            } else {
                SenderRow s = senders.get(i - 1);
                sel = sameSender(selected, s.name);
                name = s.name;
                count = s.count;
                watched = s.watched;
            }
            int bg = sel ? (int) (0x44 + 0x1A * pulse) << 24 | 0x3B5FE0 : (hov ? 0x22FFFFFF : 0x10FFFFFF);
            Gfx.rrect(ctx, listX, ry, listW - 4, 22, 5, c(bg));
            if (sel) {
                Gfx.rline(ctx, listX, ry, listW - 4, 22, 5, c(0x887C9CFF));
                ctx.fill(listX + 1, ry + 4, listX + 3, ry + 18, c(NAVY_L));
            }
            String shown = textRenderer.trimToWidth(name, listW - 4 - 44);
            ctx.drawTextWithShadow(textRenderer, shown, listX + 8, ry + 7, c(watched ? PURPLE : (sel ? 0xFFFFFFFF : TEXT)));
            String cs = String.valueOf(count);
            ctx.drawTextWithShadow(textRenderer, cs, listX + listW - 4 - 22 - textRenderer.getWidth(cs), ry + 7, c(MUTED));
            if (i > 0) {
                int dx = listX + listW - 4 - 16, dy = ry + 7;
                if (watched) {
                    Gfx.rrect(ctx, dx - 2, dy - 2, 12, 12, 6, c(0x33B06CFF));
                    Gfx.rrect(ctx, dx, dy, 8, 8, 4, c(PURPLE));
                } else {
                    Gfx.rline(ctx, dx, dy, 8, 8, 4, c(0x66FFFFFF));
                }
            }
        }
        if (listScroll > 0) ctx.fillGradient(listX, bodyY, listX + listW, bodyY + 8, c(PANEL), c(0x00070B1F));
        if (listScroll < listMax) ctx.fillGradient(listX, bodyY + bodyH - 8, listX + listW, bodyY + bodyH, c(0x00070B1F), c(PANEL));
        ctx.disableScissor();

        // ---- wiadomosci ----
        ctx.enableScissor(msgX, bodyY, msgX + msgW, bodyY + bodyH);
        int sy = Math.round(scroll);
        boolean mouseInMsgs = Gfx.inRect(mx, my, msgX, bodyY, msgW, bodyH);
        for (Row r : rows) {
            int ry = bodyY + 2 + r.y - sy;
            if (ry > bodyY + bodyH || ry + r.h < bodyY) continue;
            boolean w = cfg.isWatched(r.m.sender);
            boolean hov = mouseInMsgs && Gfx.inRect(mx, my, msgX, ry, msgW - 8, r.h);
            Gfx.rrect(ctx, msgX, ry, msgW - 8, r.h, 5, c(w ? 0x2A8B5CF6 : (hov ? 0x22FFFFFF : 0x12FFFFFF)));
            if (w) ctx.fill(msgX + 1, ry + 4, msgX + 3, ry + r.h - 4, c(PURPLE));
            String ts = TIME.format(new Date(r.m.time));
            ctx.drawTextWithShadow(textRenderer, ts, msgX + 8, ry + 3, c(MUTED));
            int nx = msgX + 8 + textRenderer.getWidth(ts) + 6;
            ctx.drawTextWithShadow(textRenderer, r.m.sender, nx, ry + 3, c(w ? PURPLE : NAVY_L));
            for (int k = 0; k < r.lines.size(); k++) {
                ctx.drawTextWithShadow(textRenderer, r.lines.get(k), msgX + 8, ry + 14 + k * 10, c(TEXT));
            }
        }
        if (rows.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, "Brak wiadomości. Poczekaj, aż ktoś coś napisze.", msgX + 4, bodyY + 6, c(MUTED));
        }
        if (sy > 0) ctx.fillGradient(msgX, bodyY, msgX + msgW, bodyY + 8, c(PANEL), c(0x00070B1F));
        if (sy < msgMax) ctx.fillGradient(msgX, bodyY + bodyH - 8, msgX + msgW, bodyY + bodyH, c(0x00070B1F), c(PANEL));
        ctx.disableScissor();

        if (msgMax > 0) {
            int tx = msgX + msgW - 5;
            Gfx.rrect(ctx, tx, bodyY, 3, bodyH, 1, c(0x14FFFFFF));
            int th = thumbH();
            int ty = bodyY + (int) ((scroll / msgMax) * (bodyH - th));
            Gfx.rrect(ctx, tx, ty, 3, th, 1, c(dragBar ? NAVY_L : NAVY));
        }

        // ---- endermany ----
        int stripY = top + ph - 38;
        int stripH = 36;
        ctx.fillGradient(left + 4, stripY, left + PW - 4, top + ph - 1, c(0x003B5FE0), c(0x2A3B5FE0));
        ctx.fill(left + 10, stripY, left + PW - 10, stripY + 1, c(0x22FFFFFF));
        ctx.enableScissor(left + 4, stripY, left + PW - 4, top + ph - 1);
        int feet = top + ph - 6;
        for (Ender en : enders) {
            en.x += en.dir * en.speed * dt;
            en.phase += dt * en.speed * 0.45f;
            if (en.x < 12) { en.x = 12; en.dir = 1; }
            if (en.x > PW - 12) { en.x = PW - 12; en.dir = -1; }
            if (nowMs >= en.nextTp) {
                burst(left + en.x, feet - 14);
                en.x = 14 + rnd.nextInt(PW - 28);
                en.dir = rnd.nextBoolean() ? 1 : -1;
                burst(left + en.x, feet - 14);
                en.nextTp = nowMs + 2500 + rnd.nextInt(5000);
            }
            drawEnder(ctx, left + en.x, feet, en.phase, alpha * 0.9f);
        }
        for (int i = parts.size() - 1; i >= 0; i--) {
            Part p = parts.get(i);
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.life -= dt * 1.4f;
            if (p.life <= 0) { parts.remove(i); continue; }
            ctx.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x) + 2, Math.round(p.y) + 2,
                    Gfx.mul(0xFFB06CFF, p.life * alpha));
        }
        ctx.disableScissor();
    }

    private int thumbH() {
        return Math.max(18, (int) ((long) bodyH * bodyH / Math.max(1, contentH)));
    }

    private void dragTo(double my) {
        int th = thumbH();
        double ratio = (my - bodyY - th / 2.0) / Math.max(1, bodyH - th);
        float v = (float) Math.max(0, Math.min(1, ratio)) * msgMax;
        scroll = v;
        target = v;
        stick = target >= msgMax - 1;
    }

    // ---------- mysz i klawiatura ----------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ChatConfig cfg = ChatConfig.get();
        if (button == 0 && Gfx.inRect(mx, my, left + PW - 26, top + 9, 16, 16)) {
            close();
            return true;
        }
        if (Gfx.inRect(mx, my, fieldX, fieldY - 5, fieldW, 20)) {
            setFocused(nickField);
            nickField.mouseClicked(mx, my, button);
            return true;
        }
        if (Gfx.inRect(mx, my, addX, fieldY - 5, 78, 20)) { addNick(); return true; }
        if (Gfx.inRect(mx, my, beepX, fieldY - 5, 70, 20)) {
            cfg.beep = !cfg.beep;
            ChatConfig.save();
            if (cfg.beep) ChatLog.beep();
            return true;
        }
        if (Gfx.inRect(mx, my, popX, fieldY - 5, 72, 20)) {
            cfg.popup = !cfg.popup;
            ChatConfig.save();
            return true;
        }
        if (button == 0 && Gfx.inRect(mx, my, listX, bodyY, listW, bodyH)) {
            int idx = (int) ((my - bodyY + listScroll) / 24);
            if (idx == 0) {
                selected = null;
                stick = true;
                dirty = true;
            } else if (idx - 1 < senders.size()) {
                SenderRow s = senders.get(idx - 1);
                if (mx >= listX + listW - 4 - 22) {
                    cfg.toggle(s.name);
                } else {
                    selected = sameSender(selected, s.name) ? null : s.name;
                    stick = true;
                }
                dirty = true;
            }
            return true;
        }
        if (button == 0 && msgMax > 0 && Gfx.inRect(mx, my, msgX + msgW - 9, bodyY, 9, bodyH)) {
            dragBar = true;
            dragTo(my);
            return true;
        }
        setFocused(null);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragBar) {
            dragTo(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragBar = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (Gfx.inRect(mx, my, listX, bodyY, listW, bodyH)) {
            listScroll = (float) Math.max(0, Math.min(listMax, listScroll - vertical * 24));
        } else {
            target = (float) Math.max(0, Math.min(msgMax, target - vertical * 30));
            stick = target >= msgMax - 1;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nickField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                addNick();
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (key == GLFW.GLFW_KEY_U) {
            close();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void close() {
        ChatConfig.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
