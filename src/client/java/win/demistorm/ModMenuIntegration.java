package win.demistorm;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

// Mod Menu config integration, class is pretty self-explanatory
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SimpleToggleScreen::new;
    }

    private static class SimpleToggleScreen extends Screen {
        private final Screen parent;
        private WeaponEffectType weaponEffectValue = ConfigHelper.CLIENT.weaponEffect;
        private boolean aimAssistValue = ConfigHelper.CLIENT.aimAssist;

        protected SimpleToggleScreen(Screen parent) {
            super(Text.literal("VR Throwing Extensions Configuration"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            // Boomerang toggle button
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Weapon Effect: " + weaponEffectValue.name()),
                                    btn -> {
                                        // Cycle through options
                                        weaponEffectValue = switch (weaponEffectValue) {
                                            case OFF -> WeaponEffectType.BOOMERANG;
                                            case BOOMERANG -> WeaponEffectType.EMBED;
                                            case EMBED -> WeaponEffectType.OFF;
                                        };
                                        btn.setMessage(Text.literal("Weapon Effect: " + weaponEffectValue.name()));
                                    })
                            .dimensions(width / 2 - 80, height / 4 + 24, 160, 20)  // Wider for better look, spaced for centering
                            .tooltip(Tooltip.of(Text.literal(
                                    """
                                            OFF: Weapons/tools drop normally.
                                            BOOMERANG: Weapons/tools arc back after hitting an entity.
                                            EMBED: Weapons/tools stick into the entity they hit.""")))
                            .build());

            // Aim assist toggle button (below boomerang)
            addDrawableChild(
                    ButtonWidget.builder(
                                    Text.literal("Aim Assist: " + (aimAssistValue ? "ON" : "OFF")),
                                    btn -> {
                                        aimAssistValue = !aimAssistValue;
                                        btn.setMessage(Text.literal(
                                                "Aim Assist: " + (aimAssistValue ? "ON" : "OFF")));
                                    })
                            .dimensions(width / 2 - 80, height / 4 + 54, 160, 20)
                            .tooltip(Tooltip.of(Text.literal("Enables/disables a light aim correction effect, " +
                                    "bridging the gap between the controllers and your real intentions.")))
                            .build());

            // Done button (bottom-center)
            addDrawableChild(
                    ButtonWidget.builder(Text.literal("Done"),
                                    btn -> {
                                        // Save both values
                                        ConfigHelper.CLIENT.weaponEffect = weaponEffectValue;
                                        ConfigHelper.CLIENT.aimAssist = aimAssistValue;
                                        ConfigHelper.write(ConfigHelper.CLIENT); // Save to file
                                        assert client != null;
                                        if (client.getServer() != null) { // Integrated server (singleplayer)
                                            // Apply immediately to ACTIVE
                                            ConfigHelper.ACTIVE.weaponEffect = ConfigHelper.CLIENT.weaponEffect;
                                            ConfigHelper.ACTIVE.aimAssist = ConfigHelper.CLIENT.aimAssist;
                                        }
                                        client.setScreen(parent);
                                    })
                            .dimensions(width / 2 - 100, height - 27, 200, 20)  // Bottom position like native MC
                            .build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            renderBackground(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);
            // Render title at top-center
            context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
        }

        @Override
        public void close() {
            assert client != null;
            client.setScreen(parent);
        }
    }
}