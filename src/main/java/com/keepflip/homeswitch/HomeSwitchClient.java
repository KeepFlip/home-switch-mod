package com.keepflip.homeswitch;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.regex.Pattern;

public class HomeSwitchClient implements ClientModInitializer {

    // ===== USTAWIENIA =====
    /** Komenda otwierajaca menu home (bez ukosnika). */
    private static final String HOME_COMMAND = "home";
    /** Przerwa po teleporcie (20 tickow = 1 sekunda). */
    private static final int COOLDOWN_TICKS = 100;
    /** Ile tickow czekac na otwarcie menu i zaladowanie przedmiotow. */
    private static final int GUI_TIMEOUT_TICKS = 60;
    /** Numer slotu w menu dla home 1 i home 2. -1 = szukaj automatycznie po nazwie (#1 / #2). */
    private static final int HOME_1_SLOT = -1;
    private static final int HOME_2_SLOT = -1;
    /** Nicki graczy, ktorych mod ma ignorowac. */
    private static final Set<String> IGNORED_PLAYERS = Set.of();
    /** Gdy nie znajdzie home w menu, wypisz na czacie liste przedmiotow z menu. */
    private static final boolean DEBUG = true;
    // ======================

    private static KeyBinding toggleKey;
    private static KeyBinding swapKey;

    private boolean enabled = false;
    private int currentHome = 1;
    private int cooldown = 0;

    private int pendingHome = 0;   // 0 = nic nie czeka
    private int guiWait = 0;
    private boolean sawScreen = false;

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
            pendingHome = 0;
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

        if (pendingHome != 0) {
            handlePendingGui(client);
            return;
        }

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        ChunkPos myChunk = client.player.getChunkPos();
        for (AbstractClientPlayerEntity other : client.world.getPlayers()) {
            if (other == client.player) continue;
            if (IGNORED_PLAYERS.contains(other.getName().getString())) continue;

            if (other.getChunkPos().equals(myChunk)) {
                startSwitch(client, other.getName().getString());
                break;
            }
        }
    }

    private void startSwitch(MinecraftClient client, String who) {
        int target = currentHome == 1 ? 2 : 1;
        client.getNetworkHandler().sendChatCommand(HOME_COMMAND);
        pendingHome = target;
        guiWait = GUI_TIMEOUT_TICKS;
        sawScreen = false;
        msg(client, who + " na chunku -> wybieram home " + target);
    }

    private void handlePendingGui(MinecraftClient client) {
        if (client.currentScreen instanceof HandledScreen<?> screen) {
            sawScreen = true;
            ScreenHandler handler = screen.getScreenHandler();
            int slotId = findSlot(client, handler, pendingHome);

            if (slotId >= 0) {
                client.interactionManager.clickSlot(
                        handler.syncId, slotId, 0, SlotActionType.PICKUP, client.player);
                currentHome = pendingHome;
                msg(client, "Kliknieto home " + pendingHome);
                pendingHome = 0;
                cooldown = COOLDOWN_TICKS;
                return;
            }

            if (guiWait == 5 && DEBUG) {
                dumpSlots(client, handler);
            }
        }

        if (--guiWait <= 0) {
            msg(client, sawScreen
                    ? "Nie znalazlem home " + pendingHome + " w menu"
                    : "Menu /home sie nie otworzylo");
            pendingHome = 0;
            cooldown = COOLDOWN_TICKS;
        }
    }

    private int findSlot(MinecraftClient client, ScreenHandler handler, int n) {
        int fixed = n == 1 ? HOME_1_SLOT : HOME_2_SLOT;
        if (fixed >= 0) {
            if (fixed < handler.slots.size() && !handler.slots.get(fixed).getStack().isEmpty()) {
                return fixed;
            }
            return -1;
        }

        Pattern hash = Pattern.compile("#\\s*" + n + "(?!\\d)");
        Pattern word = Pattern.compile("(?i)home\\s*" + n + "(?!\\d)");

        for (Slot s : handler.slots) {
            if (s.inventory == client.player.getInventory()) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            if (hash.matcher(st.getName().getString()).find()) return s.id;
        }
        for (Slot s : handler.slots) {
            if (s.inventory == client.player.getInventory()) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            if (word.matcher(st.getName().getString()).find()) return s.id;
        }
        return -1;
    }

    private void dumpSlots(MinecraftClient client, ScreenHandler handler) {
        StringBuilder sb = new StringBuilder("Przedmioty w menu: ");
        int count = 0;
        for (Slot s : handler.slots) {
            if (s.inventory == client.player.getInventory()) continue;
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            sb.append("[").append(s.id).append(": ").append(st.getName().getString()).append("] ");
            if (++count >= 15) break;
        }
        client.player.sendMessage(Text.literal("[HomeSwitch] " + sb), false);
    }

    private void msg(MinecraftClient client, String text) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[HomeSwitch] " + text), true);
        }
    }
}
