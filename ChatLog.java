package pl.chatlog;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatLog {
    private ChatLog() {}

    public static class Msg {
        public final long time;
        public final String sender;
        public final String text;

        public Msg(long time, String sender, String text) {
            this.time = time;
            this.sender = sender;
            this.text = text;
        }
    }

    public static class Toast {
        public final String sender;
        public final String text;
        public final long born;

        public Toast(String sender, String text, long born) {
            this.sender = sender;
            this.text = text;
            this.born = born;
        }
    }

    public static final List<Msg> MSGS = new ArrayList<>();
    public static final List<Toast> TOASTS = new ArrayList<>();

    private static final Pattern FALLBACK = Pattern.compile(
            "^(?:\\[[^\\]]{1,24}\\]\\s*)*<?([A-Za-z0-9_]{3,16})>?\\s*(?::|»|›|>>?)\\s*(.+)$");

    // ---------- wejscie ----------

    public static void onPlayerChat(String senderName, Text message) {
        String s = clean(message.getString());
        if (senderName == null) { onSystem(s); return; }
        String pre = "<" + senderName + "> ";
        if (s.startsWith(pre)) s = s.substring(pre.length());
        if (s.isBlank()) return;
        add(senderName, s);
    }

    public static void onSystem(String raw) {
        String s = clean(raw).trim();
        if (s.isEmpty()) return;

        String sender = null;
        String text = s;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) {
            int best = Integer.MAX_VALUE;
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String n = e.getProfile().getName();
                if (n == null || n.isEmpty()) continue;
                int[] f = findName(s, n);
                if (f != null && f[0] < best) {
                    best = f[0];
                    sender = n;
                    String rest = s.substring(f[1]).replaceFirst("^[>:»›\\s]+", "");
                    text = rest.isEmpty() ? s : rest;
                }
            }
        }

        if (sender == null) {
            Matcher m = FALLBACK.matcher(s);
            if (m.matches()) {
                sender = m.group(1);
                text = m.group(2);
            }
        }
        if (sender == null) sender = "Serwer";
        add(sender, text);
    }

    // ---------- logika ----------

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Zwraca {poczatek, koniec_separatora} jesli nick stoi na poczatku wiadomosci i ma za soba separator. */
    private static int[] findName(String s, String n) {
        int from = 0;
        while (true) {
            int i = s.indexOf(n, from);
            if (i < 0 || i > 45) return null;
            int e = i + n.length();
            boolean okB = i == 0 || !isWord(s.charAt(i - 1));
            boolean okA = e >= s.length() || !isWord(s.charAt(e));
            if (okB && okA) {
                int j = e;
                while (j < s.length() && s.charAt(j) == ' ') j++;
                if (j < s.length() && ":»›>".indexOf(s.charAt(j)) >= 0) {
                    return new int[]{i, j + 1};
                }
            }
            from = i + 1;
        }
    }

    private static void add(String sender, String text) {
        long now = System.currentTimeMillis();
        MSGS.add(new Msg(now, sender, text));
        if (MSGS.size() > 3000) MSGS.remove(0);

        ChatConfig c = ChatConfig.get();
        if (c.isWatched(sender)) {
            if (c.popup) {
                TOASTS.add(new Toast(sender, text, now));
                if (TOASTS.size() > 5) TOASTS.remove(0);
            }
            if (c.beep) beep();
        }
    }

    public static void beep() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING, 1.7f, 1.0f));
    }
}
