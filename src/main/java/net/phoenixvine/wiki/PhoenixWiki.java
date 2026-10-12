package net.phoenixvine.wiki;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.phoenixvine.wiki.client.suite.SuiteHudConfig;
import net.phoenixvine.wiki.theme.PhoenixTheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PhoenixWiki.MOD_ID)
public class PhoenixWiki {

    public static final String MOD_ID = "phoenix_wiki";
    public static final Logger LOGGER = LoggerFactory.getLogger("PhoenixWiki");

    public PhoenixWiki(IEventBus modEventBus) {
        LOGGER.info("Phoenix Wiki loaded (library mod. see PhoenixWikiAPI to open a wiki screen)");

        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.addListener(this::onClientSetup);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PhoenixTheme.loadThemes();
            SuiteHudConfig.init();
        });
    }
}