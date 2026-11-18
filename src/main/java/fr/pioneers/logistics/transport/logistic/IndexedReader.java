package fr.pioneers.logistics.transport.logistic;

/**
 * 1.0.5-beta (lot 45, retour 66 : « Le reseau de cable logistique ne doit pas avoir de limite de distance ») : lecteur du parcours d'un reseau
 * ({@link NetworkGraph#build}) : dans un chunk CHARGE, les vrais etats des blocs ({@link Live}) ; dans un chunk NON CHARGE, la topologie gardee
 * ({@link CableIndex}) : le monde n'y est jamais lu. Les fiches sont celles de l'etat plus les faces DECONNECTEES de l'index (retour 63 : fiches
 * latentes : la machine reste un raccord). Pur : le gestionnaire ({@link LogisticNetworks}) et le banc hors jeu l'emploient tel quel.
 */
public final class IndexedReader implements NetworkGraph.Reader {

    /** Le monde vivant (Minecraft : etats des blocs charges, frontieres de claim ; banc : table). */
    public interface Live {

        /** Le chunk de cette position est-il charge ? Seule question posee pour une position d'un chunk non charge. */
        boolean loaded(long pos);

        /** Comme {@link NetworkGraph.Reader#info} : appele seulement si {@link #loaded}. */
        int info(long pos);

        int cableSides(long pos);

        int plugSides(long pos);

        default boolean canCross(long from, long to) {
            return true;
        }
    }

    private final CableIndex index;
    private final Live live;

    public IndexedReader(CableIndex index, Live live) {
        this.index = index;
        this.live = live;
    }

    @Override
    public int info(long pos) {
        return live.loaded(pos) ? live.info(pos) : index.info(pos);
    }

    @Override
    public int cableSides(long pos) {
        return live.loaded(pos) ? live.cableSides(pos) : index.cableSides(pos);
    }

    @Override
    public int plugSides(long pos) {
        return (live.loaded(pos) ? live.plugSides(pos) : index.plugSides(pos)) | index.detached(pos);
    }

    @Override
    public boolean canCross(long from, long to) {
        return live.canCross(from, to);
    }
}
