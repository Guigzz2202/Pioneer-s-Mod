package fr.pioneers.gametest;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.tech.bronze.machines.SteamBlowerTile;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.tech.industry.atelier.DynamoTile;
import fr.pioneers.tech.industry.bronze.CokeOvenTile;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.kinetics.machines.MechanicalMachineTile;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiKinetics;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * API Machine (lot 7, GameTests) : machines de l'age mecanique et du bronze sur leurs unites (adaptateur cinetique {@code KineticMachineTile},
 * tiles du bronze qui portent une unite) : aller-retour de sauvegarde au format d'avant (memes cles, cases relues dans le meme ordre),
 * valeurs NOMMEES aux indices d'avant, interface v2 (zone de {@link StandardLayout}).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineApi7Tests {

    private MachineApi7Tests() {
    }

    private static <T extends BlockEntity> T place(GameTestHelper h, BlockPos rel, Block block, Class<T> type) {
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName());
        return type.cast(be);
    }

    /** Sauve {@code a}, relit dans {@code b} (meme bloc) : cles attendues presentes, sauvegarde identique apres relecture. */
    private static CompoundTag roundTrip(BlockEntity a, BlockEntity b, String... keys) {
        CompoundTag tag = a.saveWithoutMetadata();
        for (String k : keys) {
            Gt.check(tag.contains(k), "cle « " + k + " » manquante (format d'avant le lot 7)");
        }
        b.load(tag.copy());
        Gt.equal(tag, b.saveWithoutMetadata(), "sauvegarde identique apres relecture");
        return tag;
    }

    // ================================================================== machines mecaniques
    @GameTest(template = Gt.SMALL, batch = "pioneers_api7_mechanical", timeoutTicks = 40)
    public static void api7_mecanique_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block sifter = PiKinetics.MACHINES.get(MachineType.SIFTER).getBlock();
        MechanicalMachineTile a = place(h, new BlockPos(2, 2, 2), sifter, MechanicalMachineTile.class);
        a.inputs().get(0).setStack(new ItemStack(Items.GRAVEL, 12));
        a.guiSlots().get(a.guiSlots().size() - 1).setStack(new ItemStack(Items.FLINT, 3));
        MechanicalMachineTile b = place(h, new BlockPos(5, 2, 2), sifter, MechanicalMachineTile.class);
        roundTrip(a, b, "slots", "progress", "water", "placedWater");
        Gt.equal(12, b.inputs().get(0).getStack().getCount(), "entree relue");
        Gt.check(b.guiSlots().get(b.guiSlots().size() - 1).getStack().is(Items.FLINT), "derniere sortie relue (ordre des cases)");
        //Ordre des cases : entree, maille, sorties ; valeurs nommees aux indices d'avant (8)
        Gt.equal(1 + 1 + MachineType.SIFTER.outputs, b.guiSlots().size(), "cases du tamis");
        UiValues ui = b.uiValues();
        Gt.equal(8, ui.size(), "valeurs d'interface");
        Gt.equal(0, ui.index(UiValues.SPEED), "vitesse a l'indice 0");
        Gt.equal(3, ui.index(UiValues.STATUS), "etat a l'indice 3");
        Gt.equal(MachineType.SIFTER.ordinal(), b.guiValue(5), "sorte de machine a l'indice 5");
        Gt.equal(StandardLayout.ZONE, b.guiHeight(), "interface v2 : zone");
        Gt.equal(StandardLayout.WIDTH, b.guiWidth(), "interface v2 : largeur");
        //Charge declaree au reseau a vide : 5 % du couple de travail
        Gt.check(Math.abs(b.loadTorque() - MachineType.SIFTER.torque * 0.05) < 1e-6, "charge a vide : " + b.loadTorque());
        //Meule : selecteur (source mecanique), case Recette dans la zone
        MechanicalMachineTile m = place(h, new BlockPos(2, 2, 5), PiKinetics.MACHINES.get(MachineType.MILLSTONE).getBlock(), MechanicalMachineTile.class);
        Gt.check(!m.recipeSource().isEmpty(), "la meule n'a pas de selecteur");
        int[] r = m.recipeSlot();
        Gt.check(r[1] >= 16 && r[1] + 16 <= StandardLayout.ZONE, "case Recette hors de la zone");
        s.done();
    }

    // ================================================================== machines a vapeur, soufflerie
    @GameTest(template = Gt.SMALL, batch = "pioneers_api7_steam", timeoutTicks = 40)
    public static void api7_vapeur_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //1.0.4-beta (lot 11) : plus de fondeur d'alliages a vapeur : le melangeur (4 entrees) porte l'aller-retour (cases, bain, vapeur)
        Block smelter = PiBronze.STEAM_MACHINES.get(SteamMachine.MIXER).getBlock();
        SteamProcessTile a = place(h, new BlockPos(2, 2, 2), smelter, SteamProcessTile.class);
        a.inputs().get(1).setStack(new ItemStack(Items.RAW_COPPER, 5));
        a.steam(Direction.DOWN).add(300, 0);
        SteamProcessTile b = place(h, new BlockPos(5, 2, 2), smelter, SteamProcessTile.class);
        //1.0.5-beta (lot 38) : plus de bain (fondeur d'alliages retire au lot 11 de la 1.0.4) ; faces et format du lot 38
        roundTrip(a, b, "slots", "progress", "steam", "config", SteamProcessTile.FORMAT);
        Gt.equal(5, b.inputs().get(1).getStack().getCount(), "deuxieme entree relue");
        Gt.check(b.steam(Direction.DOWN).steam() > 299, "vapeur relue");
        UiValues ui = b.uiValues();
        Gt.equal(7, ui.size(), "valeurs d'interface (1.0.2-beta lot 3 : + contenance ; 1.0.5-beta lot 38 : + rendement, trop-plein)");
        Gt.equal(1, ui.index(UiValues.STEAM), "vapeur (pression avant le lot 3 du 1.0.2-beta) a l'indice 1");
        Gt.equal(2, ui.index(UiValues.STATUS), "etat a l'indice 2");
        Gt.equal(6, b.guiSlots().size(), "cases : 4 entrees, sortie, sous-produit");
        SteamBlowerTile w = place(h, new BlockPos(2, 2, 5), PiBronze.STEAM_BLOWER.getBlock(), SteamBlowerTile.class);
        SteamBlowerTile w2 = place(h, new BlockPos(5, 2, 5), PiBronze.STEAM_BLOWER.getBlock(), SteamBlowerTile.class);
        w.steam(Direction.DOWN).add(150, 0);
        roundTrip(w, w2, "steam");
        Gt.equal(3, w2.uiValues().size(), "valeurs de la soufflerie");
        Gt.equal(StandardLayout.ZONE, w2.guiHeight(), "interface v2 de la soufflerie");
        s.done();
    }

    // ================================================================== four a coke (1.0.2-beta : forge au charbon retiree)
    @GameTest(template = Gt.SMALL, batch = "pioneers_api7_fire", timeoutTicks = 40)
    public static void api7_feu_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //1.0.2-beta (lot 2) : la forge au charbon est retiree : le four a coke seul
        CokeOvenTile c = place(h, new BlockPos(2, 2, 5), PiIndustry.COKE_OVEN.getBlock(), CokeOvenTile.class);
        c.itemInputs().get(0).setStack(new ItemStack(Items.COAL, 9));
        CokeOvenTile d = place(h, new BlockPos(5, 2, 5), PiIndustry.COKE_OVEN.getBlock(), CokeOvenTile.class);
        roundTrip(c, d, "slots", "progress", "tar");
        Gt.equal(9, d.itemInputs().get(0).getStack().getCount(), "entree du four a coke relue");
        Gt.equal(4, d.uiValues().size(), "valeurs du four a coke");
        Gt.equal(3, d.uiValues().index(UiValues.FLUID), "goudron a l'indice 3");
        Gt.check(!d.recipeSource().isEmpty(), "le four a coke n'a plus de selecteur");
        s.done();
    }

    // ================================================================== dynamo (adaptateur cinetique)
    @GameTest(template = Gt.SMALL, batch = "pioneers_api7_dynamo", timeoutTicks = 40)
    public static void api7_dynamo_aller_retour(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Block block = PiIndustry.DYNAMOS.get(CableTier.ADVANCED).getBlock();
        DynamoTile a = place(h, new BlockPos(2, 2, 2), block, DynamoTile.class);
        a.generator().energy.insert(12_345, false);
        a.handleCustom(null, DynamoTile.NEXT);
        DynamoTile b = place(h, new BlockPos(5, 2, 2), block, DynamoTile.class);
        roundTrip(a, b, "energy", "excitation", "speed");
        Gt.equal(12_345L, b.guiValue(0), "reserve relue (indice 0)");
        Gt.equal(1L, b.guiValue(7), "excitation relue (indice 7)");
        Gt.equal(13, b.uiValues().size(), "valeurs de la dynamo");
        Gt.equal(DynamoTile.RATED_FE[1], b.guiValue(9), "puissance nominale du tier (indice 9)");
        Gt.check(b instanceof MachineGui && b.guiHeight() == StandardLayout.ZONE, "interface v2 de la dynamo");
        s.done();
    }
}
