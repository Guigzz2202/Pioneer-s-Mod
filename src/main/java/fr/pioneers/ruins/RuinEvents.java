package fr.pioneers.ruins;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * 1.9 (MAJ RP, bloc 1) : evenements publics des ruines (bus Forge). Chacun porte le joueur (ou null), le site, le tier et la position ; la
 * reputation (bloc 3) et les clans (bloc 4) les ecoutent sans toucher a ce code.
 */
public final class RuinEvents {

    private RuinEvents() {
    }

    public abstract static class SiteEvent extends Event {

        @Nullable
        public final ServerPlayer player;
        public final long site;
        public final int tier;
        public final BlockPos pos;
        public final ServerLevel level;

        protected SiteEvent(@Nullable ServerPlayer player, long site, int tier, BlockPos pos, ServerLevel level) {
            this.player = player;
            this.site = site;
            this.tier = tier;
            this.pos = pos;
            this.level = level;
        }
    }

    /** Premier bloc delabre casse d'un site : la ruine est entamee (elle ne pourra plus etre restauree). */
    public static class Dismantled extends SiteEvent {

        public Dismantled(@Nullable ServerPlayer player, RuinSites.Site s, BlockPos pos, ServerLevel level) {
            super(player, s.id, s.tier, pos, level);
        }
    }

    public static class Restored extends SiteEvent {

        public Restored(@Nullable ServerPlayer player, RuinSites.Site s, ServerLevel level) {
            super(player, s.id, s.tier, s.controller(), level);
        }
    }

    public static class SentinelKilled extends SiteEvent {

        public final String type;

        public SentinelKilled(@Nullable ServerPlayer player, long site, int tier, BlockPos pos, ServerLevel level, String type) {
            super(player, site, tier, pos, level);
            this.type = type;
        }
    }

    public static class SiteStandby extends SiteEvent {

        public SiteStandby(@Nullable ServerPlayer player, RuinSites.Site s, ServerLevel level) {
            super(player, s.id, s.tier, s.controller(), level);
        }
    }

    /** Page de lore lue pour la premiere fois (les notes de parchemin n'en declenchent pas). */
    public static class LoreRead extends Event {

        public final ServerPlayer player;
        public final String entry;
        public final String tier;

        public LoreRead(ServerPlayer player, String entry, String tier) {
            this.player = player;
            this.entry = entry;
            this.tier = tier;
        }
    }
}
