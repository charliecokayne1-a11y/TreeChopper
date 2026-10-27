package com.example.shardfarm;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class FarmMenuScreen extends Screen {
    private ButtonWidget startButton;
    private ButtonWidget delayButton;
    private ButtonWidget namedAxeButton;

    public FarmMenuScreen() {
        super(Text.literal("Shard Farm"));
    }

    @Override
    protected void init() {
        int w = 200;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 70;

        startButton = ButtonWidget.builder(startLabel(), b -> {
            // Start reads the block under your crosshair, so close the menu first.
            this.client.setScreen(null);
            FarmBot.toggle(this.client);
        }).dimensions(x, y, w, 20).build();
        addDrawableChild(startButton);

        delayButton = ButtonWidget.builder(delayLabel(), b -> {
            FarmConfig.actionDelayTicks = FarmConfig.actionDelayTicks >= 20 ? 1 : FarmConfig.actionDelayTicks + 1;
            b.setMessage(delayLabel());
        }).dimensions(x, y + 26, w, 20).build();
        addDrawableChild(delayButton);

        namedAxeButton = ButtonWidget.builder(namedAxeLabel(), b -> {
            FarmConfig.requireNamedAxe = !FarmConfig.requireNamedAxe;
            b.setMessage(namedAxeLabel());
        }).dimensions(x, y + 52, w, 20).build();
        addDrawableChild(namedAxeButton);

        TextFieldWidget axeName = new TextFieldWidget(this.textRenderer, x, y + 78, w, 20, Text.literal("Axe name contains"));
        axeName.setMaxLength(32);
        axeName.setText(FarmConfig.axeNameContains);
        axeName.setChangedListener(s -> FarmConfig.axeNameContains = s);
        addDrawableChild(axeName);

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
                .dimensions(x, y + 108, w, 20).build());
    }

    private Text startLabel() {
        return Text.literal(FarmBot.isRunning() ? "Stop farming" : "Start farming (aim at the ground first)");
    }

    private Text delayLabel() {
        return Text.literal("Action delay: " + FarmConfig.actionDelayTicks + " ticks (click to cycle)");
    }

    private Text namedAxeLabel() {
        return Text.literal("Require named axe: " + (FarmConfig.requireNamedAxe ? "ON" : "OFF"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 95, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Axe name contains:"),
                this.width / 2, this.height / 2 - 4, 0xFFAAAAAA);
    }
}
