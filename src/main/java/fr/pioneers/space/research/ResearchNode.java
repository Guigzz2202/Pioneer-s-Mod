package fr.pioneers.space.research;

import fr.pioneers.Pioneers;
import fr.pioneers.registries.PiBaseMachines;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.registries.PiItems;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.registries.PiScienceMachines;
import fr.pioneers.registries.PiTransport;
import fr.pioneers.registries.PiWorldBlocks;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.UnitTier;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.mining.DrillHead;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.module.RocketModule;
import fr.pioneers.transport.logistic.CableLevel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * L'arbre technologique spatial, organise en 10 chapitres (un par systeme : Terre-Lune, Mars, systeme interieur, ceinture,
 * Jupiter, Saturne, Uranus, Neptune, Pluton, puis la singularite).
 * <p>
 * Chaque astre se maitrise en quatre paliers :
 * <ol>
 *     <li><b>Orbite</b> (laboratoire) : on l'etudie avec les donnees des sondes envoyees autour de lui. Donne le droit de s'y poser.</li>
 *     <li><b>Surface</b> : sur place, on analyse plusieurs biomes avec le scanner de surface.</li>
 *     <li><b>Geologie</b> : le spectrometre geologique analyse les minerais extraits par la base miniere de l'astre.</li>
 *     <li><b>Biologie</b> : le sequenceur biologique analyse (en grande quantite) les recoltes des plantes de l'astre.</li>
 * </ol>
 * Le systeme suivant se decouvre des qu'un astre du systeme courant est entierement maitrise (biologie terminee) ;
 * le joueur peut ensuite tout finir a 100 % ou foncer.
 * La recherche d'asteroides (broyeur d'asteroides) ouvre les sondes plus puissantes.
 * <p>
 * Les recherches de laboratoire se decoupent en unites traitees par les tables de recherche ; les autres se remplissent
 * d'echantillons ({@code units} = echantillons a fournir, de chaque type pour les asteroides).
 * <p>
 * L'ordre des constantes sert aux sauvegardes : on n'en retire jamais, on ajoute a la suite.
 * Un prerequis "A|B|C" est satisfait par l'une des recherches citees.
 */
