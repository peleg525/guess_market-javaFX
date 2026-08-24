package gm.ui;

import gm.engine.GmEngine;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.ComboBox;

import java.util.function.Consumer;

/**
 * Shared wiring every screen/component needs: the engine, who the UI is currently "acting as"
 * (this exercise has no login, so a picker stands in for it - see the README), the animations
 * on/off toggle, and a couple of cross-screen navigation hooks set up once by {@link MarketApp}.
 */
public class AppContext {

    private final GmEngine engine;
    private final ComboBox<String> actingUserCombo;
    private final BooleanProperty animationsEnabled = new SimpleBooleanProperty(false);

    private Consumer<Integer> openEventDetail = id -> { };
    private Runnable refreshAll = () -> { };

    public AppContext(GmEngine engine, ComboBox<String> actingUserCombo) {
        this.engine = engine;
        this.actingUserCombo = actingUserCombo;
    }

    public GmEngine engine() {
        return engine;
    }

    public String actingUsername() {
        return actingUserCombo.getValue();
    }

    public BooleanProperty animationsEnabledProperty() {
        return animationsEnabled;
    }

    public boolean animationsEnabled() {
        return animationsEnabled.get();
    }

    public void setOpenEventDetail(Consumer<Integer> openEventDetail) {
        this.openEventDetail = openEventDetail;
    }

    public void openEventDetail(int eventId) {
        openEventDetail.accept(eventId);
    }

    public void setRefreshAll(Runnable refreshAll) {
        this.refreshAll = refreshAll;
    }

    public void refreshAll() {
        refreshAll.run();
    }
}
