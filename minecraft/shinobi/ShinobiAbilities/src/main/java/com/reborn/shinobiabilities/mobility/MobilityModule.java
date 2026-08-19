package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.mobility.training.ParkourManager;
import com.reborn.shinobiabilities.mobility.training.ParkourRunner;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobiabilities.CoreServices;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Container for the mobility systems — owns the ability instances, the
 * air-token budget, the per-character toggle store and the trails.
 */
public final class MobilityModule {

    private final AirTokenManager airTokens;
    private final ToggleStore toggles;
    private final NarutoRun narutoRun;
    private final DoubleJump doubleJump;
    private final WallJump wallJump;
    private final FloorShockwave shockwave;
    private final ShinobiDash dash;
    private final Climb climb;
    private final TrailManager trails;
    private final ParkourManager parkours;
    private final ParkourRunner parkourRunner;

    public MobilityModule(JavaPlugin plugin, CoreServices core, CooldownTracker cooldowns) {
        this.airTokens = new AirTokenManager(plugin);
        this.toggles = new ToggleStore(plugin);
        this.narutoRun = new NarutoRun(plugin, core, toggles);
        this.doubleJump = new DoubleJump(plugin, core, airTokens, toggles, cooldowns, narutoRun);
        this.wallJump = new WallJump(plugin, core, airTokens, toggles, cooldowns, narutoRun);
        this.shockwave = new FloorShockwave(plugin, core, airTokens, toggles, cooldowns);
        this.dash = new ShinobiDash(plugin, core, toggles, cooldowns, narutoRun);
        this.climb = new Climb(plugin, core, toggles);
        this.trails = new TrailManager(plugin);
        this.parkours = new ParkourManager(plugin);
        this.parkourRunner = new ParkourRunner(plugin, core, toggles, parkours);
    }

    public void enable() {
        toggles.load();
        trails.load();
        parkours.load();
    }

    public void disable() {
        narutoRun.stopAll();
        trails.stopAll();
        parkourRunner.stopAll();
        parkours.cancelEditors();
        toggles.saveSync();
        trails.saveSync();
        parkours.saveSync();
    }

    public AirTokenManager airTokens() { return airTokens; }
    public ToggleStore toggles()       { return toggles; }
    public NarutoRun narutoRun()       { return narutoRun; }
    public DoubleJump doubleJump()     { return doubleJump; }
    public WallJump wallJump()         { return wallJump; }
    public FloorShockwave shockwave()  { return shockwave; }
    public ShinobiDash dash()          { return dash; }
    public Climb climb()               { return climb; }
    public TrailManager trails()       { return trails; }
    public ParkourManager parkours()   { return parkours; }
    public ParkourRunner training()    { return parkourRunner; }
}