public enum ResearchNode {
    //Laboratoire : ere, colonne, ligne, unites, secondes, icone, cout par unite, prerequis
    //Echantillons : type, astre, ere, colonne, ligne, echantillons, icone, prerequis
    TELEMETRY(0, 0, 2, 20, 5, () -> new ItemStack(PiItems.module(RocketModule.PROBE)), costs("O", 150)),
    LAUNCH_RELIABILITY(0, 1, 1, 30, 6, () -> new ItemStack(PiBaseMachines.LAUNCH_PAD_CORE), costs("O", 200), "TELEMETRY"),
    LUNAR_RELAYS(0, 1, 3, 30, 6, () -> new ItemStack(PiScienceMachines.DATA_ANTENNA), costs("O", 100, "L", 200), "TELEMETRY"),
    RADIO_ASTRONOMY(0, 1, 2, 30, 8, () -> new ItemStack(PiTransport.logisticCable(CableLevel.BASIC, false)), costs("O", 150, "L", 150), "TELEMETRY"),
    CARGO_LOGISTICS(0, 2, 1, 40, 10, () -> new ItemStack(PiItems.module(RocketModule.CARGO_ITEMS)), costs("O", 500, "L", 375, "S", 250), "LAUNCH_RELIABILITY", "LUNAR_RELAYS"),
    BLUEPRINT_T2(0, 5, 2, 40, 10, () -> rocket(RocketTier.TIER_2), costs("L", 1000, "S", 625, "O", 250), "LUNAR_RELAYS", "RADIO_ASTRONOMY", "ORBIT_MOON"),
    /** Robotique : plans de toutes les unites (drones, robots, rovers, tortues) a la table d'assemblage robotique. */
    ROBOTICS(0, 4, 3, 30, 8, () -> new ItemStack(PiRobotics.UNITS.get(UnitKind.DRONE).get(UnitTier.T1).get()), costs("O", 150, "L", 250), "LUNAR_RELAYS", "SURFACE_MOON"),
    SPACE_AGRONOMY(0, 4, 4, 40, 8, () -> new ItemStack(PiItems.SEEDS.get(SpacePlant.SILVER_BEAN).get()), costs("O", 100, "L", 250), "LUNAR_RELAYS", "SURFACE_MOON"),
    MARS_CARTOGRAPHY(1, 6, 2, 50, 10, () -> new ItemStack(PiBlocks.MARS_SAND), costs("S", 600, "L", 400), "RADIO_ASTRONOMY", "BLUEPRINT_T2", "PROBE_T2", "BIOLOGY_MOON"),
    ION_PROPULSION(1, 7, 1, 50, 12, () -> new ItemStack(Items.BLAZE_POWDER), costs("S", 500, "M", 300), "CARGO_LOGISTICS", "MARS_CARTOGRAPHY"),
    DATA_COMPRESSION(1, 7, 4, 60, 12, () -> new ItemStack(PiScienceMachines.RESEARCH_TABLE), costs("S", 600, "M", 400), "MARS_CARTOGRAPHY"),
    CRYO_TANKS(1, 7, 3, 50, 12, () -> new ItemStack(Items.BLUE_ICE), costs("L", 500, "M", 300), "CARGO_LOGISTICS", "MARS_CARTOGRAPHY"),
    INNER_SYSTEM(2, 12, 2, 60, 15, () -> new ItemStack(PiBlocks.VENUS_REGOLITH), costs("M", 800, "S", 500), "MARS_CARTOGRAPHY", "BLUEPRINT_T3", "BIOLOGY_MARS|BIOLOGY_PHOBOS|BIOLOGY_DEIMOS"),
    BELT_SURVEY(3, 17, 2, 60, 15, () -> new ItemStack(PiBlocks.CERES_SALT), costs("Me", 600, "V", 400), "BLUEPRINT_T4", "HELIOPHYSICS", "BIOLOGY_VENUS|BIOLOGY_MERCURY"),
    BLUEPRINT_T3(1, 11, 1, 60, 12, () -> rocket(RocketTier.TIER_3), costs("M", 1000, "S", 600, "L", 400), "BLUEPRINT_T2", "ORBIT_MARS"),
    QUANTUM_LABS(1, 11, 4, 60, 15, () -> new ItemStack(PiScienceMachines.RESEARCH_TABLE), costs("O", 500, "L", 500, "S", 500, "M", 500), "DATA_COMPRESSION"),
    DEEP_TELESCOPE(8, 42, 2, 120, 22, () -> new ItemStack(Items.SPYGLASS), costs("N", 1400, "U", 700, "S", 1200), "BLUEPRINT_T9", "BIOLOGY_TRITON"),
    SINGULARITY_THEORY(9, 46, 2, 160, 30, () -> new ItemStack(Items.NETHER_STAR), costs("P", 1500, "N", 800, "H", 1000, "S", 1500, "O", 500), "BLUEPRINT_T10", "FUSION_DRIVES", "ASTEROIDS_4", "BIOLOGY_PLUTO|BIOLOGY_CHARON"),
    THERMAL_SHIELDING(2, 13, 1, 60, 15, () -> new ItemStack(Items.MAGMA_CREAM), costs("V", 800, "M", 400), "INNER_SYSTEM"),
    BLUEPRINT_T4(2, 16, 1, 70, 15, () -> rocket(RocketTier.TIER_4), costs("V", 1000, "M", 600, "S", 400), "BLUEPRINT_T3", "ORBIT_VENUS"),
    HELIOPHYSICS(2, 16, 2, 70, 15, () -> new ItemStack(Items.SUNFLOWER), costs("Me", 800, "V", 600), "BLUEPRINT_T4", "PROBE_T3"),
    PHOTONIC_SAILS(3, 18, 1, 60, 15, () -> new ItemStack(Items.PHANTOM_MEMBRANE), costs("Me", 600, "S", 600), "HELIOPHYSICS"),
    BLUEPRINT_T5(3, 18, 4, 80, 16, () -> rocket(RocketTier.TIER_5), costs("Me", 1200, "V", 800, "S", 600), "HELIOPHYSICS", "BELT_SURVEY"),
    PHOTONIC_COMPUTING(4, 25, 1, 70, 16, () -> new ItemStack(Items.GLOWSTONE_DUST), costs("H", 800, "Me", 400), "BLUEPRINT_T5"),
    JOVIAN_SYSTEM(4, 24, 2, 80, 16, () -> new ItemStack(PiBlocks.IO_SULFUR), costs("H", 1000, "Me", 600, "S", 800), "BLUEPRINT_T5", "PROBE_T4", "BIOLOGY_CERES|BIOLOGY_VESTA|BIOLOGY_PALLAS|BIOLOGY_HYGIEA"),
    BLUEPRINT_T6(4, 25, 3, 90, 18, () -> rocket(RocketTier.TIER_6), costs("H", 1400, "Me", 800, "V", 600), "JOVIAN_SYSTEM"),
    SATURN_SYSTEM(5, 30, 2, 90, 18, () -> new ItemStack(PiBlocks.TITAN_SAND), costs("J", 1200, "H", 600, "S", 800), "BLUEPRINT_T6", "BIOLOGY_IO|BIOLOGY_EUROPA|BIOLOGY_GANYMEDE|BIOLOGY_CALLISTO"),
    RADIATION_HARDENING(5, 31, 1, 80, 18, () -> new ItemStack(Items.SHIELD), costs("J", 1000, "H", 500), "BLUEPRINT_T6"),
    BLUEPRINT_T7(5, 31, 3, 100, 18, () -> rocket(RocketTier.TIER_7), costs("J", 1500, "H", 800, "S", 600), "SATURN_SYSTEM"),
    URANUS_SYSTEM(6, 35, 2, 100, 20, () -> new ItemStack(PiBlocks.TITANIA_REGOLITH), costs("Sa", 1200, "J", 600, "S", 1000), "BLUEPRINT_T7", "PROBE_T5", "BIOLOGY_ENCELADUS|BIOLOGY_TITAN"),
    AEROCAPTURE(6, 36, 1, 90, 20, () -> new ItemStack(Items.ELYTRA), costs("Sa", 1000, "J", 500), "BLUEPRINT_T7"),
    BLUEPRINT_T8(6, 36, 3, 110, 20, () -> rocket(RocketTier.TIER_8), costs("Sa", 1600, "J", 900, "H", 600), "URANUS_SYSTEM"),
    NEPTUNE_SYSTEM(7, 39, 2, 110, 20, () -> new ItemStack(PiBlocks.TRITON_FROST), costs("U", 1300, "Sa", 600, "S", 1000), "BLUEPRINT_T8", "BIOLOGY_MIRANDA|BIOLOGY_TITANIA"),
    DEEP_SPACE_RELAYS(7, 40, 1, 100, 20, () -> new ItemStack(Items.RECOVERY_COMPASS), costs("U", 1000, "Sa", 500), "BLUEPRINT_T8"),
    BLUEPRINT_T9(7, 40, 3, 120, 22, () -> rocket(RocketTier.TIER_9), costs("U", 1700, "Sa", 1000, "J", 600), "NEPTUNE_SYSTEM"),
    FUSION_DRIVES(8, 43, 1, 110, 22, () -> new ItemStack(Items.HEART_OF_THE_SEA), costs("N", 1200, "U", 600), "BLUEPRINT_T9"),
    BLUEPRINT_T10(8, 43, 3, 140, 25, () -> rocket(RocketTier.TIER_10), costs("N", 1800, "U", 1000, "H", 800), "DEEP_TELESCOPE"),
    PLANETARY_PROSPECTING(0, 4, 2, 40, 8, () -> new ItemStack(PiBlocks.PROSPECTING_RADAR), costs("L", 300, "O", 150), "LUNAR_RELAYS", "SURFACE_MOON"),
    DEEP_CORE_DRILLING(1, 11, 3, 50, 10, () -> new ItemStack(PiItems.DRILL_HEADS.get(DrillHead.MARSIUM).get()), costs("M", 400, "L", 300), "PLANETARY_PROSPECTING", "SURFACE_MARS|SURFACE_PHOBOS|SURFACE_DEIMOS"),
    AUTONOMOUS_ROVERS(2, 13, 4, 60, 14, () -> new ItemStack(PiRobotics.UNITS.get(UnitKind.ROVER).get(UnitTier.T2).get()), costs("V", 600, "M", 400), "DEEP_CORE_DRILLING", "INNER_SYSTEM"),
    EXOTIC_METALLURGY(2, 16, 3, 70, 15, () -> new ItemStack(PiItems.INGOTS.get(MiningMaterial.MERCURIUM).get()), costs("Me", 700, "V", 500), "AUTONOMOUS_ROVERS", "GEOLOGY_VENUS|GEOLOGY_MERCURY"),
    PROBE_T2(0, 4, 1, 30, 8, () -> probe(2), costs("O", 200, "L", 300), "SURFACE_MOON", "RADIO_ASTRONOMY"),
    PROBE_T3(2, 13, 3, 50, 12, () -> probe(3), costs("V", 500, "M", 300), "INNER_SYSTEM", "ASTEROIDS_1"),
    PROBE_T4(3, 19, 3, 70, 16, () -> probe(4), costs("H", 700, "Me", 400), "BELT_SURVEY", "ASTEROIDS_2"),
    PROBE_T5(5, 32, 3, 90, 18, () -> probe(5), costs("Sa", 900, "J", 500), "SATURN_SYSTEM", "ASTEROIDS_3"),
    ASTEROIDS_1(Kind.ASTEROIDS, null, 0, 4, 3, 32, () -> meteorite("rock"), "SURFACE_MOON"),
    ASTEROIDS_2(Kind.ASTEROIDS, null, 3, 18, 3, 128, () -> meteorite("iron"), "BELT_SURVEY", "ASTEROIDS_1"),
    ASTEROIDS_3(Kind.ASTEROIDS, null, 5, 32, 1, 384, () -> meteorite("ice"), "SATURN_SYSTEM", "ASTEROIDS_2"),
    ASTEROIDS_4(Kind.ASTEROIDS, null, 8, 43, 4, 1024, () -> meteorite("volcanic"), "DEEP_TELESCOPE", "ASTEROIDS_3"),
    ORBIT_MOON(0, 3, 1, 20, 5, () -> bodyIcon(Body.MOON, Kind.LAB), costs("L", 100, "O", 50), "TELEMETRY"),
    SURFACE_MOON(Kind.SURFACE, Body.MOON, 0, 3, 2, 4, () -> bodyIcon(Body.MOON, Kind.SURFACE), "ORBIT_MOON"),
    GEOLOGY_MOON(Kind.GEOLOGY, Body.MOON, 0, 3, 3, 128, () -> bodyIcon(Body.MOON, Kind.GEOLOGY), "SURFACE_MOON"),
    BIOLOGY_MOON(Kind.BIOLOGY, Body.MOON, 0, 3, 4, 512, () -> bodyIcon(Body.MOON, Kind.BIOLOGY), "GEOLOGY_MOON"),
    ORBIT_MARS(1, 8, 1, 40, 10, () -> bodyIcon(Body.MARS, Kind.LAB), costs("M", 300, "S", 150), "MARS_CARTOGRAPHY"),
    SURFACE_MARS(Kind.SURFACE, Body.MARS, 1, 8, 2, 6, () -> bodyIcon(Body.MARS, Kind.SURFACE), "ORBIT_MARS"),
    GEOLOGY_MARS(Kind.GEOLOGY, Body.MARS, 1, 8, 3, 192, () -> bodyIcon(Body.MARS, Kind.GEOLOGY), "SURFACE_MARS"),
    BIOLOGY_MARS(Kind.BIOLOGY, Body.MARS, 1, 8, 4, 768, () -> bodyIcon(Body.MARS, Kind.BIOLOGY), "GEOLOGY_MARS"),
    ORBIT_PHOBOS(1, 9, 1, 30, 10, () -> bodyIcon(Body.PHOBOS, Kind.LAB), costs("M", 250, "S", 100), "MARS_CARTOGRAPHY"),
    SURFACE_PHOBOS(Kind.SURFACE, Body.PHOBOS, 1, 9, 2, 4, () -> bodyIcon(Body.PHOBOS, Kind.SURFACE), "ORBIT_PHOBOS"),
    GEOLOGY_PHOBOS(Kind.GEOLOGY, Body.PHOBOS, 1, 9, 3, 192, () -> bodyIcon(Body.PHOBOS, Kind.GEOLOGY), "SURFACE_PHOBOS"),
    BIOLOGY_PHOBOS(Kind.BIOLOGY, Body.PHOBOS, 1, 9, 4, 768, () -> bodyIcon(Body.PHOBOS, Kind.BIOLOGY), "GEOLOGY_PHOBOS"),
    ORBIT_DEIMOS(1, 10, 1, 30, 10, () -> bodyIcon(Body.DEIMOS, Kind.LAB), costs("M", 250, "S", 100), "MARS_CARTOGRAPHY"),
    SURFACE_DEIMOS(Kind.SURFACE, Body.DEIMOS, 1, 10, 2, 3, () -> bodyIcon(Body.DEIMOS, Kind.SURFACE), "ORBIT_DEIMOS"),
    GEOLOGY_DEIMOS(Kind.GEOLOGY, Body.DEIMOS, 1, 10, 3, 192, () -> bodyIcon(Body.DEIMOS, Kind.GEOLOGY), "SURFACE_DEIMOS"),
    BIOLOGY_DEIMOS(Kind.BIOLOGY, Body.DEIMOS, 1, 10, 4, 768, () -> bodyIcon(Body.DEIMOS, Kind.BIOLOGY), "GEOLOGY_DEIMOS"),
    ORBIT_VENUS(2, 14, 1, 50, 12, () -> bodyIcon(Body.VENUS, Kind.LAB), costs("V", 400, "S", 200), "INNER_SYSTEM"),
    SURFACE_VENUS(Kind.SURFACE, Body.VENUS, 2, 14, 2, 6, () -> bodyIcon(Body.VENUS, Kind.SURFACE), "ORBIT_VENUS"),
    GEOLOGY_VENUS(Kind.GEOLOGY, Body.VENUS, 2, 14, 3, 256, () -> bodyIcon(Body.VENUS, Kind.GEOLOGY), "SURFACE_VENUS"),
    BIOLOGY_VENUS(Kind.BIOLOGY, Body.VENUS, 2, 14, 4, 1024, () -> bodyIcon(Body.VENUS, Kind.BIOLOGY), "GEOLOGY_VENUS"),
    ORBIT_MERCURY(2, 15, 1, 50, 12, () -> bodyIcon(Body.MERCURY, Kind.LAB), costs("Me", 400, "V", 150), "INNER_SYSTEM"),
    SURFACE_MERCURY(Kind.SURFACE, Body.MERCURY, 2, 15, 2, 5, () -> bodyIcon(Body.MERCURY, Kind.SURFACE), "ORBIT_MERCURY"),
    GEOLOGY_MERCURY(Kind.GEOLOGY, Body.MERCURY, 2, 15, 3, 256, () -> bodyIcon(Body.MERCURY, Kind.GEOLOGY), "SURFACE_MERCURY"),
    BIOLOGY_MERCURY(Kind.BIOLOGY, Body.MERCURY, 2, 15, 4, 1024, () -> bodyIcon(Body.MERCURY, Kind.BIOLOGY), "GEOLOGY_MERCURY"),
    ORBIT_CERES(3, 20, 1, 50, 14, () -> bodyIcon(Body.CERES, Kind.LAB), costs("H", 350, "Me", 200), "BELT_SURVEY"),
    SURFACE_CERES(Kind.SURFACE, Body.CERES, 3, 20, 2, 4, () -> bodyIcon(Body.CERES, Kind.SURFACE), "ORBIT_CERES"),
    GEOLOGY_CERES(Kind.GEOLOGY, Body.CERES, 3, 20, 3, 320, () -> bodyIcon(Body.CERES, Kind.GEOLOGY), "SURFACE_CERES"),
    BIOLOGY_CERES(Kind.BIOLOGY, Body.CERES, 3, 20, 4, 1280, () -> bodyIcon(Body.CERES, Kind.BIOLOGY), "GEOLOGY_CERES"),
    ORBIT_VESTA(3, 21, 1, 50, 14, () -> bodyIcon(Body.VESTA, Kind.LAB), costs("H", 350, "Me", 200), "BELT_SURVEY"),
    SURFACE_VESTA(Kind.SURFACE, Body.VESTA, 3, 21, 2, 4, () -> bodyIcon(Body.VESTA, Kind.SURFACE), "ORBIT_VESTA"),
    GEOLOGY_VESTA(Kind.GEOLOGY, Body.VESTA, 3, 21, 3, 320, () -> bodyIcon(Body.VESTA, Kind.GEOLOGY), "SURFACE_VESTA"),
    BIOLOGY_VESTA(Kind.BIOLOGY, Body.VESTA, 3, 21, 4, 1280, () -> bodyIcon(Body.VESTA, Kind.BIOLOGY), "GEOLOGY_VESTA"),
    ORBIT_PALLAS(3, 22, 1, 50, 14, () -> bodyIcon(Body.PALLAS, Kind.LAB), costs("H", 350, "V", 200), "BELT_SURVEY"),
    SURFACE_PALLAS(Kind.SURFACE, Body.PALLAS, 3, 22, 2, 3, () -> bodyIcon(Body.PALLAS, Kind.SURFACE), "ORBIT_PALLAS"),
    GEOLOGY_PALLAS(Kind.GEOLOGY, Body.PALLAS, 3, 22, 3, 320, () -> bodyIcon(Body.PALLAS, Kind.GEOLOGY), "SURFACE_PALLAS"),
    BIOLOGY_PALLAS(Kind.BIOLOGY, Body.PALLAS, 3, 22, 4, 1280, () -> bodyIcon(Body.PALLAS, Kind.BIOLOGY), "GEOLOGY_PALLAS"),
    ORBIT_HYGIEA(3, 23, 1, 50, 14, () -> bodyIcon(Body.HYGIEA, Kind.LAB), costs("H", 350, "S", 300), "BELT_SURVEY"),
    SURFACE_HYGIEA(Kind.SURFACE, Body.HYGIEA, 3, 23, 2, 3, () -> bodyIcon(Body.HYGIEA, Kind.SURFACE), "ORBIT_HYGIEA"),
    GEOLOGY_HYGIEA(Kind.GEOLOGY, Body.HYGIEA, 3, 23, 3, 320, () -> bodyIcon(Body.HYGIEA, Kind.GEOLOGY), "SURFACE_HYGIEA"),
    BIOLOGY_HYGIEA(Kind.BIOLOGY, Body.HYGIEA, 3, 23, 4, 1280, () -> bodyIcon(Body.HYGIEA, Kind.BIOLOGY), "GEOLOGY_HYGIEA"),
    ORBIT_IO(4, 26, 1, 60, 16, () -> bodyIcon(Body.IO, Kind.LAB), costs("J", 500, "H", 200), "JOVIAN_SYSTEM"),
    SURFACE_IO(Kind.SURFACE, Body.IO, 4, 26, 2, 5, () -> bodyIcon(Body.IO, Kind.SURFACE), "ORBIT_IO"),
    GEOLOGY_IO(Kind.GEOLOGY, Body.IO, 4, 26, 3, 384, () -> bodyIcon(Body.IO, Kind.GEOLOGY), "SURFACE_IO"),
    BIOLOGY_IO(Kind.BIOLOGY, Body.IO, 4, 26, 4, 1536, () -> bodyIcon(Body.IO, Kind.BIOLOGY), "GEOLOGY_IO"),
    ORBIT_EUROPA(4, 27, 1, 60, 16, () -> bodyIcon(Body.EUROPA, Kind.LAB), costs("J", 500, "H", 200), "JOVIAN_SYSTEM"),
    SURFACE_EUROPA(Kind.SURFACE, Body.EUROPA, 4, 27, 2, 3, () -> bodyIcon(Body.EUROPA, Kind.SURFACE), "ORBIT_EUROPA"),
    GEOLOGY_EUROPA(Kind.GEOLOGY, Body.EUROPA, 4, 27, 3, 384, () -> bodyIcon(Body.EUROPA, Kind.GEOLOGY), "SURFACE_EUROPA"),
    BIOLOGY_EUROPA(Kind.BIOLOGY, Body.EUROPA, 4, 27, 4, 1536, () -> bodyIcon(Body.EUROPA, Kind.BIOLOGY), "GEOLOGY_EUROPA"),
    ORBIT_GANYMEDE(4, 28, 1, 60, 16, () -> bodyIcon(Body.GANYMEDE, Kind.LAB), costs("J", 500, "H", 200), "JOVIAN_SYSTEM"),
    SURFACE_GANYMEDE(Kind.SURFACE, Body.GANYMEDE, 4, 28, 2, 3, () -> bodyIcon(Body.GANYMEDE, Kind.SURFACE), "ORBIT_GANYMEDE"),
    GEOLOGY_GANYMEDE(Kind.GEOLOGY, Body.GANYMEDE, 4, 28, 3, 384, () -> bodyIcon(Body.GANYMEDE, Kind.GEOLOGY), "SURFACE_GANYMEDE"),
    BIOLOGY_GANYMEDE(Kind.BIOLOGY, Body.GANYMEDE, 4, 28, 4, 1536, () -> bodyIcon(Body.GANYMEDE, Kind.BIOLOGY), "GEOLOGY_GANYMEDE"),
    ORBIT_CALLISTO(4, 29, 1, 60, 16, () -> bodyIcon(Body.CALLISTO, Kind.LAB), costs("J", 500, "H", 200), "JOVIAN_SYSTEM"),
    SURFACE_CALLISTO(Kind.SURFACE, Body.CALLISTO, 4, 29, 2, 3, () -> bodyIcon(Body.CALLISTO, Kind.SURFACE), "ORBIT_CALLISTO"),
    GEOLOGY_CALLISTO(Kind.GEOLOGY, Body.CALLISTO, 4, 29, 3, 384, () -> bodyIcon(Body.CALLISTO, Kind.GEOLOGY), "SURFACE_CALLISTO"),
    BIOLOGY_CALLISTO(Kind.BIOLOGY, Body.CALLISTO, 4, 29, 4, 1536, () -> bodyIcon(Body.CALLISTO, Kind.BIOLOGY), "GEOLOGY_CALLISTO"),
    ORBIT_ENCELADUS(5, 33, 1, 70, 18, () -> bodyIcon(Body.ENCELADUS, Kind.LAB), costs("Sa", 600, "J", 250), "SATURN_SYSTEM"),
    SURFACE_ENCELADUS(Kind.SURFACE, Body.ENCELADUS, 5, 33, 2, 3, () -> bodyIcon(Body.ENCELADUS, Kind.SURFACE), "ORBIT_ENCELADUS"),
    GEOLOGY_ENCELADUS(Kind.GEOLOGY, Body.ENCELADUS, 5, 33, 3, 448, () -> bodyIcon(Body.ENCELADUS, Kind.GEOLOGY), "SURFACE_ENCELADUS"),
    BIOLOGY_ENCELADUS(Kind.BIOLOGY, Body.ENCELADUS, 5, 33, 4, 1792, () -> bodyIcon(Body.ENCELADUS, Kind.BIOLOGY), "GEOLOGY_ENCELADUS"),
    ORBIT_TITAN(5, 34, 1, 70, 18, () -> bodyIcon(Body.TITAN, Kind.LAB), costs("Sa", 600, "J", 250), "SATURN_SYSTEM"),
    SURFACE_TITAN(Kind.SURFACE, Body.TITAN, 5, 34, 2, 4, () -> bodyIcon(Body.TITAN, Kind.SURFACE), "ORBIT_TITAN"),
    GEOLOGY_TITAN(Kind.GEOLOGY, Body.TITAN, 5, 34, 3, 448, () -> bodyIcon(Body.TITAN, Kind.GEOLOGY), "SURFACE_TITAN"),
    BIOLOGY_TITAN(Kind.BIOLOGY, Body.TITAN, 5, 34, 4, 1792, () -> bodyIcon(Body.TITAN, Kind.BIOLOGY), "GEOLOGY_TITAN"),
    ORBIT_MIRANDA(6, 37, 1, 80, 20, () -> bodyIcon(Body.MIRANDA, Kind.LAB), costs("U", 700, "Sa", 300), "URANUS_SYSTEM"),
    SURFACE_MIRANDA(Kind.SURFACE, Body.MIRANDA, 6, 37, 2, 3, () -> bodyIcon(Body.MIRANDA, Kind.SURFACE), "ORBIT_MIRANDA"),
    GEOLOGY_MIRANDA(Kind.GEOLOGY, Body.MIRANDA, 6, 37, 3, 512, () -> bodyIcon(Body.MIRANDA, Kind.GEOLOGY), "SURFACE_MIRANDA"),
    BIOLOGY_MIRANDA(Kind.BIOLOGY, Body.MIRANDA, 6, 37, 4, 2048, () -> bodyIcon(Body.MIRANDA, Kind.BIOLOGY), "GEOLOGY_MIRANDA"),
    ORBIT_TITANIA(6, 38, 1, 80, 20, () -> bodyIcon(Body.TITANIA, Kind.LAB), costs("U", 700, "Sa", 300), "URANUS_SYSTEM"),
    SURFACE_TITANIA(Kind.SURFACE, Body.TITANIA, 6, 38, 2, 3, () -> bodyIcon(Body.TITANIA, Kind.SURFACE), "ORBIT_TITANIA"),
    GEOLOGY_TITANIA(Kind.GEOLOGY, Body.TITANIA, 6, 38, 3, 512, () -> bodyIcon(Body.TITANIA, Kind.GEOLOGY), "SURFACE_TITANIA"),
    BIOLOGY_TITANIA(Kind.BIOLOGY, Body.TITANIA, 6, 38, 4, 2048, () -> bodyIcon(Body.TITANIA, Kind.BIOLOGY), "GEOLOGY_TITANIA"),
    ORBIT_TRITON(7, 41, 1, 90, 20, () -> bodyIcon(Body.TRITON, Kind.LAB), costs("N", 800, "U", 300), "NEPTUNE_SYSTEM"),
    SURFACE_TRITON(Kind.SURFACE, Body.TRITON, 7, 41, 2, 3, () -> bodyIcon(Body.TRITON, Kind.SURFACE), "ORBIT_TRITON"),
    GEOLOGY_TRITON(Kind.GEOLOGY, Body.TRITON, 7, 41, 3, 576, () -> bodyIcon(Body.TRITON, Kind.GEOLOGY), "SURFACE_TRITON"),
    BIOLOGY_TRITON(Kind.BIOLOGY, Body.TRITON, 7, 41, 4, 2304, () -> bodyIcon(Body.TRITON, Kind.BIOLOGY), "GEOLOGY_TRITON"),
    ORBIT_PLUTO(8, 44, 1, 100, 22, () -> bodyIcon(Body.PLUTO, Kind.LAB), costs("P", 900, "N", 300), "DEEP_TELESCOPE"),
    SURFACE_PLUTO(Kind.SURFACE, Body.PLUTO, 8, 44, 2, 5, () -> bodyIcon(Body.PLUTO, Kind.SURFACE), "ORBIT_PLUTO"),
    GEOLOGY_PLUTO(Kind.GEOLOGY, Body.PLUTO, 8, 44, 3, 640, () -> bodyIcon(Body.PLUTO, Kind.GEOLOGY), "SURFACE_PLUTO"),
    BIOLOGY_PLUTO(Kind.BIOLOGY, Body.PLUTO, 8, 44, 4, 2560, () -> bodyIcon(Body.PLUTO, Kind.BIOLOGY), "GEOLOGY_PLUTO"),
    ORBIT_CHARON(8, 45, 1, 100, 22, () -> bodyIcon(Body.CHARON, Kind.LAB), costs("P", 900, "N", 300), "DEEP_TELESCOPE"),
    SURFACE_CHARON(Kind.SURFACE, Body.CHARON, 8, 45, 2, 4, () -> bodyIcon(Body.CHARON, Kind.SURFACE), "ORBIT_CHARON"),
    GEOLOGY_CHARON(Kind.GEOLOGY, Body.CHARON, 8, 45, 3, 640, () -> bodyIcon(Body.CHARON, Kind.GEOLOGY), "SURFACE_CHARON"),
    BIOLOGY_CHARON(Kind.BIOLOGY, Body.CHARON, 8, 45, 4, 2560, () -> bodyIcon(Body.CHARON, Kind.BIOLOGY), "GEOLOGY_CHARON"),
    //Survie spatiale (bêta) : combinaisons et support de vie
    SPACE_SUIT(0, 2, 3, 20, 6, () -> suit(1), costs("O", 200), "TELEMETRY"),
    LIFE_SUPPORT(0, 2, 4, 30, 8, () -> machine("basic_oxygen_diffuser"), costs("O", 300, "L", 100), "SPACE_SUIT"),
    THERMAL_SUIT(0, 5, 3, 40, 10, () -> suit(2), costs("L", 800, "S", 300), "SPACE_SUIT", "GEOLOGY_MOON"),
    ACID_SUIT(1, 11, 2, 60, 12, () -> suit(3), costs("M", 900, "S", 400, "L", 300), "THERMAL_SUIT", "GEOLOGY_MARS|GEOLOGY_PHOBOS|GEOLOGY_DEIMOS"),
    ORBITAL_STATIONS(1, 7, 2, 50, 12, () -> new ItemStack(PiItems.STATION_KIT.get()), costs("M", 400, "L", 400, "O", 400), "BLUEPRINT_T2", "LIFE_SUPPORT"),
    //Simulation planetaire (fin de partie)
    PLANET_SYNTHESIS(9, 46, 3, 200, 30, () -> machine("synthesizer"), costs("P", 2000, "N", 1500, "U", 1500, "Sa", 1500, "J", 1500, "H", 2000),
          "SINGULARITY_THEORY"),
    SOLAR_SIMULATION(9, 47, 2, 300, 40, () -> machine("space_simulator_controller"), costs("P", 3000, "N", 2000, "U", 2000, "Sa", 2000, "J", 2000, "H", 3000,
          "Me", 2000, "V", 2000, "M", 2000, "L", 2000, "O", 2000, "S", 3000), "PLANET_SYNTHESIS"),
    //Ere X : les geantes gazeuses (descente dans les nuages, aerostats, recolte de gaz) et l'absorbeur solaire
    ATMOSPHERIC_ENTRY(10, 48, 2, 160, 30, () -> new ItemStack(Items.PHANTOM_MEMBRANE), costs("J", 2500, "Sa", 2000, "H", 1500, "S", 2000),
          "BLUEPRINT_T10", "AEROCAPTURE"),
    BLUEPRINT_T11(10, 49, 1, 180, 30, () -> rocket(RocketTier.TIER_11), costs("J", 3000, "Sa", 2000, "N", 1500, "P", 1000), "ATMOSPHERIC_ENTRY"),
    ATMOSPHERIC_SUIT(10, 49, 3, 120, 25, () -> suit(4), costs("J", 1500, "Sa", 1500, "U", 800), "ATMOSPHERIC_ENTRY", "ACID_SUIT"),
    AEROSTATS(10, 50, 2, 140, 28, () -> machine("aerostat_kit"), costs("J", 2000, "Sa", 1000, "U", 1000), "BLUEPRINT_T11"),
    GAS_HARVESTING(10, 51, 2, 160, 30, () -> machine("drone_mk1"), costs("J", 2500, "Sa", 2500, "U", 1500, "N", 1500), "AEROSTATS"),
    STORM_DRONES(10, 52, 1, 180, 30, () -> machine("drone_armor_plating"), costs("J", 2500, "Sa", 2500, "U", 2500, "N", 2500), "GAS_HARVESTING",
          "RADIATION_HARDENING"),
    SOLAR_ABSORPTION(10, 52, 3, 220, 35, () -> machine("solar_absorber_collector"), costs("H", 5000, "Me", 2500, "V", 2000, "S", 2000),
          "SINGULARITY_THEORY", "ORBITAL_STATIONS", "HELIOPHYSICS"),
    //Ere XI : Alpha du Centaure
    INTERSTELLAR_PROPULSION(11, 54, 2, 240, 40, () -> new ItemStack(Items.END_CRYSTAL), costs("N", 3000, "P", 3000, "H", 3000, "S", 4000),
          "BLUEPRINT_T11", "FUSION_DRIVES", "SINGULARITY_THEORY"),
    BLUEPRINT_T12(11, 55, 1, 260, 40, () -> rocket(RocketTier.TIER_12), costs("P", 4000, "N", 3000, "H", 3000), "INTERSTELLAR_PROPULSION"),
    CENTAURI_SURVEY(11, 55, 3, 200, 35, () -> new ItemStack(Items.SPYGLASS), costs("S", 5000, "P", 2000, "H", 2000), "INTERSTELLAR_PROPULSION",
          "DEEP_TELESCOPE"),
    ORBIT_TOLIMAN_B(11, 56, 1, 120, 30, () -> bodyIcon(Body.TOLIMAN_B, Kind.LAB), costs("AC", 1500, "P", 500), "CENTAURI_SURVEY"),
    SURFACE_TOLIMAN_B(Kind.SURFACE, Body.TOLIMAN_B, 11, 56, 2, 4, () -> bodyIcon(Body.TOLIMAN_B, Kind.SURFACE), "ORBIT_TOLIMAN_B"),
    GEOLOGY_TOLIMAN_B(Kind.GEOLOGY, Body.TOLIMAN_B, 11, 56, 3, 768, () -> bodyIcon(Body.TOLIMAN_B, Kind.GEOLOGY), "SURFACE_TOLIMAN_B"),
    BIOLOGY_TOLIMAN_B(Kind.BIOLOGY, Body.TOLIMAN_B, 11, 56, 4, 3072, () -> bodyIcon(Body.TOLIMAN_B, Kind.BIOLOGY), "GEOLOGY_TOLIMAN_B"),
    ORBIT_PROXIMA_B(11, 57, 1, 120, 30, () -> bodyIcon(Body.PROXIMA_B, Kind.LAB), costs("AC", 1500, "S", 800), "CENTAURI_SURVEY"),
    SURFACE_PROXIMA_B(Kind.SURFACE, Body.PROXIMA_B, 11, 57, 2, 4, () -> bodyIcon(Body.PROXIMA_B, Kind.SURFACE), "ORBIT_PROXIMA_B"),
    GEOLOGY_PROXIMA_B(Kind.GEOLOGY, Body.PROXIMA_B, 11, 57, 3, 768, () -> bodyIcon(Body.PROXIMA_B, Kind.GEOLOGY), "SURFACE_PROXIMA_B"),
    BIOLOGY_PROXIMA_B(Kind.BIOLOGY, Body.PROXIMA_B, 11, 57, 4, 3072, () -> bodyIcon(Body.PROXIMA_B, Kind.BIOLOGY), "GEOLOGY_PROXIMA_B"),
    ORBIT_PROXIMA_D(11, 58, 1, 100, 30, () -> bodyIcon(Body.PROXIMA_D, Kind.LAB), costs("AC", 1200, "H", 800), "CENTAURI_SURVEY"),
    SURFACE_PROXIMA_D(Kind.SURFACE, Body.PROXIMA_D, 11, 58, 2, 3, () -> bodyIcon(Body.PROXIMA_D, Kind.SURFACE), "ORBIT_PROXIMA_D"),
    GEOLOGY_PROXIMA_D(Kind.GEOLOGY, Body.PROXIMA_D, 11, 58, 3, 640, () -> bodyIcon(Body.PROXIMA_D, Kind.GEOLOGY), "SURFACE_PROXIMA_D"),
    BIOLOGY_PROXIMA_D(Kind.BIOLOGY, Body.PROXIMA_D, 11, 58, 4, 2560, () -> bodyIcon(Body.PROXIMA_D, Kind.BIOLOGY), "GEOLOGY_PROXIMA_D"),
    FLARE_SHIELDING(11, 59, 2, 200, 35, () -> new ItemStack(Items.SHIELD), costs("AC", 2500, "H", 2500), "ORBIT_PROXIMA_B"),
    //Ere XII : TRAPPIST-1
    VOID_DRIVE(12, 61, 2, 300, 45, () -> machine("void_singularity_cell"), costs("AC", 4000, "P", 3000, "S", 5000), "BLUEPRINT_T12",
          "BIOLOGY_TOLIMAN_B|BIOLOGY_PROXIMA_B|BIOLOGY_PROXIMA_D"),
    BLUEPRINT_T13(12, 62, 1, 320, 45, () -> rocket(RocketTier.TIER_13), costs("AC", 5000, "P", 4000), "VOID_DRIVE"),
    TRAPPIST_SURVEY(12, 62, 3, 260, 40, () -> new ItemStack(Items.SPYGLASS), costs("AC", 3000, "S", 6000), "VOID_DRIVE"),
    ORBIT_TRAPPIST_1B(12, 63, 1, 140, 32, () -> bodyIcon(Body.TRAPPIST_1B, Kind.LAB), costs("T", 1500, "AC", 800), "TRAPPIST_SURVEY"),
    SURFACE_TRAPPIST_1B(Kind.SURFACE, Body.TRAPPIST_1B, 12, 63, 2, 3, () -> bodyIcon(Body.TRAPPIST_1B, Kind.SURFACE), "ORBIT_TRAPPIST_1B"),
    GEOLOGY_TRAPPIST_1B(Kind.GEOLOGY, Body.TRAPPIST_1B, 12, 63, 3, 896, () -> bodyIcon(Body.TRAPPIST_1B, Kind.GEOLOGY), "SURFACE_TRAPPIST_1B"),
    BIOLOGY_TRAPPIST_1B(Kind.BIOLOGY, Body.TRAPPIST_1B, 12, 63, 4, 3584, () -> bodyIcon(Body.TRAPPIST_1B, Kind.BIOLOGY), "GEOLOGY_TRAPPIST_1B"),
    ORBIT_TRAPPIST_1D(12, 64, 1, 140, 32, () -> bodyIcon(Body.TRAPPIST_1D, Kind.LAB), costs("T", 1500, "AC", 800), "TRAPPIST_SURVEY"),
    SURFACE_TRAPPIST_1D(Kind.SURFACE, Body.TRAPPIST_1D, 12, 64, 2, 3, () -> bodyIcon(Body.TRAPPIST_1D, Kind.SURFACE), "ORBIT_TRAPPIST_1D"),
    GEOLOGY_TRAPPIST_1D(Kind.GEOLOGY, Body.TRAPPIST_1D, 12, 64, 3, 896, () -> bodyIcon(Body.TRAPPIST_1D, Kind.GEOLOGY), "SURFACE_TRAPPIST_1D"),
    BIOLOGY_TRAPPIST_1D(Kind.BIOLOGY, Body.TRAPPIST_1D, 12, 64, 4, 3584, () -> bodyIcon(Body.TRAPPIST_1D, Kind.BIOLOGY), "GEOLOGY_TRAPPIST_1D"),
    ORBIT_TRAPPIST_1E(12, 65, 1, 140, 32, () -> bodyIcon(Body.TRAPPIST_1E, Kind.LAB), costs("T", 1500, "AC", 800), "TRAPPIST_SURVEY"),
    SURFACE_TRAPPIST_1E(Kind.SURFACE, Body.TRAPPIST_1E, 12, 65, 2, 4, () -> bodyIcon(Body.TRAPPIST_1E, Kind.SURFACE), "ORBIT_TRAPPIST_1E"),
    GEOLOGY_TRAPPIST_1E(Kind.GEOLOGY, Body.TRAPPIST_1E, 12, 65, 3, 896, () -> bodyIcon(Body.TRAPPIST_1E, Kind.GEOLOGY), "SURFACE_TRAPPIST_1E"),
    BIOLOGY_TRAPPIST_1E(Kind.BIOLOGY, Body.TRAPPIST_1E, 12, 65, 4, 3584, () -> bodyIcon(Body.TRAPPIST_1E, Kind.BIOLOGY), "GEOLOGY_TRAPPIST_1E"),
    ORBIT_TRAPPIST_1F(12, 66, 1, 140, 32, () -> bodyIcon(Body.TRAPPIST_1F, Kind.LAB), costs("T", 1500, "AC", 800), "TRAPPIST_SURVEY"),
    SURFACE_TRAPPIST_1F(Kind.SURFACE, Body.TRAPPIST_1F, 12, 66, 2, 4, () -> bodyIcon(Body.TRAPPIST_1F, Kind.SURFACE), "ORBIT_TRAPPIST_1F"),
    GEOLOGY_TRAPPIST_1F(Kind.GEOLOGY, Body.TRAPPIST_1F, 12, 66, 3, 896, () -> bodyIcon(Body.TRAPPIST_1F, Kind.GEOLOGY), "SURFACE_TRAPPIST_1F"),
    BIOLOGY_TRAPPIST_1F(Kind.BIOLOGY, Body.TRAPPIST_1F, 12, 66, 4, 3584, () -> bodyIcon(Body.TRAPPIST_1F, Kind.BIOLOGY), "GEOLOGY_TRAPPIST_1F"),
    //Ere XIII : le cosmos
    GALACTIC_CARTOGRAPHY(13, 68, 2, 400, 50, () -> new ItemStack(Items.RECOVERY_COMPASS), costs("T", 5000, "AC", 5000, "S", 8000),
          "BIOLOGY_TRAPPIST_1B|BIOLOGY_TRAPPIST_1D|BIOLOGY_TRAPPIST_1E|BIOLOGY_TRAPPIST_1F"),
    UNIVERSE_SIMULATION(13, 69, 2, 600, 60, () -> machine("space_rift_projector"), costs("T", 8000, "AC", 8000, "S", 10000, "H", 6000, "P", 4000,
          "N", 4000, "U", 4000, "Sa", 4000, "J", 4000), "GALACTIC_CARTOGRAPHY", "SOLAR_SIMULATION");

