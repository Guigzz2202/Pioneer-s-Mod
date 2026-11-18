package fr.pioneers.gametest;

import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.crop.CropTile;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crop.Genes;
import fr.pioneers.farm.crop.GeneticSeedItem;
import fr.pioneers.farm.fish.FishItems;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.bag.BagDeposit;
import fr.pioneers.building.home.bag.BagEvents;
import fr.pioneers.building.home.bag.BagItem;
import fr.pioneers.building.home.bag.BagKind;
import fr.pioneers.building.home.bag.BagLedger;
import fr.pioneers.building.home.bag.BagStore;
import fr.pioneers.building.home.bag.BagUpgradeRecipe;
import fr.pioneers.building.home.bag.StorageBagMenu;
import fr.pioneers.world.hunt.HerdData;
import fr.pioneers.world.hunt.HerdEvents;
import fr.pioneers.world.hunt.HuntRules;
import fr.pioneers.world.pet.PetDog;
import fr.pioneers.world.pet.PetMenu;
import fr.pioneers.world.pet.PiPets;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.5-beta, lot 31 (retours de jeu 4e serie, 1 : sacs et chasses ; GameTests) :
 * <ul>
 *     <li>RECOLTE D'UN CHAMP (retour 42) : 5 x 5 de ble mur aux genes forts, recolte au vrai clic droit ({@code FarmCropBlock.use}), objets
 *     ramasses par le vrai {@code ItemEntity.playerTouch} : toutes les graines filles (genes exacts, combinaisons diverses) vont dans le sac a
 *     graines range au fond de l'inventaire, l'inventaire ne garde que le ble ; semis 3 x 3 depuis le sac ;</li>
 *     <li>PECHE (retour 42) : la prise va dans le panier de peche (main secondaire), cabillaud ramasse aussi ; panier plein ou ramassage arrete :
 *     l'inventaire ; filtre ;</li>
 *     <li>PAS DE DUPLICATION (retour 42) : ecran du sac ouvert pendant un ramassage, pile prise puis ecran ferme, mort (tout tombe : le sac garde
 *     tout, l'ecran se ferme, plus aucun geste), vidage dans un coffre (coffre + sac = avant), amelioration de niveau (contenu garde) ;</li>
 *     <li>BOUTON CHASSE (retour 43) : eteint sans troupeau, allume avec un troupeau a portee du flair, eteint s'il est trop loin ou en
 *     dispersion.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot31Tests {

    private PatchP105Lot31Tests() {
    }

    static ItemStack bag(BagKind kind) {
        return new ItemStack(PiHome.BAGS.get(kind).get());
    }

    static int count(Inventory inv, java.util.function.Predicate<ItemStack> what) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && what.test(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Ramasse tout ce qui est au sol pres de {@code abs} par le vrai chemin du jeu (evenement de ramassage compris). */
    static int pickUpAll(Gt.Scope s, ServerPlayer p, BlockPos abs, int radius) {
        int n = 0;
        for (ItemEntity e : s.level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(radius))) {
            n += e.getItem().getCount();
            e.setNoPickUpDelay();
            e.playerTouch(p);
        }
        return n;
    }

    // ------------------------------------------------------------------ 1. recolte d'un champ avec le sac

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_31_field", timeoutTicks = 40)
    public static void p105_31_recolte_champ_sac(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p31f", "Ceres", new BlockPos(2, 1, 2));
        p.getInventory().clearContent();
        ItemStack seedBag = bag(BagKind.SEED_BAG);
        p.getInventory().setItem(30, seedBag);
        FarmCropBlock wheat = (FarmCropBlock) ((BlockItem) PiFarm.seedOf("wheat")).getBlock();
        BlockState ripe = wheat.getStateForAge(wheat.getMaxAge());
        BlockPos o = h.absolutePos(new BlockPos(6, 1, 6));
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                BlockPos c = o.offset(dx, 0, dz);
                s.level.setBlock(c.below(), Blocks.FARMLAND.defaultBlockState(), 3);
                s.level.setBlock(c, ripe, 3);
                if (s.level.getBlockEntity(c) instanceof CropTile t) {
                    t.setGenes(new Genes(6, 5, 5, 6));
                }
            }
        }
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 5; dz++) {
                BlockPos c = o.offset(dx, 0, dz);
                s.level.getBlockState(c).use(s.level, p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(c), Direction.UP, c, false));
            }
        }
        int dropped = pickUpAll(s, p, o.offset(2, 0, 2), 8);
        BagLedger l = BagStore.read(seedBag, BagKind.SEED_BAG);
        int inSeeds = count(p.getInventory(), st -> st.getItem() instanceof GeneticSeedItem);
        int other = count(p.getInventory(), st -> !(st.getItem() instanceof BagItem));
        Gt.check(dropped > 25, "la recolte a rendu des objets (" + dropped + ")");
        Gt.equal(0, inSeeds, "aucune graine dans l'inventaire (toutes au sac)");
        Gt.check(l.total() > 0 && l.entries.size() == 1, "les graines filles sont dans UNE case du sac (" + l.total() + " en " + l.entries.size() + ")");
        Gt.equal(dropped, l.total() + other, "rien de perdu : ble de l'inventaire + graines du sac = recolte");
        int used = 0;
        for (int i = 0; i < 36; i++) {
            used += p.getInventory().getItem(i).isEmpty() ? 0 : 1;
        }
        Gt.check(used <= 3, "inventaire presque vide : le ble et le sac (" + used + " cases)");
        Gt.check(s.level.getBlockState(o).getBlock() == wheat && !wheat.isMaxAge(s.level.getBlockState(o)), "le champ est replante (" + s.level.getBlockState(o)
              + ", dessous " + s.level.getBlockState(o.below()) + ")");
        //semis 3 x 3 depuis le sac (clic droit sur la terre labouree du centre) : les 8 voisines ont deja une pousse, on vide d'abord
        BlockPos sow = h.absolutePos(new BlockPos(14, 1, 14));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                s.level.setBlock(sow.offset(dx, -1, dz), Blocks.FARMLAND.defaultBlockState(), 3);
                s.level.setBlock(sow.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        int before = l.total();
        int planted = BagItem.sow(s.level, p, InteractionHand.MAIN_HAND, seedBag, sow.below(), BagItem.SOW_RADIUS);
        if (before >= 9) {
            Gt.equal(9, planted, "semis 3 x 3 depuis le sac");
        }
        Gt.equal(before - planted, BagStore.read(seedBag, BagKind.SEED_BAG).total(), "graines semees retirees du sac");
        s.done();
    }

    // ------------------------------------------------------------------ 2. peche : le panier

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_31_fish", timeoutTicks = 20)
    public static void p105_31_peche_panier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p31p", "Pierre", new BlockPos(4, 1, 4));
        p.getInventory().clearContent();
        ItemStack creel = bag(BagKind.FISH_BAG);
        p.setItemInHand(InteractionHand.OFF_HAND, creel);
        ItemStack pike = FishItems.of(FishItems.index("pike"), 44);
        Gt.check(BagEvents.deliver(p, pike.copy()).isEmpty(), "la prise de peche va directement au panier (main secondaire)");
        ItemEntity cod = new ItemEntity(s.level, p.getX(), p.getY(), p.getZ(), new ItemStack(Items.COD, 3));
        s.level.addFreshEntity(cod);
        cod.setNoPickUpDelay();
        cod.playerTouch(p);
        ItemStack held = p.getItemInHand(InteractionHand.OFF_HAND);
        BagLedger l = BagStore.read(held, BagKind.FISH_BAG);
        Gt.equal(4, (long) l.total(), "brochet + 3 cabillauds au panier");
        Gt.equal(0, count(p.getInventory(), st -> st.is(Items.COD)), "aucun cabillaud dans l'inventaire");
        Gt.check(!BagKind.FISH_BAG.accepts(new ItemStack(Items.COOKED_COD)), "le poisson CUIT n'est pas pour le panier");
        //ramassage arrete : l'inventaire
        l.pickup = false;
        BagStore.write(held, l);
        Gt.equal(2, BagEvents.absorb(p, new ItemStack(Items.SALMON, 2)).getCount(), "ramassage arrete : rien n'entre");
        l.pickup = true;
        l.filter.add("minecraft:salmon");
        BagStore.write(held, l);
        Gt.equal(2, BagEvents.absorb(p, new ItemStack(Items.SALMON, 2)).getCount(), "filtre : le saumon reste dehors");
        Gt.check(BagEvents.absorb(p, new ItemStack(Items.TROPICAL_FISH, 2)).isEmpty(), "filtre : le reste entre");
        //appat : la canne le prend dans le panier
        Gt.check(BagStore.insert(held, BagKind.FISH_BAG, new ItemStack(PiFarm.seedOf("wheat")), true, false).getCount() == 1, "une graine n'entre pas");
        s.done();
    }

    // ------------------------------------------------------------------ 3. pas de duplication

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_31_dupe", timeoutTicks = 20)
    public static void p105_31_pas_de_duplication(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p31d", "Duplo", new BlockPos(4, 1, 4));
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        ItemStack seedBag = bag(BagKind.SEED_BAG);
        p.getInventory().setItem(0, seedBag);
        ItemStack seeds = new ItemStack(PiFarm.seedOf("wheat"), 64);
        Gt.check(BagStore.insert(seedBag, BagKind.SEED_BAG, seeds.copyWithCount(64), false, false).isEmpty(), "64 graines rangees");
        Gt.check(BagStore.insert(seedBag, BagKind.SEED_BAG, seeds.copyWithCount(900), false, false).isEmpty(), "900 de plus (case de 1 024)");
        Gt.equal(964, BagStore.read(seedBag, BagKind.SEED_BAG).total(), "964 graines dans une case de 1 024");
        //ecran ouvert, ramassage pendant l'ecran, pile prise, ecran ferme
        ((BagItem) seedBag.getItem()).open(p, InteractionHand.MAIN_HAND, seedBag);
        Gt.check(p.containerMenu instanceof StorageBagMenu, "ecran du sac ouvert");
        StorageBagMenu menu = (StorageBagMenu) p.containerMenu;
        ItemEntity e = new ItemEntity(s.level, p.getX(), p.getY(), p.getZ(), seeds.copyWithCount(10));
        s.level.addFreshEntity(e);
        e.setNoPickUpDelay();
        e.playerTouch(p);
        Gt.equal(974, BagStore.read(p.getMainHandItem(), BagKind.SEED_BAG).total(), "ramassage pendant l'ecran : dans le sac tenu");
        menu.act(p, StorageBagMenu.TAKE, 0, BagStore.idOf(seeds), 0);
        Gt.equal(64, menu.getCarried().getCount(), "une pile prise en main");
        Gt.equal(910, BagStore.read(p.getMainHandItem(), BagKind.SEED_BAG).total(), "pile retiree du sac");
        p.closeContainer();
        Gt.equal(910 + 64, BagStore.read(p.getMainHandItem(), BagKind.SEED_BAG).total() + count(p.getInventory(), st -> st.is(seeds.getItem())),
              "ecran ferme : la pile tenue revient a l'inventaire, rien de double");
        //mort : tout tombe (comme Player.dropEquipment), l'ecran se ferme, plus aucun geste
        ((BagItem) seedBag.getItem()).open(p, InteractionHand.MAIN_HAND, p.getMainHandItem());
        AbstractContainerMenu open = p.containerMenu;
        int total = BagStore.read(p.getMainHandItem(), BagKind.SEED_BAG).total();
        p.getInventory().dropAll();
        Gt.check(!open.stillValid(p), "sac tombe : l'ecran n'est plus valide");
        ((StorageBagMenu) open).act(p, StorageBagMenu.TAKE, 0, BagStore.idOf(seeds), 0);
        Gt.check(open.getCarried().isEmpty(), "plus aucun geste apres la chute du sac");
        List<ItemEntity> fallen = s.level.getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(4),
              it -> it.getItem().getItem() instanceof BagItem);
        Gt.equal(1, fallen.size(), "un seul sac au sol");
        ItemStack lying = fallen.get(0).getItem();
        Gt.equal(total, BagStore.read(lying, BagKind.SEED_BAG).total(), "le sac tombe garde tout son contenu");
        p.closeContainer();
        //coffre : vidage, coffre + sac = avant
        BlockPos chest = h.absolutePos(new BlockPos(2, 1, 2));
        s.level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity cb = (ChestBlockEntity) s.level.getBlockEntity(chest);
        int moved = BagDeposit.intoHandler(cb.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new), lying,
              BagKind.SEED_BAG);
        int inChest = 0;
        for (int i = 0; i < cb.getContainerSize(); i++) {
            inChest += cb.getItem(i).getCount();
        }
        Gt.equal(moved, inChest, "le coffre a recu ce qui a ete depose");
        Gt.equal(total, inChest + BagStore.read(lying, BagKind.SEED_BAG).total(), "coffre + sac = avant");
        //amelioration de niveau : contenu garde, recette du niveau suivant seulement
        ItemStack small = bag(BagKind.SEED_BAG);
        BagStore.insert(small, BagKind.SEED_BAG, seeds.copyWithCount(500), false, false);
        var recipe = s.level.getRecipeManager().byKey(new ResourceLocation("pioneers", "home/seed_bag_tier2")).orElse(null);
        Gt.check(recipe instanceof BagUpgradeRecipe, "recette de niveau II chargee");
        TransientCraftingContainer grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @NotNull
            @Override
            public ItemStack quickMoveStack(@NotNull Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(@NotNull Player player) {
                return true;
            }
        }, 3, 3);
        String[] pattern = {"RTR", "TBT", " T "};
        for (int i = 0; i < 9; i++) {
            char c = pattern[i / 3].charAt(i % 3);
            grid.setItem(i, c == 'R' ? new ItemStack(BuiltIns.item("pioneers:iron_ring")) : c == 'T' ? new ItemStack(BuiltIns.item("pioneers:tanned_leather"))
                                                                                                     : c == 'B' ? small.copy() : ItemStack.EMPTY);
        }
        BagUpgradeRecipe up = (BagUpgradeRecipe) recipe;
        Gt.check(up.matches(grid, s.level), "le sac de niveau I s'ameliore");
        ItemStack out = up.assemble(grid, s.level.registryAccess());
        BagLedger ol = BagStore.read(out, BagKind.SEED_BAG);
        Gt.equal(1, ol.tier, "niveau II");
        Gt.equal(500, ol.total(), "contenu garde");
        grid.setItem(4, out);
        Gt.check(!up.matches(grid, s.level), "un sac de niveau II ne repasse pas la recette du niveau II");
        s.done();
    }

    // ------------------------------------------------------------------ 4. bouton Chasse

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_31_hunt", timeoutTicks = 20)
    public static void p105_31_bouton_chasse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p31h", "Artemis", new BlockPos(3, 1, 3));
        PetDog dog = PatchP105Lot28Tests.add(s, PiPets.DOGS.get(1).get(), h.absolutePos(new BlockPos(5, 1, 5)));
        dog.tame(p);
        //aucun troupeau du serveur pres d'ici
        HerdData data = HerdData.get(s.server);
        java.util.Map<Integer, HerdData.Herd> saved = new java.util.HashMap<>();
        for (HerdData.Herd x : List.copyOf(data.herds())) {
            saved.put(x.id, x);
            data.remove(x.id);
        }
        s.onCleanup(() -> saved.values().forEach(data::put));
        PetMenu menu = new PetMenu(0, p.getInventory(), dog, p);
        Gt.equal(0, menu.get(PetMenu.HUNT_LIT), "sans troupeau : bouton Chasse eteint");
        double reach = HerdEvents.scentOf(dog, p) * HuntRules.TRAIL_FACTOR;
        BlockPos near = dog.blockPosition().offset((int) (reach * 0.5), 0, 0), far = dog.blockPosition().offset((int) (reach + 20), 0, 0);
        HerdData.Herd herd = data.add(p.getUUID(), "pioneers:deer", near, s.level.getGameTime());
        herd.members.add(UUID.randomUUID());
        s.onCleanup(() -> data.remove(herd.id));
        menu.recheckHunt();
        Gt.equal(1, menu.get(PetMenu.HUNT_LIT), "troupeau a portee du flair : bouton allume");
        herd.disperse = s.level.getGameTime();
        menu.recheckHunt();
        Gt.equal(0, menu.get(PetMenu.HUNT_LIT), "troupeau en dispersion : eteint");
        data.remove(herd.id);
        HerdData.Herd away = data.add(p.getUUID(), "pioneers:deer", far, s.level.getGameTime());
        away.members.add(UUID.randomUUID());
        s.onCleanup(() -> data.remove(away.id));
        menu.recheckHunt();
        Gt.equal(0, menu.get(PetMenu.HUNT_LIT), "troupeau hors de portee du flair (" + Math.round(reach) + " blocs) : eteint");
        s.done();
    }

    /** Objets par identifiant (registre Forge). */
    static final class BuiltIns {
        private BuiltIns() {
        }

        static net.minecraft.world.item.Item item(String id) {
            net.minecraft.world.item.Item it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
            Gt.check(it != null && it != Items.AIR, "objet " + id);
            return it;
        }
    }
}
