package com.terraformersmc.modmenu.api;

/**
 * Compile-time stand-in for Mod Menu's API (only what Oborokage uses). Never packaged: when Mod
 * Menu is installed its own interface is used, and without it the entry point is never loaded.
 */
public interface ModMenuApi {
  default ConfigScreenFactory<?> getModConfigScreenFactory() {
    return parent -> null;
  }
}
