package fr.pioneers.society.clan.territory;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * 1.9 (MAJ RP, bloc 4) : les automates du mod (robots, chantiers, faux joueurs) n'agissent sur un bloc reclame que s'ils viennent du meme
 * territoire ou d'un territoire allie. Hors claim, tout automate travaille librement (rien a proteger). Un faux joueur sans origine
 * declaree ({@link #act}) ne touche a aucun claim.
 */
public final class ClanAgents {

    private static final ThreadLocal<BlockPos> ORIGIN = new ThreadLocal<>();

    private ClanAgents() {
    }

    /** Un automate venu de {@code origin} (sa base : baie du robot, poste, machine) peut-il agir sur {@code target} ? */
    public static boolean canAct(Level level, @Nullable BlockPos origin, BlockPos target) {
        if (level.isClientSide || !ClanApi.enabled() || !ClaimIndex.any()) {
            return true;
        }
        String dim = ClanApi.dim(level);
        UUID b = ClaimIndex.owner(dim, ChunkPos.asLong(target.getX() >> 4, target.getZ() >> 4));
        if (b == null) {
            return true;
        }
        if (origin == null) {
            return false;
        }
        UUID a = ClaimIndex.owner(dim, ChunkPos.asLong(origin.getX() >> 4, origin.getZ() >> 4));
        if (a == null || level.getServer() == null) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        ClanData data = ClanData.get(level.getServer());
        //1.9 (MAJ RP, lot 19) : ou le clan proprietaire a ouvert le transit au clan d'origine
        return ClanApi.relation(level.getServer(), data.clan(a), data.clan(b)) == ClanApi.Relation.ALLY || ClanTransit.grants(data.clan(b), a);
    }

    /** Origine de l'automate qui agit en ce moment sur ce fil (null : aucune). */
    @Nullable
    public static BlockPos origin() {
        return ORIGIN.get();
    }

    /** Execute {@code action} au nom d'un automate venu de {@code origin} (les evenements de protection le reconnaissent). */
    public static <T> T act(@Nullable BlockPos origin, java.util.function.Supplier<T> action) {
        BlockPos before = ORIGIN.get();
        ORIGIN.set(origin);
        try {
            return action.get();
        } finally {
            if (before == null) {
                ORIGIN.remove();
            } else {
                ORIGIN.set(before);
            }
        }
    }
}
