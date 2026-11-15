package fr.pioneers.gametest;

import fr.pioneers.core.machine.MachineGui;
import fr.pioneers.core.machine.MachineMenu;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.animal.AnimalData;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.NeedGoal;
import fr.pioneers.farm.animal.PiAnimals;
import fr.pioneers.farm.animal.SiteChoice;
import fr.pioneers.farm.animal.breed.PiBreeds;
import fr.pioneers.farm.device.AutoFeederTile;
import fr.pioneers.farm.device.DeviceBlock;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.device.WeatherStationTile;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.farm.kitchen.StationBlock;
import fr.pioneers.farm.kitchen.StationTile;
import fr.pioneers.farm.kitchen.StationTiles;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 41 (retours de jeu de Guillaume, 5e serie, 6 : interfaces des machines de ferme ; 6e serie, retour 67 : animaux qui
 * mangent et boivent chez eux ; GameTests) :
 * <ul>
 *     <li>PLANCHE PAR SON INTERFACE (retour 60) : menu standard ({@link MachineMenu}), aliment et couteau poses par les cases du menu (une
 *     sortie refuse tout), bouton « Couper » = action {@code CUSTOM} du menu jusqu'au produit (couteau use) ; clic droit avec un aliment en
 *     main : rien n'entre (plus d'interaction directe), l'interface s'ouvre ; Maj + clic droit main vide : un coup ;</li>
 *     <li>PAIRES ET AUTOMATISATION (retour 60) : seau d'eau pose par un entonnoir dans la case « Vider » de l'abreuvoir (1 000 mB, seau vide
 *     rendu et repris par le dessous) ; seau vide pose dans la case « Remplir » du pressoir (seau plein, cuve videe de 1 000 mB) ; faces
 *     reglables (objets fermes par le haut : l'entonnoir n'entre plus) sauvees sous {@code farm_sides} ;</li>
 *     <li>APPAREILS ET MONDES EXISTANTS (retour 60) : distributeur automatique (seau d'eau par la case du menu), station meteo sans onglet
 *     des faces, ancienne marmite (7 cases) relue sans perte, nouvelles cases vides ;</li>
 *     <li>ENCLOS (retour 67) : deux enclos voisins separes par une cloture ; l'abreuvoir du voisin, plus proche, est ecarte par le vrai calcul
 *     de chemin ({@link SiteChoice}), la vache assoiffee boit dans le sien.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot41Tests {

    private PatchP105Lot41Tests() {
    }

    static <T> T station(GameTestHelper h, BlockPos rel, StationBlock.Kind kind, Class<T> type) {
        h.setBlock(rel, PiKitchen.STATIONS.get(kind).get().defaultBlockState());
        Object be = h.getBlockEntity(rel);
        Gt.check(type.isInstance(be), "pas de tuile " + type.getSimpleName() + " pour " + kind);
        return type.cast(be);
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pioneers", id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    static BlockHitResult hit(BlockPos abs) {
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    // ------------------------------------------------------------------ 1. planche a decouper par son interface

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_41_board", timeoutTicks = 20)
    public static void p105_41_planche_interface(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p41b", "Colette", new BlockPos(4, 1, 4));
        BlockPos rel = new BlockPos(5, 1, 5);
        StationTiles.CuttingBoard board = station(h, rel, StationBlock.Kind.CUTTING_BOARD, StationTiles.CuttingBoard.class);
        KitchenRecipe cut = null;
        for (KitchenRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(Station.CUTTING).get())) {
            if (r.ingredients().size() == 1 && r.ingredients().get(0).ingredient().getItems().length > 0 && r.strokes() > 0) {
                cut = r;
                break;
            }
        }
        Gt.check(cut != null, "aucune recette de decoupe");
        ItemStack food = r0(cut);
        //Clic droit avec l'aliment en main : rien n'entre, l'interface s'ouvre (plus d'interaction directe)
        p.setItemInHand(InteractionHand.MAIN_HAND, food.copy());
        BlockPos abs = h.absolutePos(rel);
        h.getBlockState(rel).use(s.level, p, InteractionHand.MAIN_HAND, hit(abs));
        Gt.check(board.items().get(StationTiles.CuttingBoard.IN).isEmpty(), "clic droit : l'aliment n'entre plus dans le poste");
        Gt.equal(food.getCount(), p.getMainHandItem().getCount(), "clic droit : l'aliment reste en main");
        Gt.check(p.containerMenu instanceof MachineMenu, "clic droit : l'interface s'ouvre");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        //Par les cases du menu (ordre de la disposition : couteau, entree, deux sorties)
        MachineMenu menu = (MachineMenu) board.standardMenu(0, p.getInventory());
        Gt.check(menu.stillValid(p), "menu valide a cote du poste");
        Gt.check(!menu.slots.get(2).mayPlace(food), "une sortie ne prend rien");
        Gt.check(!menu.slots.get(0).mayPlace(food), "la case du couteau ne prend pas l'aliment");
        Gt.check(menu.slots.get(1).safeInsert(food.copy()).isEmpty(), "aliment pose dans l'entree");
        Gt.check(menu.slots.get(0).safeInsert(new ItemStack(item("iron_knife"))).isEmpty(), "couteau pose dans sa case");
        Gt.check(board.buttonActive(0), "bouton « Couper » actif");
        int clicks = 0;
        while (board.items().get(StationTiles.CuttingBoard.OUT).isEmpty() && clicks < 40) {
            board.handleAction(p, MachineGui.CUSTOM);
            clicks++;
        }
        Gt.check(!board.items().get(StationTiles.CuttingBoard.OUT).isEmpty(), "produit dans la sortie apres " + clicks + " coups du bouton");
        Gt.check(board.items().get(StationTiles.CuttingBoard.KNIFE).getDamageValue() > 0, "le couteau s'use");
        //Maj + clic droit main vide : un coup (geste garde des postes manuels)
        Gt.check(menu.slots.get(1).safeInsert(food.copy()).isEmpty(), "second aliment");
        float before = board.arrowProgress();
        p.setShiftKeyDown(true);
        h.getBlockState(rel).use(s.level, p, InteractionHand.MAIN_HAND, hit(abs));
        p.setShiftKeyDown(false);
        Gt.check(board.arrowProgress() > before || !board.items().get(StationTiles.CuttingBoard.OUT).isEmpty() && board.items().get(StationTiles.CuttingBoard.IN).getCount()
              < food.getCount(), "Maj + clic droit main vide : un coup de couteau");
        p.getInventory().clearContent();
        s.done();
    }

    static ItemStack r0(KitchenRecipe r) {
        KitchenRecipe.Counted c = r.ingredients().get(0);
        return new ItemStack(c.ingredient().getItems()[0].getItem(), Math.max(1, c.count()));
    }

    // ------------------------------------------------------------------ 2. paires Vider / Remplir, automatisation, faces

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_41_pairs", timeoutTicks = 20)
    public static void p105_41_paires_automatisation(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        StationTiles.Trough trough = station(h, new BlockPos(2, 1, 2), StationBlock.Kind.WATER_TROUGH, StationTiles.Trough.class);
        IItemHandler up = trough.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        Gt.check(up != null, "capacite d'objets de l'abreuvoir");
        Gt.equal(0, up.insertItem(StationTiles.Trough.DRAIN_IN, new ItemStack(Items.WATER_BUCKET), false).getCount(), "seau d'eau pose par l'entonnoir");
        Gt.equal(1000L, trough.farmTank(0).getFluidAmount(), "seau verse dans la cuve (1 000 mB)");
        Gt.check(trough.items().get(StationTiles.Trough.DRAIN_OUT).is(Items.BUCKET), "seau vide rendu dans sa case");
        IItemHandler down = trough.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
        Gt.equal(1, down.extractItem(StationTiles.Trough.DRAIN_OUT, 64, false).getCount(), "seau vide repris par le dessous");
        Gt.equal(1, up.insertItem(StationTiles.Trough.DRAIN_OUT, new ItemStack(Items.WATER_BUCKET), true).getCount(), "rien n'entre dans la case rendue");
        //Faces : objets fermes par le haut, sauvees sous farm_sides
        SideConfig cfg = trough.getSideConfig();
        Gt.check(cfg != null && cfg.types().contains(SideType.ITEMS) && cfg.types().contains(SideType.FLUIDS), "faces : objets et fluides reglables");
        cfg.set(SideType.ITEMS, RelativeSide.TOP, SideMode.NONE);
        IItemHandler closed = trough.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        Gt.check(closed == null || closed.insertItem(StationTiles.Trough.DRAIN_IN, new ItemStack(Items.WATER_BUCKET), true).getCount() == 1,
              "face du haut fermee : l'entonnoir n'entre plus");
        CompoundTag saved = trough.saveWithoutMetadata();
        Gt.check(saved.contains(StationTile.SIDES_NBT), "faces sauvees sous farm_sides");
        StationTiles.Trough back = station(h, new BlockPos(4, 1, 2), StationBlock.Kind.WATER_TROUGH, StationTiles.Trough.class);
        back.load(saved);
        Gt.equal(SideMode.NONE, back.getSideConfig().mode(SideType.ITEMS, Direction.UP), "faces relues");
        Gt.equal(1000L, back.farmTank(0).getFluidAmount(), "cuve relue");
        //Pressoir : seau vide dans « Remplir » -> seau plein, cuve videe de 1 000 mB
        StationTiles.Press press = station(h, new BlockPos(6, 1, 2), StationBlock.Kind.FRUIT_PRESS, StationTiles.Press.class);
        KitchenRecipe juice = null;
        for (KitchenRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiKitchen.TYPES.get(Station.PRESSING).get())) {
            if (!r.fluidResult().isEmpty()) {
                juice = r;
                break;
            }
        }
        Gt.check(juice != null, "aucune recette de pressoir a jus");
        FluidStack fs = juice.fluidResult().copy();
        fs.setAmount(1500);
        Gt.equal(1500, press.farmTank(1).fill(fs, IFluidHandler.FluidAction.EXECUTE), "cuve du pressoir remplie (1 500 mB)");
        IItemHandler pup = press.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        Gt.equal(0, pup.insertItem(StationTiles.Press.FILL_IN, new ItemStack(Items.BUCKET), false).getCount(), "seau vide pose par l'entonnoir");
        ItemStack full = press.items().get(StationTiles.Press.FILL_OUT);
        Gt.check(!full.isEmpty() && !full.is(Items.BUCKET), "seau plein dans la case Remplir (" + full + ")");
        Gt.equal(500L, press.farmTank(1).getFluidAmount(), "1 000 mB tires");
        s.done();
    }

    // ------------------------------------------------------------------ 3. appareils, mondes existants

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_41_devices", timeoutTicks = 20)
    public static void p105_41_appareils_migration(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p41d", "Fermier", new BlockPos(4, 1, 4));
        h.setBlock(new BlockPos(2, 1, 2), PiFarmDevices.DEVICES.get(DeviceBlock.Kind.AUTO_FEEDER).get().defaultBlockState());
        AutoFeederTile feeder = (AutoFeederTile) h.getBlockEntity(new BlockPos(2, 1, 2));
        MachineMenu menu = (MachineMenu) feeder.standardMenu(0, p.getInventory());
        //Ordre de la disposition : Vider (seau plein, seau rendu), puis les 9 cases de nourriture
        Gt.check(menu.slots.get(0).safeInsert(new ItemStack(Items.WATER_BUCKET)).isEmpty(), "seau d'eau pose par la case Vider du menu");
        Gt.equal(1000L, feeder.farmTank(0).getFluidAmount(), "eau du distributeur (1 000 mB)");
        Gt.check(feeder.items().get(AutoFeederTile.DRAIN_OUT).is(Items.BUCKET), "seau vide rendu");
        Gt.check(menu.slots.get(2).safeInsert(new ItemStack(Items.WHEAT, 16)).isEmpty(), "ble pose dans une case de nourriture");
        Gt.check(!menu.slots.get(2).mayPlace(new ItemStack(Items.STONE)), "une pierre n'est pas de la nourriture");
        h.setBlock(new BlockPos(5, 1, 2), PiFarmDevices.DEVICES.get(DeviceBlock.Kind.WEATHER_STATION).get().defaultBlockState());
        WeatherStationTile ws = (WeatherStationTile) h.getBlockEntity(new BlockPos(5, 1, 2));
        Gt.check(ws.getSideConfig() == null && ws.guiSlots().isEmpty(), "station meteo : ecran seul, ni case ni onglet des faces");
        Gt.check(!ws.screenState().lines().isEmpty(), "station meteo : releve a l'ecran");
        //Ancienne marmite (7 cases : 6 ingredients, le plat) : relue sans perte, nouvelles cases (servir, vider) vides
        StationTiles.CookingPot pot = station(h, new BlockPos(2, 1, 5), StationBlock.Kind.COOKING_POT, StationTiles.CookingPot.class);
        CompoundTag old = new CompoundTag();
        ListTag items = new ListTag();
        CompoundTag a = new ItemStack(Items.CARROT, 3).save(new CompoundTag());
        a.putByte("Slot", (byte) 0);
        items.add(a);
        CompoundTag b = new ItemStack(item("beef_stew"), 2).save(new CompoundTag());
        b.putByte("Slot", (byte) 6);
        items.add(b);
        old.put("Items", items);
        pot.load(old);
        Gt.equal(3, pot.items().get(0).getCount(), "marmite d'avant : ingredient relu");
        Gt.check(pot.items().get(StationTiles.CookingPot.OUT).is(item("beef_stew")), "marmite d'avant : plat relu dans sa case");
        for (int i = StationTiles.CookingPot.SERVE_IN; i <= StationTiles.CookingPot.DRAIN_OUT; i++) {
            Gt.check(pot.items().get(i).isEmpty(), "nouvelle case " + i + " vide");
        }
        Gt.equal(SideMode.IO, pot.getSideConfig().mode(SideType.ITEMS, Direction.UP), "sans farm_sides : faces en entree-sortie comme avant");
        p.getInventory().clearContent();
        s.done();
    }

    // ------------------------------------------------------------------ 4. enclos (retour 67)

    static void fence(GameTestHelper h, int x, int z) {
        h.setBlock(new BlockPos(x, 1, z), Blocks.OAK_FENCE.defaultBlockState());
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_41_pens", timeoutTicks = 1200)
    public static void p105_41_enclos(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //Un joueur a cote (IA pleine : loin de tout joueur, l'IA est allegee et le test serait plus lent)
        ServerPlayer watcher = s.player("p41e", "Berger", new BlockPos(6, 1, 9));
        Gt.check(watcher != null, "joueur temoin");
        //Deux enclos de 5 x 5 separes par une cloture (x = 6) : A (x 1..5), B (x 7..11), z 1..5
        for (int x = 0; x <= 12; x++) {
            fence(h, x, 0);
            fence(h, x, 6);
        }
        for (int z = 1; z <= 5; z++) {
            fence(h, 0, z);
            fence(h, 6, z);
            fence(h, 12, z);
        }
        //Clotures reliees (comme posees a la main)
        for (int x = 0; x <= 12; x++) {
            for (int z = 0; z <= 6; z++) {
                BlockPos r = new BlockPos(x, 1, z);
                BlockState st = h.getBlockState(r);
                if (st.is(Blocks.OAK_FENCE)) {
                    h.setBlock(r, st.setValue(FenceBlock.NORTH, h.getBlockState(r.north()).is(Blocks.OAK_FENCE))
                          .setValue(FenceBlock.SOUTH, h.getBlockState(r.south()).is(Blocks.OAK_FENCE))
                          .setValue(FenceBlock.WEST, h.getBlockState(r.west()).is(Blocks.OAK_FENCE))
                          .setValue(FenceBlock.EAST, h.getBlockState(r.east()).is(Blocks.OAK_FENCE)));
                }
            }
        }
        BlockPos ownRel = new BlockPos(1, 1, 1), otherRel = new BlockPos(8, 1, 3);
        StationTiles.Trough own = station(h, ownRel, StationBlock.Kind.WATER_TROUGH, StationTiles.Trough.class);
        StationTiles.Trough other = station(h, otherRel, StationBlock.Kind.WATER_TROUGH, StationTiles.Trough.class);
        FluidStack water = new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 2000);
        own.farmTank(0).fill(water.copy(), IFluidHandler.FluidAction.EXECUTE);
        other.farmTank(0).fill(water.copy(), IFluidHandler.FluidAction.EXECUTE);
        Animal cow = (Animal) h.spawn(PiBreeds.type(AnimalSpecies.COW, 0), new BlockPos(5, 1, 3));
        h.runAfterDelay(5, () -> {
            AnimalData d = PiAnimals.get(cow);
            Gt.check(d != null, "vache sans donnees d'elevage");
            d.domestic = true;
            d.hunger = 100;
            d.thirst = 10;
            //Le vrai choix : l'abreuvoir du voisin (plus proche, derriere la cloture) est essaye puis ecarte, le sien est retenu
            BlockPos ownAbs = h.absolutePos(ownRel), otherAbs = h.absolutePos(otherRel);
            Gt.check(otherAbs.distSqr(cow.blockPosition()) < ownAbs.distSqr(cow.blockPosition()), "l'abreuvoir du voisin est le plus proche");
            SiteChoice choice = new SiteChoice();
            choice.begin();
            long now = s.level.getGameTime();
            SiteChoice.Choice c = choice.choose(List.of(otherAbs, ownAbs), now, 0, to -> cow.getNavigation().createPath(to, 1),
                  (to, x, y, z) -> NeedGoal.inReach(to, x, y, z, cow.getBbWidth()));
            Gt.check(c != null && c.target().equals(ownAbs), "choix : son propre abreuvoir (" + (c == null ? "aucun" : c.target()) + ")");
            Gt.check(choice.forgotten(otherAbs, now), "l'abreuvoir du voisin, injoignable, est ecarte");
            Gt.check(choice.computed() <= SiteChoice.PATHS_PER_DECISION, "au plus " + SiteChoice.PATHS_PER_DECISION + " calculs de chemin");
            //En vrai : la vache assoiffee boit dans son enclos
            h.succeedWhen(() -> {
                Gt.check(own.farmTank(0).getFluidAmount() < 2000, "la vache n'a pas encore bu dans son abreuvoir");
                Gt.equal(2000L, other.farmTank(0).getFluidAmount(), "l'abreuvoir du voisin n'a pas ete touche");
                cow.discard();
                s.done();
            });
        });
    }
}