    public static final int ERAS = 14;

    /** Nature d'une recherche : laboratoire, ou echantillons apportes par le joueur. */
    public enum Kind {
        LAB, SURFACE, GEOLOGY, BIOLOGY, ASTEROIDS;

        public boolean isSample() {
            return this != LAB;
        }

        public String getTranslationKey() {
            return "research.pioneers.kind." + name().toLowerCase();
        }
    }

    public final Kind kind;
    /** Astre concerne (orbite, surface, geologie, biologie), null sinon. */
    @Nullable
    private final Body body;
    /** Ere, et position dans l'arbre (colonne, ligne). */
    public final int era, col, row;
    /** Unites de laboratoire, ou echantillons a fournir (de chaque type pour les asteroides). */
    public final int units;
    /** Duree d'une unite (secondes, pour un laboratoire sans bonus) ; 0 pour les echantillons. */
    public final int unitSeconds;
    private final Supplier<ItemStack> icon;
    private final Map<ProbeDestination, Long> unitCost;
    private final String[] prerequisiteNames;
    private List<List<ResearchNode>> prerequisiteGroups;
    private List<ResearchNode> prerequisites;

    ResearchNode(int era, int col, int row, int units, int unitSeconds, Supplier<ItemStack> icon, Map<ProbeDestination, Long> unitCost, String... prerequisites) {
        this.kind = Kind.LAB;
        this.body = null;
        this.era = era;
        this.col = col;
        this.row = row;
        this.units = units;
        this.unitSeconds = unitSeconds;
        this.icon = icon;
        this.unitCost = unitCost;
        this.prerequisiteNames = prerequisites;
    }

