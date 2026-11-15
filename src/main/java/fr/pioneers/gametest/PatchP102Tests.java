package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.GeneratedFish;
import fr.pioneers.farm.fish.PiBobber;
import fr.pioneers.tech.forge.SmithingGrid;
import fr.pioneers.tech.forge.smithing.SmithingMoves;
import fr.pioneers.tech.kinetics.parts.GaugeBlock;
import fr.pioneers.tech.kinetics.parts.GovernorBlock;
import fr.pioneers.tech.kinetics.parts.GovernorUnit;
import fr.pioneers.tech.kinetics.parts.InlineBlock;
import fr.pioneers.tech.kinetics.parts.TransmissionUnit;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiForge;
import fr.pioneers.core.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.2-beta, lot 1 (GameTests) : durabilites (moules 128, creusets 256, outils x2), elevage (un animal affame ne perd plus de vie, sa
 * production reste au plancher), curseurs de la boite de vitesses et du regulateur a boules (reglage borne, relecture, ancienne cle), zone
 * verte de la peche (plus etroite pour un poisson plus difficile), enclume a 8 lingots, cadran des instruments tourne vers le joueur.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP102Tests {

    private PatchP102Tests() {
    }

    private static int max(Item item) {
        return new ItemStack(item).getMaxDamage();
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(Pioneers.rl(id));
        Gt.check(it != null, "objet pioneers:" + id + " absent");
        return it;
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, BlockPos rel, Block block, Class<T> type) {
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName());
        return type.cast(be);
    }

    // ================================================================== durabilites
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_durability", timeoutTicks = 20)
    public static void p102_durabilites(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(128, max(PiBronze.MOLDS.get("ingot").get()), "moule a lingot : 128 coulees");
        Gt.equal(PiBronze.MOLD_USES, max(PiBronze.MOLDS.get("gear").get()), "moule a engrenage : 128 coulees");
        Gt.equal(256, max(PiBronze.CRUCIBLE.get()), "creuset : 256 utilisations");
        Gt.equal(256, max(PiForge.CLAY_CRUCIBLE.getBlock().asItem()), "creuset en argile : 256 fusions");
        Gt.equal(32, max(PiForge.FIRE_STARTER.get()), "allume-feu : 32 (x2)");
        Gt.equal(128, max(PiForge.CRUCIBLE_TONGS.get()), "pince a creuset : 128 (x2)");
        Gt.equal(192, max(PiFishing.BAMBOO_ROD.get()), "canne en bambou : 192 (x2)");
        Gt.equal(512, max(PiFishing.BRONZE_ROD.get()), "canne en bronze : 512 (x2)");
        Gt.equal(1280, max(PiFishing.STEEL_ROD.get()), "canne en acier : 1 280 (x2)");
        Gt.equal(1200, max(item("iron_pickaxe")), "pioche en fer : 1 200 (600 x2)");
        Gt.equal(512, max(item("iron_hammer")), "marteau d'atelier en fer : 512 (256 x2)");
        s.done();
    }

    // ================================================================== elevage
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_husbandry", timeoutTicks = 120)
    public static void p102_elevage_ne_meurt_plus(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(0.1F, AnimalData.production(0), "jauge vide : production au plancher (10 %)");
        Gt.check(Math.abs(AnimalData.production(25) - 0.55F) < 1e-4, "jauge a 25 : production proportionnelle (55 %)");
        Gt.check(Math.abs(AnimalData.production(80) - 1F) < 1e-4, "jauge au-dessus de 50 : production pleine");
        //1.0.3-beta lot 8 : une vache vanilla devient sa race au tick suivant : on fait naitre directement une race
        Cow cow = (Cow) h.spawn(fr.pioneers.farm.animal.breed.PiBreeds.type(fr.pioneers.farm.animal.AnimalSpecies.COW, 0), new BlockPos(4, 2, 4));
        h.runAfterDelay(5, () -> {
            AnimalData d = PiAnimals.get(cow);
            Gt.check(d != null && d.init, "vache sans donnees d'elevage");
            d.domestic = true;
            d.hunger = 0;
            d.thirst = 0;
            //Avant la 1.0.2-beta : 2 points de vie perdus a chaque multiple de 30 s au-dela de 240 s de jeune
            d.starving = 299;
            cow.setHealth(cow.getMaxHealth());
            h.runAfterDelay(70, () -> {
                Gt.equal(cow.getMaxHealth(), cow.getHealth(), "vache affamee : aucun point de vie perdu");
                Gt.check(cow.isAlive(), "vache affamee : vivante");
                Gt.check(d.starving > 299, "vache affamee : elle deperit toujours (fiche « Deperit »)");
                Gt.equal(AnimalData.MIN_PRODUCTION, d.production(), "vache affamee : production au plancher");
                s.done();
            });
        });
    }

    // ================================================================== curseurs
    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_sliders", timeoutTicks = 20)
    public static void p102_curseurs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos g = new BlockPos(1, 2, 1);
        InlineBlock.Tile box = place(h, g, PiKinetics.GEARSHIFT.getBlock(), InlineBlock.Tile.class);
        Gt.check(box.unit() instanceof TransmissionUnit, "boite de vitesses : logique portee par TransmissionUnit");
        box.handleSetting(null, TransmissionUnit.SETTING_RATIO, 4);
        Gt.equal(2, (int) h.getBlockState(g).getValue(InlineBlock.RATIO), "curseur en position 4 : rapport x4 (indice 2)");
        box.handleSetting(null, TransmissionUnit.SETTING_RATIO, -7);
        Gt.equal(4, (int) h.getBlockState(g).getValue(InlineBlock.RATIO), "valeur du client bornee : position 0 = x1/4 (indice 4)");
        box.handleSetting(null, TransmissionUnit.SETTING_RATIO, 99);
        Gt.equal(2, (int) h.getBlockState(g).getValue(InlineBlock.RATIO), "valeur du client bornee : position 4 = x4");
        Gt.check(box.uiValues().index(UiValues.RATIO) >= 0, "valeur nommee du curseur declaree");
        Gt.equal(4L, box.guiValue(box.uiValues().index(UiValues.RATIO)), "valeur nommee : position 4");
        BlockPos c = new BlockPos(3, 2, 1);
        InlineBlock.Tile clutch = place(h, c, PiKinetics.CLUTCH.getBlock(), InlineBlock.Tile.class);
        clutch.handleSetting(null, TransmissionUnit.SETTING_RATIO, 4);
        Gt.equal(0, (int) h.getBlockState(c).getValue(InlineBlock.RATIO), "embrayage : pas de rapport a regler");
        GovernorBlock.Tile gov = place(h, new BlockPos(5, 2, 1), PiKinetics.GOVERNOR.getBlock(), GovernorBlock.Tile.class);
        Gt.equal(GovernorUnit.DEFAULT_RPM, gov.targetSpeed(), "regulateur neuf : 30 tr/min");
        gov.handleSetting(null, GovernorUnit.SETTING_TARGET, 77);
        Gt.equal(77, gov.targetSpeed(), "curseur du regulateur : 77 tr/min");
        gov.handleSetting(null, GovernorUnit.SETTING_TARGET, 5000);
        Gt.equal(GovernorUnit.MAX_RPM, gov.targetSpeed(), "valeur du client bornee : 120 tr/min");
        CompoundTag tag = gov.saveWithoutMetadata();
        Gt.check(tag.contains("rpm"), "cle de sauvegarde rpm");
        GovernorBlock.Tile gov2 = place(h, new BlockPos(7, 2, 1), PiKinetics.GOVERNOR.getBlock(), GovernorBlock.Tile.class);
        gov2.load(tag.copy());
        Gt.equal(GovernorUnit.MAX_RPM, gov2.targetSpeed(), "vitesse visee relue");
        CompoundTag old = new CompoundTag();
        old.putInt("target", 5);
        gov2.load(old);
        Gt.equal(GovernorBlock.TARGETS[5], gov2.targetSpeed(), "regulateur d'avant (cran 5) relu : 60 tr/min");
        s.done();
    }

    // ================================================================== peche, enclume, instruments
    private static FishDef fish(String id) {
        for (FishDef d : GeneratedFish.SPECIES) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        throw new AssertionError("poisson " + id + " absent");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p102_misc", timeoutTicks = 20)
    public static void p102_peche_enclume_cadran(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        float[] easy = PiBobber.greenZone(fish("sardine"), 0.9F, 1), mid = PiBobber.greenZone(fish("pike"), 1.0F, 1),
              hard = PiBobber.greenZone(fish("oarfish"), 1.2F, 1);
        float we = easy[1] - easy[0], wm = mid[1] - mid[0], wh = hard[1] - hard[0];
        Gt.check(we > wm && wm > wh, "zone verte plus etroite pour un poisson plus difficile : " + we + " > " + wm + " > " + wh);
        float[] big = PiBobber.greenZone(fish("pike"), 1.2F, 1);
        Gt.check(big[1] - big[0] < wm, "un plus gros individu : zone plus etroite");
        float[] steel = PiBobber.greenZone(fish("pike"), 1.0F, 2);
        Gt.check(steel[1] - steel[0] > wm, "une meilleure canne elargit la zone");
        Gt.check(SmithingMoves.MAX_INGOTS >= 5, "l'enclume accepte au moins 5 lingots (marteau de minage)");
        Gt.equal(160, SmithingGrid.initial(5).count(), "5 lingots sur l'enclume : 160 pixels");
        Gt.equal(SmithingMoves.MAX_INGOTS * 32, SmithingGrid.initial(SmithingMoves.MAX_INGOTS).count(), "capacite pleine de l'enclume");
        Gt.equal(Direction.SOUTH, GaugeBlock.dialToward(Direction.Axis.Y, new Vec3(0.5, 0.5, 0.5), new Vec3(0.6, 1.6, 6)),
              "arbre vertical, joueur au sud : cadran au sud");
        Gt.equal(Direction.UP, GaugeBlock.dialToward(Direction.Axis.X, new Vec3(0.5, 0.5, 0.5), new Vec3(1.5, 6, 0.8)),
              "arbre est-ouest, joueur au-dessus : cadran en haut");
        s.done();
    }
}
