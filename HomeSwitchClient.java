package com.keepflip.homeswitch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public class HomeSwitchClient implements ClientModInitializer {

    // ===== USTAWIENIA =====
    /** Format komendy bez ukosnika. Jesli serwer wymaga "/home #2", zmien na "home #%d". */
    private static final String HOME_COMMAND = "home %d";
    /** Przerwa po teleporcie (20 tickow = 1 sekunda). */
    private static final int COOLDOWN_TICKS = 100;
    /** Nicki graczy, ktorych mod ma ignorowac (np. Twoi znajomi). */
    private static final Set<String> IGNORED_PLAYERS = Set.of();
    // ======================

    private static KeyBinding toggleKey;
    private static KeyBinding swapKey;

    private boolean enabled = false;
    private int currentHome = 1;
    private int cooldown = 0;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.homeswitch.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.homeswitch"));
        swapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.homeswitch.swap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.homeswitch"));

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(MinecraftClient client) {
        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            cooldown = 0;
            msg(client, "Home Switch: " + (enabled ? "WLACZONY (jestes na home " + currentHome + ")" : "WYLACZONY"));
        }
        while (swapKey.wasPressed()) {
            currentHome = currentHome == 1 ? 2 : 1;
            msg(client, "Ustawiono biezacy home: " + currentHome);
        }

        if (!enabled || client.player == null || client.world == null || client.getNetworkHandler() == null) {
            return;
        }

        // Dzieki temu mod dziala dalej po zminimalizowaniu / utracie fokusu okna.
        client.options.pauseOnLostFocus = false;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        ChunkPos myChunk = client.player.getChunkPos();
        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
            if (other == client.player) continue;
            if (IGNORED_PLAYERS.contains(other.getName().getString())) continue;

            if (other.getChunkPos().equals(myChunk)) {
                switchHome(client, other.getName().getString());
                break;
            }
        }
    }

    private void switchHome(MinecraftClient client, String who) {
        int target = currentHome == 1 ? 2 : 1;
        client.getNetworkHandler().sendChatCommand(String.format(HOME_COMMAND, target));
        currentHome = target;
        cooldown = COOLDOWN_TICKS;
        msg(client, who + " na chunku -> home " + target);
    }

    private void msg(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[HomeSwitch] " + text), true);
        }
    }
}
