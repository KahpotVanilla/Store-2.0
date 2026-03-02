package io.github.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;

@Plugin(
        id = "store",
        name = "Store",
        version = "1.1.0",
        description = "Hybrid Store plugin entrypoint for Velocity",
        authors = {"Theo"}
)
public final class StoreVelocityPlugin {
    private final Logger logger;

    @Inject
    public StoreVelocityPlugin(Logger logger) {
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("Store loaded on Velocity proxy. GUI flows run on Paper servers; PayPal capture + GUI interactions remain server-side.");
    }
}
