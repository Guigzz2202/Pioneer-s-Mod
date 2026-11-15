package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.core.common.PiRemaps;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.bag.BagDeposit;
import fr.pioneers.building.home.bag.BagKind;
import fr.pioneers.building.home.bag.BagStore;
import fr.pioneers.core.registries.PiBronze;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 35 (verification finale independante des retours de jeu 4e serie, lots 31 a 34 ; GameTests) :
 * <ul>
 *     <li>SACS, RAMASSAGE PARTIEL (retour 42, duplication trouvee au lot 35) : une case du sac a graines a 1 020 / 1 024, une pile de 10
 *     graines ramassee par le vrai {@code ItemEntity.playerTouch} : 4 vont au sac, 6 a l'inventaire, la pile disparait (avant : la pile
 *     neuve posee par {@code setItem} laissait l'ancienne, entiere, aller a l'inventaire : 4 graines en double) ; un 2e joueur ne
 *     ramasse plus rien ;</li>
 *     <li>SACS EN MULTIJOUEUR (retour 42) : deux joueurs ont le meme coffre ouvert, l'un y vide son sac (Maj + clic droit dans l'ecran du
 *     coffre) : coffre + sac = avant, l'autre voit le meme coffre ; une case du coffre n'est jamais prise pour le sac ;</li>
 *     <li>MACHINES A VAPEUR DES ETABLIS (retour 48, lot 34) : une presse verrouillee sur une recette partie a un autre etabli (aquarium au
 *     potier) relue d'une sauvegarde : rien ne plante, le verrou est efface proprement, les materiaux restent ; un verrou encore valable
 *     (revolver, arrive au metallier) est garde ; les copies automatisees deplacees sont suivies ({@code PiRemaps.P105_LOT34_RECIPES}).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot35Tests {

    private PatchP105Lot35Tests() {
    }

    static int count(Inventory inv, Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ 1. ramassage partiel : aucune graine en double

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_35_pickup", timeoutTicks = 20)
    public static void p105_35_ramassage_partiel_sans_doublon(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p35a", "Albane", new BlockPos(4, 1, 4));
        ServerPlayer q = s.player("p35b", "Basile", new BlockPos(4, 1, 5));
        p.getInventory().clearContent();
        q.getInventory().clearContent();
        ItemStack bag = new ItemStack(PiHome.BAGS.get(BagKind.SEED_BAG).get());
        p.getInventory().setItem(0, bag);
        Item seed = PiFarm.seedOf("wheat");
        Gt.check(BagStore.insert(bag, BagKind.SEED_BAG, new ItemStack(seed, 1020), false, false).isEmpty(), "1 020 graines rangees");
        Gt.equal(1020, BagStore.read(bag, BagKind.SEED_BAG).total(), "case a 1 020 / 1 024");
        ItemEntity e = new ItemEntity(s.level, p.getX(), p.getY(), p.getZ(), new ItemStack(seed, 10));
        s.level.addFreshEntity(e);
        e.setNoPickUpDelay();
        e.playerTouch(p);
        int inBag = BagStore.read(p.getInventory().getItem(0), BagKind.SEED_BAG).total();
        int inInv = count(p.getInventory(), seed);
        Gt.equal(1024, inBag, "le sac se remplit jusqu'a sa capacite (4 graines)");
        Gt.equal(6, inInv, "le reste (6) va a l'inventaire, pas la pile entiere");
        Gt.equal(1030, inBag + inInv, "sac + inventaire = 1 020 + 10 : aucune graine en double");
        Gt.check(e.isRemoved(), "la pile au sol a disparu");
        //2e joueur (meme chemin que Player.aiStep : une entite retiree n'est plus touchee)
        if (!e.isRemoved()) {
            e.playerTouch(q);
        }
        Gt.equal(0, count(q.getInventory(), seed), "le 2e joueur ne ramasse rien de plus");
        //pile ramassee par un sac qui a de la place : tout entre, la pile disparait, l'inventaire ne change pas
        ItemStack other = new ItemStack(PiHome.BAGS.get(BagKind.SEED_BAG).get());
        q.getInventory().setItem(0, other);
        ItemEntity f = new ItemEntity(s.level, q.getX(), q.getY(), q.getZ(), new ItemStack(seed, 7));
        s.level.addFreshEntity(f);
        f.setNoPickUpDelay();
        f.playerTouch(q);
        Gt.check(f.isRemoved(), "pile rangee entiere : retiree");
        Gt.equal(7, BagStore.read(q.getInventory().getItem(0), BagKind.SEED_BAG).total(), "7 graines dans le sac du 2e joueur");
        Gt.equal(0, count(q.getInventory(), seed), "rien dans son inventaire");
        if (!f.isRemoved()) {
            f.playerTouch(p);
        }
        Gt.equal(6, count(p.getInventory(), seed), "le 1er joueur ne ramasse pas une pile deja prise");
        s.done();
    }

    // ------------------------------------------------------------------ 2. vidage dans un coffre ouvert par deux joueurs

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_35_chest", timeoutTicks = 20)
    public static void p105_35_vidage_coffre_deux_joueurs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p35c", "Clotilde", new BlockPos(3, 1, 3));
        ServerPlayer q = s.player("p35d", "Damien", new BlockPos(3, 1, 4));
        p.getInventory().clearContent();
        q.getInventory().clearContent();
        BlockPos chest = h.absolutePos(new BlockPos(2, 1, 2));
        s.level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity cb = (ChestBlockEntity) s.level.getBlockEntity(chest);
        cb.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        ItemStack bag = new ItemStack(PiHome.BAGS.get(BagKind.SEED_BAG).get());
        p.getInventory().setItem(0, bag);
        Item seed = PiFarm.seedOf("wheat");
        BagStore.insert(bag, BagKind.SEED_BAG, new ItemStack(seed, 700), false, false);
        p.openMenu(cb);
        q.openMenu(cb);
        Gt.check(p.containerMenu != p.inventoryMenu && q.containerMenu != q.inventoryMenu, "les deux joueurs ont le coffre ouvert");
        //case du coffre visee (pas un sac) : rien
        Gt.equal(0, BagDeposit.intoOpenMenu(p, 0), "une case du coffre n'est pas un sac");
        //sac de la barre d'action (case 0) : derniere rangee du menu du coffre (27 cases du coffre + 27 de l'inventaire)
        int bagSlot = 27 + 27;
        Gt.check(p.containerMenu.slots.get(bagSlot).getItem() == p.getInventory().getItem(0), "case du sac dans le menu du coffre");
        int moved = BagDeposit.intoOpenMenu(p, bagSlot);
        int inChest = 0;
        for (int i = 0; i < cb.getContainerSize(); i++) {
            if (cb.getItem(i).is(seed)) {
                inChest += cb.getItem(i).getCount();
            }
        }
        Gt.check(moved > 0, "le sac s'est vide dans le coffre");
        Gt.equal(moved, inChest, "le coffre a recu ce qui a quitte le sac");
        Gt.equal(700, inChest + BagStore.read(p.getInventory().getItem(0), BagKind.SEED_BAG).total(), "coffre + sac = avant");
        Gt.equal(64, (long) cb.getItem(0).getCount(), "la pierre du coffre n'a pas bouge");
        int seenByQ = 0;
        for (int i = 0; i < 27; i++) {
            if (q.containerMenu.slots.get(i).getItem().is(seed)) {
                seenByQ += q.containerMenu.slots.get(i).getItem().getCount();
            }
        }
        Gt.equal(inChest, seenByQ, "le 2e joueur voit le meme coffre");
        Gt.equal(0, BagDeposit.intoOpenMenu(q, bagSlot), "le 2e joueur n'a pas de sac : rien");
        p.closeContainer();
        q.closeContainer();
        s.done();
    }

    // ------------------------------------------------------------------ 3. presse a vapeur verrouillee sur une recette partie

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_35_press", timeoutTicks = 20)
    public static void p105_35_presse_recette_partie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(3, 1, 3);
        //1.0.5-beta (lot 38) : la presse a vapeur n'est plus un atelier d'etabli (elle aplatit : machine de traitement) ; une presse d'etabli
        //sauvee avant (verrou « recipe », 6 materiaux) se relit : materiaux poses au sol, verrou efface
        h.setBlock(at, PiBronze.STEAM_MACHINES.get(fr.pioneers.tech.bronze.machines.SteamMachine.PRESS).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof SteamProcessTile, "presse a vapeur posee");
        SteamProcessTile press = (SteamProcessTile) h.getBlockEntity(at);
        ResourceLocation gone = new ResourceLocation("pioneers", "aquarium/aquarium");
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "pioneers:steam_press");
        net.minecraft.nbt.ListTag workshop = new net.minecraft.nbt.ListTag();
        CompoundTag slot = new ItemStack(Items.GLASS_PANE, 5).save(new CompoundTag());
        slot.putShort("Slot", (short) 0);
        workshop.add(slot);
        tag.put("slots", workshop);
        tag.putString("recipe", gone.toString());
        press.load(tag);
        Gt.check(press.lockedRecipe() == null, "verrou d'une presse d'etabli : efface");
        press.steam(Direction.DOWN).add(300, 0);
        press.serverTick(s.level);
        Gt.check(press.guiSlots().get(0).getStack().isEmpty(), "les materiaux ne restent pas dans la presse");
        Gt.check(!h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(at)).inflate(2),
              e -> e.getItem().is(Items.GLASS_PANE) && e.getItem().getCount() == 5).isEmpty(), "materiaux poses au sol, rien de perdu");
        //copies automatisees deplacees au lot 34 : verrous suivis
        for (Map.Entry<String, String> m : PiRemaps.P105_LOT34_RECIPES.entrySet()) {
            ResourceLocation old = new ResourceLocation("pioneers", m.getKey());
            ResourceLocation now = new ResourceLocation("pioneers", m.getValue());
            Gt.equal(now, PiRemaps.recipeId(old), "verrou suivi : " + old);
            Gt.check(s.level.getRecipeManager().byKey(now).isPresent(), "copie deplacee chargee : " + now);
            Gt.check(s.level.getRecipeManager().byKey(old).isEmpty(), "ancienne copie absente : " + old);
        }
        s.done();
    }
}
