package com.example.shardfarm;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ShardFarmClient implements ClientModInitializer {
    public static final String MOD_ID = "shardfarm";

    private static KeyBinding menuKey;
    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        // 1.21.9+ uses a Category object instead of a plain string.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shardfarm.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.shardfarm.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menuKey.wasPressed()) {
                client.setScreen(new FarmMenuScreen());
            }
            while (toggleKey.wasPressed()) {
                FarmBot.toggle(client);
            }
            FarmBot.tick(client);
        });
    }
}
