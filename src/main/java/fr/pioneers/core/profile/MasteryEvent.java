package fr.pioneers.core.profile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** 1.9 (MAJ RP) : un skill atteint la Maitrise (niveau 10) (bus de Forge, cote serveur). */
public class MasteryEvent extends Event {

    private final ServerPlayer player;
    private final String skill;

    public MasteryEvent(ServerPlayer player, String skill) {
        this.player = player;
        this.skill = skill;
    }

    public ServerPlayer player() {
        return player;
    }

    public String skill() {
        return skill;
    }
}