    ResearchNode(Kind kind, @Nullable Body body, int era, int col, int row, int count, Supplier<ItemStack> icon, String... prerequisites) {
        this.kind = kind;
        this.body = body;
        this.era = era;
        this.col = col;
        this.row = row;
        this.units = count;
        this.unitSeconds = 0;
        this.icon = icon;
        this.unitCost = Collections.emptyMap();
        this.prerequisiteNames = prerequisites;
    }

    private static ItemStack rocket(RocketTier tier) {
        return new ItemStack(PiItems.ROCKETS.get(tier).get());
    }

    private static ItemStack probe(int tier) {
        return new ItemStack(PiItems.module(RocketModule.PROBE), tier);
    }

    private static ItemStack meteorite(String type) {
        return new ItemStack(PiWorldBlocks.METEORITES.get(type + "_meteorite"));
    }

    /** Plastron de la combinaison d'un tier (icone). */
    private static ItemStack suit(int tier) {
        return new ItemStack(PiItems.suit(tier, EquipmentSlot.CHEST));
    }

    /** Machine du mod par son identifiant (icone). */
    private static ItemStack machine(String id) {
        var item = ForgeRegistries.ITEMS.getValue(Pioneers.rl(id));
        return new ItemStack(item == null ? Items.BARRIER : item);
    }

