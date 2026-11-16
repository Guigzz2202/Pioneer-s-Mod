package fr.pioneers.gametest;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.3-beta, lot 6 (GameTests) : retours de Guillaume du 05/11.
 * <ul>
 *     <li>seaux : aucun objet qui porte un fluide (seau de lave, seaux du mod, capsules, objets a capacite fluide) n'entre dans le
 *     generateur a combustion solide (filtre des cases : interface, Maj + clic, entonnoirs, cables, robots) ni n'y brule ; le charbon oui ;</li>
 *     <li>hauteur des interfaces v2 : glissiere au-dessus du cadre de l'inventaire, cases du joueur a leur place dans le cadre ;</li>
 *     <li>generateurs : temporisation d'extinction (plus de saccade marche / arret entre deux combustibles).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot6Tests {

    private PatchP103Lot6Tests() {
    }

    private static AtelierTile atelier(GameTestHelper h, BlockPos rel, IndustryMachine machine, CableTier tier) {
        BlockState st = Gt.compact(machine, tier).defaultBlockState();   // 1.0.6 : le four est une machine du kit (1.0.4-beta lot 11)
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier en " + rel);
        return (AtelierTile) h.getBlockEntity(rel);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_6_seaux", timeoutTicks = 40)
    public static void p103_6_seaux_hors_du_generateur_solide(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile solid = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.SOLID_COMBUSTION_GENERATOR, CableTier.BASIC);
        Gt.check(!solid.itemInputs().isEmpty(), "generateur solide : une case de combustible");
        ItemSlot fuelSlot = solid.itemInputs().get(0);
        ItemStack coal = new ItemStack(Items.COAL);
        Gt.check(IndustryRecipes.accepts(h.getLevel(), IndustryMachine.SOLID_COMBUSTION_GENERATOR, coal), "le charbon entre");
        Gt.check(fuelSlot.insert(coal.copy(), Action.SIMULATE, Automation.EXTERNAL).isEmpty(), "le charbon passe par l'entonnoir");
        ItemStack lava = new ItemStack(Items.LAVA_BUCKET);
        Gt.check(IndustryRecipes.holdsFluid(lava), "le seau de lave porte un fluide");
        Gt.check(!IndustryRecipes.accepts(h.getLevel(), IndustryMachine.SOLID_COMBUSTION_GENERATOR, lava), "le seau de lave est refuse");
        Gt.check(IndustryRecipes.fuel(lava) == null && GeneratorOutput.solidFuel(lava) == null, "le seau de lave ne brule pas (ni JEI)");
        for (Automation a : Automation.values()) {
            Gt.equal(1, fuelSlot.insert(lava.copy(), Action.SIMULATE, a).getCount(), "seau de lave refuse (" + a + ")");
        }
        //tous les objets du jeu qui portent un fluide (seaux du mod, capsules, reservoirs portatifs) : jamais dans le generateur solide
        int checked = 0;
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack st = new ItemStack(item);
            if (!IndustryRecipes.holdsFluid(st)) {
                continue;
            }
            checked++;
            Gt.check(!IndustryRecipes.accepts(h.getLevel(), IndustryMachine.SOLID_COMBUSTION_GENERATOR, st), "objet a fluide accepte : " + item);
            Gt.check(IndustryRecipes.fuel(st) == null, "objet a fluide brule : " + item);
        }
        Gt.check(checked > 10, "objets a fluide reconnus : " + checked);
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_6_interfaces", timeoutTicks = 40)
    public static void p103_6_hauteur_des_interfaces(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile furnace = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.FURNACE, CableTier.BASIC);
        Gt.equal(StandardLayout.INVENTORY, furnace.guiInventory(), "bande de l'inventaire v2");
        MachineMenu menu = (MachineMenu) furnace.standardMenu(0, new Inventory(h.makeMockPlayer()));
        int zone = furnace.guiHeight();
        StandardLayout.Rect frame = StandardLayout.inventoryFrame(menu.width(), zone);
        Gt.check(frame.y() >= zone + StandardLayout.SEPARATOR, "cadre de l'inventaire sous la glissiere");
        Slot first = menu.slots.get(menu.slots.size() - 36), hotbar = menu.slots.get(menu.slots.size() - 9);
        Gt.check(first.y > frame.y() + StandardLayout.INV_LABEL - StandardLayout.SEPARATOR && hotbar.y + 16 < frame.y() + frame.h(),
              "cases du joueur dans le cadre (" + first.y + ", " + hotbar.y + " ; cadre " + frame + ")");
        Gt.equal(menu.height() - 82, first.y, "premiere rangee du joueur a hauteur - 82");
        int[][] bands = StandardLayout.panelBands(zone, menu.height());
        Gt.equal(zone - 1, bands[2][2], "bas de l'alcove sur la derniere rangee de la zone");
        Gt.equal(frame.y() - 2, bands[2][3], "glissiere juste au-dessus du cadre");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_6_generateurs", timeoutTicks = 40)
    public static void p103_6_generateurs_sans_saccade(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (IndustryMachine m : new IndustryMachine[]{IndustryMachine.SOLID_COMBUSTION_GENERATOR, IndustryMachine.LIQUID_COMBUSTION_GENERATOR,
              IndustryMachine.GAS_TURBINE}) {
            AtelierTile gen = atelier(h, new BlockPos(2, 2, 2), m, CableTier.BASIC);
            Gt.equal(MachineTile.GENERATOR_HOLD, gen.activeHold(), m.key + " : temporisation d'extinction du generateur");
        }
        AtelierTile furnace = atelier(h, new BlockPos(2, 2, 2), IndustryMachine.FURNACE, CableTier.BASIC);
        Gt.equal(20, furnace.activeHold(), "machine a procede : extinction d'avant (1 s)");
        s.done();
    }
}
