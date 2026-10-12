package pl.chatlog;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ChatLogMod implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        ChatConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.chatlog.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, "category.chatlog"));

        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) ->
                ChatLog.onPlayerChat(sender == null ? null : sender.getName(), message));

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) ChatLog.onSystem(message.getString());
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new LogScreen());
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> Hud.render(ctx));
    }
}
