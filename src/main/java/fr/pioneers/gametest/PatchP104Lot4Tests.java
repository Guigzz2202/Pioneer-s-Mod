package fr.pioneers.gametest;

import fr.pioneers.core.vanilla.VanillaRecipeFilter;
import fr.pioneers.economy.SpaceGoods;
import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PetFood;
import fr.pioneers.home.PiHome;
import fr.pioneers.home.bag.BagContents;
import fr.pioneers.home.bag.BagKind;
import fr.pioneers.home.bag.FishStacking;
import fr.pioneers.home.basket.BasketTile;
import fr.pioneers.village.VillageCatalog;
import fr.pioneers.village.VillagerTrade;
import fr.pioneers.village.Villagers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Patch 1.0.4-beta, lot 4 (GameTests) : correctifs de jeu. Une poule suit et mange une graine du mod (et une graine des astres) ; un chat
 * s'apprivoise avec un poisson du mod (le poisson est consomme) ; le panier de peche et le panier en osier empilent deux poissons de tailles
 * differentes (la pile garde la plus grande) ; aucune recette de grille ou d'etabli ne donne un vehicule ; aucun echange de villageois
 * (catalogues des treize metiers, doctrines, et offres d'un vrai fermier) ne propose un objet de l'espace.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot4Tests {

    private PatchP104Lot4Tests() {
    }

    private static Item item(String id) {
        Item it = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        Gt.check(it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static boolean hasGoal(Mob mob, Class<?> type) {
        for (WrappedGoal w : mob.goalSelector.getAvailableGoals()) {
            if (type.isInstance(w.getGoal())) {
                return true;
            }
        }
        return false;
    }

    /** Poule : attiree (but de tentation) et nourrie par une graine du mod et une graine des astres. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104d_chicken", timeoutTicks = 40)
    public static void poule_graine_du_mod(GameTestHelper h) {
        Chicken chicken = h.spawnWithNoFreeWill(EntityType.CHICKEN, new BlockPos(4, 2, 4));
        PetFood.addGoals(chicken);
        Gt.check(hasGoal(chicken, PetFood.SeedTemptGoal.class), "la poule n'a pas le but de tentation par les graines");
        ItemStack barley = new ItemStack(item("pioneers:barley_seeds"));
        ItemStack space = new ItemStack(item("pioneers:silver_bean_vine_seeds"));
        ItemStack torch = new ItemStack(Items.TORCHFLOWER_SEEDS);
        for (ItemStack s : List.of(barley, space, torch)) {
            Gt.check(PetFood.seed(s), "graine hors du tag pioneers:seeds/all : " + s);
            Gt.check(AnimalSpecies.CHICKEN.feedValue(s) > 0, "la poule ne mange pas " + s);
        }
        Gt.check(!PetFood.seed(new ItemStack(Items.WHEAT)), "le ble n'est pas une graine");
        h.succeed();
    }

    /** Chat : un poisson du mod l'attire et l'apprivoise (1 chance sur 3 a chaque poisson, comme la morue), le poisson est consomme. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104d_cat", timeoutTicks = 40)
    public static void chat_poisson_du_mod(GameTestHelper h) {
        Cat cat = h.spawnWithNoFreeWill(EntityType.CAT, new BlockPos(4, 2, 4));
        PetFood.addGoals(cat);
        Gt.check(hasGoal(cat, PetFood.FishTemptGoal.class), "le chat n'a pas le but de tentation par les poissons du mod");
        Item pike = item("pioneers:pike");
        Gt.check(PetFood.catFish(new ItemStack(pike)), "le brochet n'est pas un poisson pour chat");
        Player p = h.makeMockPlayer();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(pike, 64));
        int fed = 0;
        while (!cat.isTame() && fed < 60) {
            PetFood.feedAsCod(cat, p, InteractionHand.MAIN_HAND);
            fed++;
        }
        Gt.check(cat.isTame(), "chat non apprivoise apres 60 poissons du mod");
        Gt.check(cat.isOwnedBy(p), "le chat n'appartient pas au joueur");
        ItemStack left = p.getItemInHand(InteractionHand.MAIN_HAND);
        Gt.check(left.is(pike), "le joueur ne tient plus le brochet (" + left + ")");
        Gt.equal(64 - fed, left.getCount(), "brochets consommes (un par essai)");
        h.succeed();
    }

    /** Panier de peche (sac) et panier en osier : deux brochets de tailles differentes font une pile de 2, a la plus grande taille. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104d_basket", timeoutTicks = 40)
    public static void panier_poissons_empiles(GameTestHelper h) {
        Item pike = item("pioneers:pike");
        ItemStack small = new ItemStack(pike), big = new ItemStack(pike);
        FishStacking.setSize(small, 31);
        FishStacking.setSize(big, 58);
        Gt.check(!ItemStack.isSameItemSameTags(small, big), "les deux brochets devraient differer");
        ItemStack bag = new ItemStack(PiHome.BAGS.get(BagKind.FISH_BAG).get());
        Gt.check(BagContents.insert(bag, BagKind.FISH_BAG, small, false).isEmpty(), "petit brochet refuse par le panier");
        Gt.check(BagContents.insert(bag, BagKind.FISH_BAG, big, false).isEmpty(), "grand brochet refuse par le panier");
        ItemStackHandler in = BagContents.read(bag, BagKind.FISH_BAG);
        int stacks = 0;
        for (int i = 0; i < in.getSlots(); i++) {
            ItemStack s = in.getStackInSlot(i);
            if (!s.isEmpty()) {
                stacks++;
                Gt.equal(2, s.getCount(), "pile du panier");
                Gt.equal(58, FishStacking.size(s), "taille gardee (la plus grande)");
            }
        }
        Gt.equal(1, stacks, "piles dans le panier de peche");
        //panier en osier
        BlockPos at = new BlockPos(4, 2, 4);
        h.setBlock(at, PiHome.BASKET.getBlock().defaultBlockState());
        BasketTile tile = (BasketTile) h.getLevel().getBlockEntity(h.absolutePos(at));
        Gt.equal(1, tile.insert(small, 1, false), "panier en osier : petit brochet");
        Gt.equal(1, tile.insert(big, 1, false), "panier en osier : grand brochet (autre taille)");
        Gt.equal(2, tile.count(), "brochets dans le panier en osier");
        Gt.equal(58, FishStacking.size(tile.kind()), "panier en osier : taille gardee");
        h.succeed();
    }

    /** Aucune recette de grille ni d'etabli ne donne un vehicule (Ateliers ferroviaires et chantier naval seulement). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104d_vehicles", timeoutTicks = 40)
    public static void vehicules_aux_ateliers(GameTestHelper h) {
        var server = h.getLevel().getServer();
        VanillaRecipeFilter.filter(server);
        int vehicles = 0;
        for (Item it : BuiltInRegistries.ITEM) {
            if (new ItemStack(it).is(VanillaRecipeFilter.VEHICLES)) {
                vehicles++;
            }
        }
        Gt.check(vehicles >= 100, "tag pioneers:vehicles trop petit : " + vehicles);
        for (Recipe<?> r : server.getRecipeManager().getRecipes()) {
            Gt.check(!VanillaRecipeFilter.vehicleCraft(r, server), "recette d'etabli ou de grille d'un vehicule : " + r.getId());
        }
        h.succeed();
    }

    /** Aucun echange de villageois avec un objet de l'espace : catalogues (13 metiers x 2 versions, doctrines) et offres d'un fermier. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p104d_trades", timeoutTicks = 60)
    public static void villageois_sans_espace(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        try {
            Gt.check(SpaceGoods.is(new ItemStack(item("pioneers:silver_bean_vine_seeds"))), "graine des astres hors du tag pioneers:space_only");
            Gt.check(!SpaceGoods.is(new ItemStack(item("pioneers:barley_seeds"))), "graine d'orge dans pioneers:space_only");
            int seen = 0;
            for (String prof : Villagers.KNOWN) {
                VillageCatalog.Profession p = VillageCatalog.profession(prof);
                Gt.check(p != null, "metier sans catalogue : " + prof);
                for (String ver : List.of("standard", "rural")) {
                    VillageCatalog.Version v = p.version(ver);
                    Gt.check(!v.sells().isEmpty(), "metier vide : " + prof + " " + ver);
                    for (ItemStack it : v.sells()) {
                        Gt.check(!SpaceGoods.is(it), prof + " " + ver + " vend un objet de l'espace : " + it);
                        seen++;
                    }
                    for (ItemStack it : v.workshopSells()) {
                        Gt.check(!SpaceGoods.is(it), prof + " " + ver + " (atelier) vend un objet de l'espace : " + it);
                    }
                    for (Item it : v.buys()) {
                        Gt.check(!SpaceGoods.is(it), prof + " " + ver + " achete un objet de l'espace : " + it);
                    }
                }
            }
            for (String d : List.of("lettered", "earthbound", "mercantile")) {
                VillageCatalog.Doctrine doc = VillageCatalog.doctrine(d);
                for (ItemStack it : doc.exclusive()) {
                    Gt.check(!SpaceGoods.is(it), "doctrine " + d + " : objet de l'espace exclusif " + it);
                }
                for (ItemStack it : doc.scraps()) {
                    Gt.check(!SpaceGoods.is(it), "doctrine " + d + " : objet de l'espace achete " + it);
                }
            }
            Gt.check(seen > 100, "catalogues presque vides : " + seen);
            ServerPlayer player = s.player("gtspace", "Fermiere", new BlockPos(4, 2, 4));
            Villager farmer = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(6, 2, 4));
            farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));
            Villagers.identify(s.level, farmer);
            VillagerTrade trade = new VillagerTrade(s.level, farmer);
            var offers = trade.offers(player);
            Gt.check(!offers.isEmpty(), "le fermier ne vend plus rien");
            offers.forEach(o -> Gt.check(!SpaceGoods.is(o.stack()), "le fermier vend un objet de l'espace : " + o.stack()));
            trade.buyList(player).forEach(it -> Gt.check(!SpaceGoods.is(it), "le fermier achete un objet de l'espace : " + it));
        } finally {
            s.done();
        }
        h.succeed();
    }
}