    private static ItemStack bodyIcon(Body body, Kind kind) {
        return BodyResearch.icon(body, kind);
    }

    /**
     * Couts par unite. Codes : O orbitales, L lunaires, S siderales, H solaires, Me mercuriennes, V venusiennes, M martiennes,
     * J joviennes, Sa saturniennes, U uraniennes, N neptuniennes, P plutoniennes, AC centauriennes, T de TRAPPIST-1.
     */
    private static Map<ProbeDestination, Long> costs(Object... entries) {
        Map<ProbeDestination, Long> map = new EnumMap<>(ProbeDestination.class);
        for (int i = 0; i < entries.length; i += 2) {
            ProbeDestination type = switch ((String) entries[i]) {
                case "O" -> ProbeDestination.EARTH_ORBIT;
                case "L" -> ProbeDestination.MOON;
                case "S" -> ProbeDestination.DEEP_SPACE;
                case "H" -> ProbeDestination.SUN;
                case "Me" -> ProbeDestination.MERCURY;
                case "V" -> ProbeDestination.VENUS;
                case "M" -> ProbeDestination.MARS;
                case "J" -> ProbeDestination.JUPITER;
                case "Sa" -> ProbeDestination.SATURN;
                case "U" -> ProbeDestination.URANUS;
                case "N" -> ProbeDestination.NEPTUNE;
                case "P" -> ProbeDestination.PLUTO;
                case "AC" -> ProbeDestination.CENTAURI;
                case "T" -> ProbeDestination.TRAPPIST;
                default -> throw new IllegalArgumentException((String) entries[i]);
            };
            map.put(type, ((Integer) entries[i + 1]).longValue());
        }
        return map;
    }

