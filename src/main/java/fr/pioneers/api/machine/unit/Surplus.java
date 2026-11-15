package fr.pioneers.api.machine.unit;

/**
 * 1.0.4-beta (lot 5, Guillaume : « faire en sorte que les generateurs ne se coupent plus s'ils sont pleins », precision : « continuer,
 * surplus perdu ») : REGLE COMMUNE DES PRODUCTEURS d'energie, de vapeur et de chaleur. Un producteur dont la sortie est pleine NE S'ARRETE
 * PLUS : il reste allume, brule ou consomme comme d'habitude, la chaudiere garde sa temperature ; ce qui ne rentre pas est PERDU, et
 * l'interface (ou la fiche au regard) l'affiche : « Surplus perdu ». Le stock ne depasse jamais la contenance (on ne range que la place).
 * <p>
 * Inventaire (avant -> apres) :
 * <ul>
 *     <li>generateurs a combustion des ateliers (solide, liquide, turbine a gaz compacte) : {@link FuelGenerator} n'allumait plus de
 *     combustible reserve pleine -> brule toujours ; sous-produits FLUIDES / GAZEUX qui n'ont plus de place : perdus (objets : la case doit
 *     avoir la place, comme avant) ;</li>
 *     <li>renouvelables (soleil, vent, barrage, geothermie, pile microbienne) : {@link RenewableUnit} ne produisait rien reserve pleine
 *     (la pile ne mangeait plus) -> produit toujours ;</li>
 *     <li>multiblocs generateurs (generateur a combustion, turbine a vapeur, turbine a gaz, cycle combine) et chaudiere industrielle :
 *     {@code industry/multi/MultiProcess.start} refusait un lot reserve pleine (ou vapeur pleine) -> lance toujours le lot ;</li>
 *     <li>dynamo : {@code industry/atelier/DynamoUnit} lachait la charge reserve pleine (« Reserve pleine », la source tournait a vide) ->
 *     garde sa charge ;</li>
 *     <li>turbine de detente (air comprime) : retiree au lot 27 de la 1.0.4-beta ;</li>
 *     <li>machine a balancier : {@code kinetics/sources/BeamEngineUnit} eteignait son feu a la temperature maximale -> le feu continue,
 *     la chaudiere reste a sa temperature maximale ;</li>
 *     <li>chaudiere de l'age du bronze ({@code bronze/steam/FireboxTile}) : brulait deja reserve pleine (eau tenue a 100 C) : inchange,
 *     l'interface dit maintenant « surplus perdu » ;</li>
 *     <li>inchanges : machine a vapeur, moulins, roue a aubes, manege (pas de reserve), collecteur de foudre et absorbeur solaire (pas de
 *     combustible : rangent deja ce qui rentre), REACTEURS nucleaires, RTG et FUSION (equilibrage et suretes a part : non touches).</li>
 * </ul>
 * L'indicateur reste allume {@link #HOLD} ticks apres la derniere perte (pas de clignotement d'un tick a l'autre).
 */
public final class Surplus {

    /** Ticks pendant lesquels « surplus perdu » reste affiche apres une perte. */
    public static final int HOLD = 40;

    private long lostAt = Long.MIN_VALUE;
    private long lost;

    /** Note une production de ce tick : {@code made} produits, {@code kept} ranges ({@code now} = heure du jeu). */
    public void note(long now, double made, double kept) {
        if (made - kept > 1e-6) {
            lostAt = now;
            lost += (long) Math.ceil(made - kept);
        }
    }

    /** Une perte recente ? */
    public boolean active(long now) {
        return lostAt != Long.MIN_VALUE && now - lostAt >= 0 && now - lostAt < HOLD;
    }

    /** Total perdu depuis la pose (non sauve : pour les bancs et les tests). */
    public long total() {
        return lost;
    }
}
