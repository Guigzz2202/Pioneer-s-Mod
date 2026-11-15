package fr.pioneers.core.profile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** 1.9 (MAJ RP) : un gain d'XP effectif (apres regles et plafonds) ; source des statistiques de production des clans (bus de Forge, cote serveur). */
public class XpGainedEvent extends Event {

    private final ServerPlayer player;
    private final String skill;
    private final String action;
    private final double amount;
    private final boolean automatic;

    public XpGainedEvent(ServerPlayer player, String skill, String action, double amount, boolean automatic) {
        this.player = player;
        this.skill = skill;
        this.action = action;
        this.amount = amount;
        this.automatic = automatic;
    }

    public ServerPlayer player() {
        return player;
    }

    public String skill() {
        return skill;
    }

    public String action() {
        return action;
    }

    public double amount() {
        return amount;
    }

    public boolean automatic() {
        return automatic;
    }
}
