package de.einfabo.skintotems.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Zahnrad-Knopf in Mod Menu. Wird nur geladen, wenn Mod Menu installiert ist. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<SkinTotemsScreen> getModConfigScreenFactory() {
		return SkinTotemsScreen::new;
	}
}
