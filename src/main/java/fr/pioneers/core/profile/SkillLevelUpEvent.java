package fr.pioneers.core.profile;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/** 1.9 (MAJ RP) : un skill passe un niveau (bus de Forge, cote serveur). */
public class SkillLevelUpEvent extends Event {

    private final ServerPlayer player;
    private final String skill;
    private final int level;

    public SkillLevelUpEvent(ServerPlayer player, String skill, int level) {
        this.player = player;
        this.skill = skill;
        this.level = level;
    }

    public ServerPlayer player() {
        return player;
    }

    public String skill() {
        return skill;
    }

    public int level() {
        return level;
    }
}
