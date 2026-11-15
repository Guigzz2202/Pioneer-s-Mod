package fr.pioneers.common.look;

import fr.pioneers.farm.FarmCard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.4 : fiches au regard calculees par le SERVEUR (demande de Guillaume : « reprendre le concept des fiches de la ferme pour les objets de
 * l'age mecanique qui n'ont pas d'interface »). Un bloc vise qui a une fiche ({@link #has}, connu des deux cotes) fait demander sa fiche au
 * serveur toutes les 10 ticks ({@code client/look/LookCardClient}, paquet {@code network/PacketLookCard}) ; le serveur la construit avec
 * toutes ses donnees (couple, reseau, contenu...) sans rien synchroniser en plus. Dessin : la fiche de la ferme ({@code client/farm/FarmHud}).
 * Les fiches sont declarees par {@link LookCardRegistry}.
 */
public final class LookCards {

    private LookCards() {
    }

    /** Construction d'une fiche (serveur) ; null = pas de fiche pour ce bloc maintenant. */
    public interface Builder {

        @Nullable
        FarmCard build(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, boolean sneaking);
    }

    private record Entry(Predicate<BlockState> applies, Builder builder) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void register(Predicate<BlockState> applies, Builder builder) {
        ENTRIES.add(new Entry(applies, builder));
    }

    /** Le bloc a-t-il une fiche a demander (client et serveur) ? */
    public static boolean has(BlockState state) {
        for (Entry e : ENTRIES) {
            if (e.applies.test(state)) {
                return true;
            }
        }
        return false;
    }

    /** Premiere fiche non nulle des constructeurs qui s'appliquent (serveur). */
    @Nullable
    public static FarmCard build(ServerLevel level, BlockPos pos, ServerPlayer player, boolean sneaking) {
        BlockState state = level.getBlockState(pos);
        for (Entry e : ENTRIES) {
            if (e.applies.test(state)) {
                FarmCard card = e.builder.build(level, pos, state, player, sneaking);
                if (card != null) {
                    return card;
                }
            }
        }
        return null;
    }
}
