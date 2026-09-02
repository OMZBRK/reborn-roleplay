package com.reborn.shinobicore.gui;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.gui.framework.Screen;

/**
 * Base of every ShinobiCore screen. The engine {@link Screen} only
 * knows the minimal {@code ScreenRouter}; this class re-exposes the
 * concrete {@link CoreGuiRouter} (and the plugin) to Core's screens —
 * the exact mirror of ShinobiAbilities' {@code AbilityScreen}.
 */
public abstract class CoreScreen extends Screen {

    protected final CoreGuiRouter router;

    protected CoreScreen(CoreGuiRouter router) {
        super(router);
        this.router = router;
    }

    /** The owning plugin, for manager access inside render/onAction. */
    protected ShinobiCore core() {
        return router.plugin();
    }
}
