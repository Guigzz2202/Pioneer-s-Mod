package fr.pioneers.robotics.logistics;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Role d'un point logistique (balise ou coffre logistique), inspire de Factorio :
 * <ul>
 *     <li><b>fournisseur</b> (rouge) : les unites peuvent y prendre (filtre facultatif : seulement ces ressources) ;</li>
 *     <li><b>a vider</b> (violet) : les unites l'emptent activement vers les demandeurs, les tampons ou le stockage ;</li>
 *     <li><b>stockage</b> (jaune) : recoit les recoltes et les surplus (filtre facultatif), et sert de source ;</li>
 *     <li><b>demandeur</b> (bleu) : les unites le gardent approvisionne (ressources et quantites cibles) ;</li>
 *     <li><b>tampon</b> (vert) : demande comme un demandeur, et redonne au reseau comme un fournisseur.</li>
 * </ul>
 */
public enum MarkerRole implements StringRepresentable {
    PROVIDER(0xE53935, true, false),
    UNLOAD(0xAB47BC, true, false),
    STORAGE(0xFDD835, true, false),
    REQUESTER(0x1E88E5, false, true),
    BUFFER(0x43A047, true, true);

    public final int color;
    /** Les unites peuvent y prendre des ressources. */
    public final boolean source;
    /** Le point demande des ressources (quantites cibles). */
    public final boolean requests;

    MarkerRole(int color, boolean source, boolean requests) {
        this.color = color;
        this.source = source;
        this.requests = requests;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NotNull
    @Override
    public String getSerializedName() {
        return id();
    }

    public String getTranslationKey() {
        return "robotics.pioneers.marker." + id();
    }

    public static MarkerRole byIndex(int index) {
        MarkerRole[] all = values();
        return all[Math.floorMod(index, all.length)];
    }
}
