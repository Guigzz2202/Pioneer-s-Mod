package fr.pioneers.world.ruins;

import fr.pioneers.core.registries.PiTriggers;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * 1.9 (MAJ RP, bloc 1) : facade publique des ruines (sites, Veille, restauration). Les autres blocs de la MAJ s'y branchent, et par les
 * evenements {@link RuinEvents}.
 */
public final class RuinApi {

    /** Duree de la Veille : un jour de jeu. */
    public static final long STANDBY_TICKS = 24000;

    private RuinApi() {
    }

    @Nullable
    public static RuinSites.Site site(ServerLevel level, long id) {
        return RuinSites.get(level).site(id);
    }

    @Nullable
    public static RuinSites.Site siteAt(ServerLevel level, BlockPos pos) {
        return RuinSites.get(level).siteAt(pos, 2);
    }

    /** Site en Veille (les Sentinelles dorment et ignorent les joueurs, casser ne declenche plus l'alarme). */
    public static boolean standby(ServerLevel level, long id) {
        RuinSites.Site s = site(level, id);
        return s != null && s.standby(level.getGameTime());
    }

    public static void setStandby(ServerLevel level, RuinSites.Site site, @Nullable ServerPlayer player) {
        site.standbyUntil = level.getGameTime() + STANDBY_TICKS;
        site.wrong = 0;
        RuinSites.get(level).changed();
        fr.pioneers.world.ruins.sentinel.Sentinels.sleepSite(level, site);
        MinecraftForge.EVENT_BUS.post(new RuinEvents.SiteStandby(player, site, level));
        //1.9 (MAJ RP, lot 19) : progres « Habilitation » (site mis en Veille par le code de sa Console)
        if (player != null) {
            PiTriggers.MECHANICS.trigger(player, "ruin_standby");
        }
    }

    /** Une ruine dont le chantier de restauration est ouvert (ou finie) protege ses blocs. */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        RuinSites.Site s = RuinSites.get(level).siteAt(pos, 0);
        return s != null && (s.state == RuinSites.State.RESTORING);
    }

    /** Cle des donnees persistantes d'une machine restauree : liste des contributeurs. */
    public static final String RESTORED_BY = "pioneers_restored_by";

    /**
     * Une machine restauree est ouverte a tous ; avec le reglage {@code ruins.restoredOwnerOnly}, seulement a ses contributeurs (et aux
     * operateurs). Appele par {@code MachineBlock.use}.
     */
    public static boolean mayUse(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.level.block.entity.BlockEntity tile) {
        net.minecraft.nbt.CompoundTag data = tile.getPersistentData();
        if (!data.contains(RESTORED_BY) || player.hasPermissions(2)
            || !fr.pioneers.core.common.PiCommonConfig.get(fr.pioneers.core.profile.RpConfig.RESTORED_OWNER_ONLY, false)) {
            return true;
        }
        for (net.minecraft.nbt.Tag t : data.getList(RESTORED_BY, net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            if (net.minecraft.nbt.NbtUtils.loadUUID(t).equals(player.getUUID())) {
                return true;
            }
        }
        return false;
    }

    /** Services rendus par les ruines restaurees a moins de {@code radius} blocs (Eau, Energie, Atelier, Entrepot, Archives). */
    public static java.util.List<String> restoredServicesNear(ServerLevel level, BlockPos pos, double radius) {
        return Restoration.servicesNear(level, pos, radius);
    }
}
