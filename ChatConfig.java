package pl.chatlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ChatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chatlog.json");
    private static ChatConfig instance = new ChatConfig();

    public List<String> watch = new ArrayList<>();
    public boolean beep = true;
    public boolean popup = true;

    public static ChatConfig get() { return instance; }

    public boolean isWatched(String name) {
        if (name == null) return false;
        for (String w : watch) if (w.equalsIgnoreCase(name)) return true;
        return false;
    }

    public void toggle(String name) {
        Iterator<String> it = watch.iterator();
        while (it.hasNext()) {
            if (it.next().equalsIgnoreCase(name)) { it.remove(); save(); return; }
        }
        watch.add(name);
        save();
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                ChatConfig c = GSON.fromJson(Files.readString(FILE), ChatConfig.class);
                if (c != null) {
                    if (c.watch == null) c.watch = new ArrayList<>();
                    instance = c;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
