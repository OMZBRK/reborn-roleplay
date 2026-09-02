package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.gui.framework.Screen;

/**
 * Base of every ShinobiAbilities screen. The engine {@link Screen} only
 * knows the minimal {@code ScreenRouter}; this class re-exposes the
 * concrete {@link GuiRouter} so screens keep their rich navigation
 * calls ({@code router.openCatalogueManage(...)}, …).
 */
public abstract class AbilityScreen extends Screen {

    protected final GuiRouter router;

    protected AbilityScreen(GuiRouter router) {
        super(router);
        this.router = router;
    }
}