    public ItemStack getIcon() {
        return icon.get();
    }

    public Map<ProbeDestination, Long> getUnitCost() {
        return unitCost;
    }

    public boolean isLab() {
        return kind == Kind.LAB;
    }

    /** Astre etudie (y compris pour les recherches d'orbite), null sinon. */
    @Nullable
    public Body getBody() {
        if (body != null) {
            return body;
        }
        return name().startsWith("ORBIT_") ? Body.valueOf(name().substring(6)) : null;
    }

    /** Recherche d'orbite d'un astre (laboratoire). */
    public boolean isOrbit() {
        return name().startsWith("ORBIT_");
    }

    /** Groupes de prerequis : il faut un element de chaque groupe. */
    public List<List<ResearchNode>> getPrerequisiteGroups() {
        if (prerequisiteGroups == null) {
            List<List<ResearchNode>> groups = new ArrayList<>();
            for (String name : prerequisiteNames) {
                List<ResearchNode> group = new ArrayList<>();
                for (String alternative : name.split("\\|")) {
                    group.add(valueOf(alternative));
                }
                groups.add(Collections.unmodifiableList(group));
            }
            prerequisiteGroups = Collections.unmodifiableList(groups);
        }
        return prerequisiteGroups;
    }

    /** Tous les prerequis cites (pour tracer les liens de l'arbre). */
    public List<ResearchNode> getPrerequisites() {
        if (prerequisites == null) {
            List<ResearchNode> list = new ArrayList<>();
            for (List<ResearchNode> group : getPrerequisiteGroups()) {
                list.addAll(group);
            }
            prerequisites = Collections.unmodifiableList(list);
        }
        return prerequisites;
    }

