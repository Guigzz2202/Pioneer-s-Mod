package fr.pioneers.api.multiblock;

import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : FICHE d'un multibloc. Une seule declaration par multibloc, cote Python (tools/gc/api/multiblocks.py : le
 * catalogue industriel par adaptateur, les multiblocs maison a la main), qui produit {@link GeneratedMultiblocks} : ne jamais ecrire une
 * fiche a la main en Java. Declaree avec les fiches machines ({@code MachineSpec}) : la fiche machine du controleur porte la meme cle
 * ({@link #machine}) et le lien compacte &lt;-&gt; multibloc est symetrique ({@code MachineSpec.multiblock} / {@link #compact}).
 *
 * @param key        identifiant unique (id du controleur pour les multiblocs industriels)
 * @param machine    cle de la fiche machine du controleur (interface, infobulle, JEI, faces)
 * @param controller ids des blocs controleurs (inchanges : les mondes en dependent)
 * @param compact    cle de la fiche de la machine compacte soeur, ou null
 * @param shape      maniere de definir la forme
 * @param template   gabarit (forme {@link Shape#TEMPLATE} : nom de l'{@code IndustryShape}) ou plan / forme propre
 * @param roles      roles des cases : blocs acceptes par tag (et par la classe du bloc), nombre minimal
 * @param tier       regle du tier de la structure
 * @param ports      types de ports acceptes ({@code PortBlock.Type})
 * @param process    unite qui fait tourner le multibloc ({@code batch}, {@code generator}, {@code well}, {@code pump}, {@code drill},
 *                   {@code storage}, {@code reactor}, {@code fusion}, {@code compute}, {@code offshore}, ou {@code logic} : tile propre)
 * @param heat       corps thermique (chaleur, caloporteur, ports de chaleur, surchauffe)
 * @param style      habillage peint a la formation ({@code IndustryStyle}) ou null
 * @param interior   machine interieure dessinee ({@code BigInterior.Kind}) ou null
 * @param ui         vue de l'interface du controleur
 * @param logic      classe Java du comportement propre (relative a {@code fr.pioneers})
 * @param migrated   le multibloc tourne sur le moteur commun ({@link StructureMatcher}, {@link MultiblockCore}, unites)
 * @param lot        lot du plan ou il migre
 */
public record MultiblockSpec(String key, String machine, List<String> controller, @Nullable String compact, Shape shape, String template,
                             List<RoleSpec> roles, TierRule tier, List<String> ports, String process, boolean heat, @Nullable String style,
                             @Nullable String interior, String ui, @Nullable String logic, boolean migrated, int lot) {

    /**
     * TEMPLATE : gabarit a tailles connues ({@code IndustryShape} : tour, fut, dome, ligne, portique, derrick, reacteurs...) ; FIXED : plan a
     * cases et roles (bases, port, haut fourneau, simulateur) ; PARAMETRIC : taille mesuree par des sondes (stabilisateur, absorbeur,
     * complexe de lancement) ; FREEFORM : pieces reliees, parcours borne (plate-forme offshore, chaudiere).
     */
    public enum Shape { TEMPLATE, FIXED, PARAMETRIC, FREEFORM }

    /** WEAKEST : la plus faible piece a tier (carcasses, toles, cadres, vitres) ; FIXED : tier du bloc controleur ; CORE : case du coeur. */
    public enum TierRule { WEAKEST, FIXED, CORE }

    /**
     * Role d'une case : nom ({@code casing}, {@code glass}, {@code port}, {@code part}, {@code frame}, {@code sheet}, {@code concrete},
     * {@code module}, {@code controller}, {@code reactor}...), tag des blocs acceptes ({@code pioneers:multiblock/<role>}), nombre minimal
     * exige (0 : facultatif).
     */
    public record RoleSpec(String name, String tag, int min) {}

    /** Le role nomme, ou null. */
    @Nullable
    public RoleSpec role(String name) {
        for (RoleSpec r : roles) {
            if (r.name().equals(name)) {
                return r;
            }
        }
        return null;
    }
}
