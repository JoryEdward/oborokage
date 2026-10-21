package oborokage.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu's settings button opens the mod's settings. Loaded only when Mod Menu is present. */
public final class ModMenuEntry implements ModMenuApi {
  @Override
  public ConfigScreenFactory<?> getModConfigScreenFactory() {
    return SettingsScreen::new;
  }
}