    /** Prerequis satisfaits selon {@code done} (un element de chaque groupe). */
    public boolean prerequisitesMet(Predicate<ResearchNode> done) {
        for (List<ResearchNode> group : getPrerequisiteGroups()) {
            if (group.stream().noneMatch(done)) {
                return false;
            }
        }
        return true;
    }

    /** Nombre de types d'echantillons (4 pour les asteroides : roche, metal, glace, volcanique). */
    public int sampleTypes() {
        return kind == Kind.ASTEROIDS ? 4 : 1;
    }

    public int unitTicks() {
        return unitSeconds * 20;
    }

    public String getTranslationKey() {
        return "research.pioneers.node." + name().toLowerCase();
    }

    public String getDescriptionKey() {
        return getTranslationKey() + ".desc";
    }

    public static String getEraKey(int era) {
        return "research.pioneers.era." + era;
    }

    /** Tier de fusee dont cette recherche donne le plan (null sinon). */
    @Nullable
    public RocketTier getBlueprintTier() {
        return switch (this) {
            case BLUEPRINT_T2 -> RocketTier.TIER_2;
            case BLUEPRINT_T3 -> RocketTier.TIER_3;
            case BLUEPRINT_T4 -> RocketTier.TIER_4;
            case BLUEPRINT_T5 -> RocketTier.TIER_5;
            case BLUEPRINT_T6 -> RocketTier.TIER_6;
            case BLUEPRINT_T7 -> RocketTier.TIER_7;
            case BLUEPRINT_T8 -> RocketTier.TIER_8;
            case BLUEPRINT_T9 -> RocketTier.TIER_9;
            case BLUEPRINT_T10 -> RocketTier.TIER_10;
            case BLUEPRINT_T11 -> RocketTier.TIER_11;
            case BLUEPRINT_T12 -> RocketTier.TIER_12;
            case BLUEPRINT_T13 -> RocketTier.TIER_13;
            default -> null;
        };
    }

    /** Plan imprimable une fois la recherche terminee (fusee ou modules logistiques). */
    @Nullable
    public ItemStack getBlueprint() {
        if (this == CARGO_LOGISTICS) {
            return new ItemStack(PiItems.BLUEPRINT_CARGO.get());
        }
        RocketTier tier = getBlueprintTier();
        return tier == null ? null : new ItemStack(PiItems.BLUEPRINTS.get(tier).get());
    }

    /** Recherche qui fait decouvrir de nouveaux mondes (annonce speciale). */
    public boolean isDiscovery() {
        return switch (this) {
            case MARS_CARTOGRAPHY, INNER_SYSTEM, BELT_SURVEY, JOVIAN_SYSTEM, SATURN_SYSTEM, URANUS_SYSTEM, NEPTUNE_SYSTEM, DEEP_TELESCOPE,
                 CENTAURI_SURVEY, TRAPPIST_SURVEY, ATMOSPHERIC_ENTRY -> true;
            default -> false;
        };
    }

    public static ResearchNode byIndex(int index) {
        ResearchNode[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
