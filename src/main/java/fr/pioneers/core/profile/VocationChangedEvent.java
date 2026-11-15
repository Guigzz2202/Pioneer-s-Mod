package fr.pioneers.core.profile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** 1.9 (MAJ RP) : la vocation change (initiation, reconversion, commande) (bus de Forge, cote serveur). */
public class VocationChangedEvent extends Event {

    private final ServerPlayer player;
    private final String before;
    private final String after;

    public VocationChangedEvent(ServerPlayer player, String before, String after) {
        this.player = player;
        this.before = before;
        this.after = after;
    }

    public ServerPlayer player() {
        return player;
    }

    public String before() {
        return before;
    }

    public String after() {
        return after;
    }
}
