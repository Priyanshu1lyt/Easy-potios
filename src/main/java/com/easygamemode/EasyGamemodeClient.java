package com.easygamemode;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/** Client-only entrypoint. There is intentionally no server entrypoint. */
public class EasyGamemodeClient implements ClientModInitializer {
	public static final String MOD_ID = "easygamemode";

	private static KeyBinding openKey;

	@Override
	public void onInitializeClient() {
		KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
		openKey = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.easygamemode.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openKey.wasPressed()) {
				if (client.currentScreen == null && client.player != null) {
					client.setScreen(new GamemodeScreen());
				}
			}
		});

		// Used to show a simple error when the server rejects a /gamemode command.
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				GamemodeScreen.onServerMessage(message);
			}
		});
	}

	public static KeyBinding getOpenKey() {
		return openKey;
	}
}
