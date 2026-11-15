package fr.pioneers.robotics;

import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Taches des unites robotiques : le transport (ordres et balises) et les taches de zone (planificateur). L'outil est celui que
 * tiennent les unites (rendu).
 */
public enum RobotTask {
    /** Transport entre deux blocs (objets, fluides, gaz, donnees), face d'extraction et face d'insertion choisies. */
    LOGISTICS(Items.CHEST, 0x4FC3F7),
    /** Recolte les cultures mures et replante. */
    FARMER(Items.IRON_HOE, 0x9CCC65),
    /** Abat les arbres et replante les pousses. */
    LUMBERJACK(Items.IRON_AXE, 0xA1887F),
    /** Extrait les minerais sous la zone, jusqu'au fond du monde. */
    /** Retire (0.11) : garde sa place dans l'enumeration pour les sauvegardes ; les zones de minage sont supprimees au chargement. */
    MINER(Items.IRON_PICKAXE, 0xB0BEC5),
    /** Terrasse la zone couche par couche, sous la base. */
    EXCAVATOR(Items.IRON_SHOVEL, 0xD7A86E),
    /** Retire (0.11) : ramassage au sol, garde sa place pour les sauvegardes ; ses zones sont supprimees au chargement. */
    COLLECTOR(Items.HOPPER, 0xFFD54F),
    /** Peche dans l'eau de la zone. */
    FISHER(Items.FISHING_ROD, 0x4DD0E1),
    /** Nourrit les animaux pour qu'ils se reproduisent (sans depasser un troupeau maximal). */
    BREEDER(Items.WHEAT, 0xF48FB1),
    /** Chasse les monstres et le surplus des troupeaux. */
    HUNTER(Items.IRON_SWORD, 0xE57373),
    /** Travaux planifies : vide, trace des routes, construit d'apres un plan (planificateur de travaux). */
    BUILDER(Items.SCAFFOLDING, 0xFF8A65),
    /** Apiculture : fait naitre des abeilles jusqu'a remplir les ruches de la zone, recolte rayons et fioles de miel. */
    APIARIST(Items.HONEYCOMB, 0xFFC107);

    /** Objet tenu par les unites et icone de l'interface. */
    public final Item tool;
    public final int color;

    RobotTask(Item tool, int color) {
        this.tool = tool;
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String getTranslationKey() {
        return "robotics.pioneers.task." + id();
    }

    public static RobotTask byIndex(int index) {
        RobotTask[] all = values();
        return all[Math.floorMod(index, all.length)];
    }

    /** Taches proposees par le planificateur de zone. */
    public static final RobotTask[] ZONE_TASKS = {HUNTER, LUMBERJACK, FARMER, FISHER, BREEDER, APIARIST};

    /** Les animaux de ces zones ne sont jamais chasses (elevage, rucher). */
    public boolean protectsAnimals() {
        return this == BREEDER || this == APIARIST;
    }

    /** La tache abat ou extrait des blocs a distance (rayon laser). */
    public boolean usesBeam() {
        return this == MINER || this == EXCAVATOR || this == LUMBERJACK || this == BUILDER;
    }
}
