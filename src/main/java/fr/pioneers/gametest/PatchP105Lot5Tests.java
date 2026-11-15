package fr.pioneers.gametest;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.fish.PiFish;
import fr.pioneers.world.pet.CatBasketTile;
import fr.pioneers.world.pet.GeneratedPets;
import fr.pioneers.world.pet.Pet;
import fr.pioneers.world.pet.PetCat;
import fr.pioneers.world.pet.PetConversion;
import fr.pioneers.world.pet.PetCore;
import fr.pioneers.world.pet.PetDog;
import fr.pioneers.world.pet.PetGoals;
import fr.pioneers.world.pet.PetHomeBlock;
import fr.pioneers.world.pet.PetMaster;
import fr.pioneers.world.pet.PetMode;
import fr.pioneers.world.pet.PetRecall;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.pet.PetRules;
import fr.pioneers.world.pet.PiPets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 5 (GameTests ; specification « Le monde vivant » §5, §8, decisions 6 a 9) : chien sauvage apprivoise -> chien de
 * race (proprietaire garde) ; nom a l'etiquette -> maitre, autre joueur refuse, adoption ; protection contre les autres joueurs ; liaison au
 * panier, mode Autonome, territoire de 30 blocs ; rappel d'un animal d'une autre dimension et d'un animal dont les entites arrivent apres
 * coup (troncon pas encore charge) ; chat qui attrape un vrai poisson et le rapporte dans son panier ; migration d'un loup apprivoise et
 * nomme (maitre, collier, nom, assis gardes).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot5Tests {

    private PatchP105Lot5Tests() {
    }

    static <T extends Entity> T add(Gt.Scope s, EntityType<T> type, BlockPos abs) {
        T e = type.create(s.level);
        Gt.check(e != null, "creation de " + type);
        e.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(e);
        s.onCleanup(() -> {
            if (!e.isRemoved()) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(e.getUUID());
        });
        return e;
    }

    static void name(ServerPlayer p, Entity e, String name) {
        ItemStack tag = new ItemStack(Items.NAME_TAG);
        tag.setHoverName(Component.literal(name));
        p.setItemInHand(InteractionHand.MAIN_HAND, tag);
        p.interactOn(e, InteractionHand.MAIN_HAND);
    }

    // ------------------------------------------------------------------ 1. chien sauvage -> race

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_tame", timeoutTicks = 40)
    public static void p105_5_chien_sauvage_apprivoise_race(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("pet", "Mireille", new BlockPos(3, 1, 3));
        Wolf w = add(s, EntityType.WOLF, h.absolutePos(new BlockPos(6, 1, 6)));
        Gt.check(!(w instanceof Pet), "le loup vanilla reste un chien sauvage tant qu'il n'est pas apprivoise");
        Gt.check(!PetConversion.needs(w), "chien sauvage : pas de conversion");
        w.tame(p);
        Gt.check(PetConversion.needs(w), "apprivoise : a convertir");
        UUID id = w.getUUID();
        Entity ne = PetConversion.convert(s.level, w);
        Gt.check(ne instanceof PetDog, "devenu un chien de race : " + ne);
        PetDog d = (PetDog) ne;
        s.onCleanup(d::discard);
        Gt.equal(id, d.getUUID(), "meme UUID");
        Gt.check(d.isTame() && p.getUUID().equals(d.getOwnerUUID()), "proprietaire garde");
        Gt.check(d.breed() >= 0 && d.breed() < GeneratedPets.DOG_BREEDS && !GeneratedPets.DOGS[d.breed()].wild(), "race tiree parmi les 8");
        Gt.check(PetMaster.master(d) == null, "sans nom : pas de maitre");
        s.done();
    }

    // ------------------------------------------------------------------ 2. maitre par etiquette, adoption, protection

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_master", timeoutTicks = 40)
    public static void p105_5_maitre_etiquette_adoption(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer a = s.player("peta", "Arsene", new BlockPos(3, 1, 3)), b = s.player("petb", "Berthe", new BlockPos(5, 1, 3));
        PetCat cat = add(s, PiPets.CATS.get(1).get(), h.absolutePos(new BlockPos(4, 1, 5)));
        cat.tame(a);
        name(b, cat, "Voleur");
        Gt.check(!cat.hasCustomName(), "un autre joueur ne peut pas nommer un animal apprivoise par un autre");
        name(a, cat, "Minou");
        Gt.equal("Minou", cat.getCustomName() == null ? null : cat.getCustomName().getString(), "nom pose par l'etiquette");
        Gt.equal(a.getUUID(), PetMaster.master(cat), "le joueur qui l'a nomme est le maitre");
        Gt.check(PetRegistry.get(s.server).list(a.getUUID()).stream().anyMatch(e -> e.id().equals(cat.getUUID())), "inscrit au registre du maitre");
        //protection : un autre joueur ne le blesse pas
        boolean hurt = ForgeHooks.onLivingAttack(cat, s.level.damageSources().playerAttack(b), 2F);
        Gt.check(!hurt, "un autre joueur ne peut pas blesser un animal qui a un maitre");
        name(b, cat, "Pas a toi");
        Gt.equal(a.getUUID(), PetMaster.master(cat), "toujours le meme maitre");
        //adoption
        PetMaster.setAdoptable(cat, true);
        name(b, cat, "Felix");
        Gt.equal(b.getUUID(), PetMaster.master(cat), "adopte : nouveau maitre");
        Gt.check(!PetMaster.adoptable(cat), "la case d'adoption se decoche");
        Gt.equal(b.getUUID(), cat.getOwnerUUID(), "il obeit a son nouveau maitre");
        s.done();
    }

    // ------------------------------------------------------------------ 3. panier, autonome, territoire

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_home", timeoutTicks = 40)
    public static void p105_5_panier_territoire(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("petc", "Colette", new BlockPos(3, 1, 3));
        BlockPos basket = h.absolutePos(new BlockPos(10, 1, 10));
        s.level.setBlock(basket, PiPets.BASKETS.get(0).getBlock().defaultBlockState(), 3);
        PetCat cat = add(s, PiPets.CATS.get(0).get(), h.absolutePos(new BlockPos(8, 1, 8)));
        cat.tame(p);
        cat.setLeashedTo(p, true);
        Pet led = PetHomeBlock.led(s.level, p, true);
        Gt.check(led == cat, "le chat en laisse est trouve");
        PetHomeBlock.link(led, s.level, basket, p);
        Gt.equal(PetMode.AUTONOMOUS, cat.mode(), "lie : mode Autonome");
        Gt.check(cat.home() != null && cat.home().pos().equals(basket), "panier lie");
        Gt.check(!cat.isLeashed(), "laisse rendue");
        int r = PetCore.radius();
        Gt.equal(PetRules.TERRITORY, r, "territoire de 30 blocs par defaut");
        cat.moveTo(basket.getX() + 0.5 + r - 2, basket.getY(), basket.getZ() + 0.5);
        Gt.check(!PetGoals.wantsHome(cat), "dans le territoire : il y reste libre");
        cat.moveTo(basket.getX() + 0.5 + r + 3, basket.getY(), basket.getZ() + 0.5);
        Gt.check(PetGoals.wantsHome(cat), "hors du territoire : il revient");
        //panier casse : delie
        s.level.setBlock(basket, Blocks.AIR.defaultBlockState(), 3);
        cat.moveTo(basket.getX() + 2.5, basket.getY(), basket.getZ() + 0.5);
        PetCore.checkHomeNow(cat, s.level);
        Gt.check(cat.home() == null && cat.mode() == PetMode.FOLLOW, "panier casse : delie, il suit");
        s.done();
    }

    // ------------------------------------------------------------------ 4. rappel (autre dimension, troncon pas charge)

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_recall_dim", timeoutTicks = 60)
    public static void p105_5_rappel_autre_dimension(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("petd", "Desire", new BlockPos(9, 1, 9));
        ServerLevel nether = s.server.getLevel(Level.NETHER);
        Gt.check(nether != null, "Nether present");
        BlockPos far = new BlockPos(4000, 70, 4000);
        nether.getChunk(far);
        nether.setBlock(far.below(), Blocks.NETHERRACK.defaultBlockState(), 3);
        PetDog d = PiPets.DOGS.get(3).get().create(nether);
        Gt.check(d != null, "chien cree");
        d.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0, 0);
        nether.addFreshEntity(d);
        UUID id = d.getUUID();
        s.onCleanup(() -> {
            Entity e = PetRecall.find(s.server, id);
            if (e != null) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(id);
        });
        d.tame(p);
        name(p, d, "Rex");
        Gt.equal(p.getUUID(), PetMaster.master(d), "maitre par l'etiquette (a distance, comme apres un voyage)");
        PetRecall.request(p, null);
        Entity back = PetRecall.find(s.server, id);
        Gt.check(back != null && back.level() == s.level, "rappele dans la dimension du joueur : " + (back == null ? null : back.level().dimension()));
        Gt.check(back.distanceTo(p) < 6, "pres du joueur : " + back.distanceTo(p));
        Gt.equal(PetMode.FOLLOW, ((Pet) back).mode(), "rappele : mode Suivre");
        s.done();
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_recall_load", timeoutTicks = 120)
    public static void p105_5_rappel_troncon_non_charge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("pete", "Edmond", new BlockPos(9, 1, 9));
        //un chien inscrit au registre du maitre, dont les entites n'existent pas encore (troncon pas charge)
        BlockPos far = h.absolutePos(new BlockPos(9, 1, 9)).offset(3000, 0, 3000);
        PetDog d = PiPets.DOGS.get(7).get().create(s.level);
        Gt.check(d != null, "chien cree");
        d.tame(p);
        d.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 0, 0);
        d.setCustomName(Component.literal("Saucisse"));
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putUUID("master", p.getUUID());
        t.putString("master_name", p.getGameProfile().getName());
        t.putBoolean("init", true);
        d.getPersistentData().put(PetMaster.KEY, t);
        PetRegistry.get(s.server).update(d);
        UUID id = d.getUUID();
        s.onCleanup(() -> {
            Entity e = PetRecall.find(s.server, id);
            if (e != null) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(id);
        });
        Gt.check(PetRecall.find(s.server, id) == null, "pas encore charge");
        PetRecall.request(p, id);
        //le troncon est charge par le ticket ; les entites arrivent quelques ticks plus tard (chargement asynchrone en jeu)
        h.runAfterDelay(10, () -> {
            s.level.getChunk(far);
            s.level.addFreshEntity(d);
        });
        h.succeedWhen(() -> {
            Entity e = PetRecall.find(s.server, id);
            Gt.check(e != null && e.distanceTo(p) < 6, "le chien est arrive pres du joueur");
            s.done();
        });
    }

    // ------------------------------------------------------------------ 5. peche du chat

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_fish", timeoutTicks = 40)
    public static void p105_5_chat_rapporte_poisson(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("petf", "Fanny", new BlockPos(3, 1, 3));
        BlockPos basket = h.absolutePos(new BlockPos(4, 1, 10));
        s.level.setBlock(basket, PiPets.BASKETS.get(2).getBlock().defaultBlockState(), 3);
        BlockPos water = h.absolutePos(new BlockPos(12, 0, 10));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                s.level.setBlock(water.offset(dx, 0, dz), Blocks.WATER.defaultBlockState(), 3);
            }
        }
        PiFish fish = PiFishing.create(s.level, 0, 1F);
        Gt.check(fish != null, "poisson cree");
        fish.moveTo(water.getX() + 0.5, water.getY() + 0.2, water.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(fish);
        s.onCleanup(() -> {
            if (!fish.isRemoved()) {
                fish.discard();
            }
        });
        PetCat cat = add(s, PiPets.CATS.get(5).get(), h.absolutePos(new BlockPos(10, 1, 10)));
        cat.tame(p);
        PetHomeBlock.link(cat, s.level, basket, p);
        ItemStack caught = PetGoals.catchFish(cat, water, true);
        Gt.check(!caught.isEmpty(), "le chat attrape un poisson");
        Gt.check(fish.isRemoved(), "c'est le vrai poisson du coin (population des poissons respectee)");
        PetGoals.deposit(cat, basket, caught);
        Gt.check(s.level.getBlockEntity(basket) instanceof CatBasketTile t && !t.empty(), "le poisson est dans le panier");
        Gt.check(s.level.getBlockEntity(basket) instanceof CatBasketTile t2 && t2.comparator() > 0, "comparateur du panier");
        s.done();
    }

    // ------------------------------------------------------------------ 6. migration d'un loup apprivoise et nomme

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_5_migrate", timeoutTicks = 40)
    public static void p105_5_migration_loup_apprivoise(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("petg", "Gaston", new BlockPos(3, 1, 3));
        //loup d'un monde d'avant : apprivoise, nomme, collier bleu, assis ; il entre dans le monde (evenement d'arrivee vanilla)
        Wolf w = EntityType.WOLF.create(s.level);
        Gt.check(w != null, "loup cree");
        w.tame(p);
        w.setCustomName(Component.literal("Medor"));
        w.setCollarColor(DyeColor.BLUE);
        w.setOrderedToSit(true);
        BlockPos at = h.absolutePos(new BlockPos(7, 1, 7));
        w.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(w);
        UUID id = w.getUUID();
        s.onCleanup(() -> {
            Entity e = s.level.getEntity(id);
            if (e != null) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(id);
        });
        Gt.equal(p.getUUID(), PetMaster.master(w), "apprivoise et deja nomme : son proprietaire en est le maitre");
        Entity ne = PetConversion.convert(s.level, w);
        Gt.check(ne instanceof PetDog, "devenu un chien de race");
        PetDog d = (PetDog) ne;
        Gt.equal(id, d.getUUID(), "meme UUID");
        Gt.equal("Medor", d.getCustomName() == null ? null : d.getCustomName().getString(), "nom garde");
        Gt.equal(DyeColor.BLUE, d.getCollarColor(), "collier garde");
        Gt.equal(p.getUUID(), PetMaster.master(d), "maitre garde");
        Gt.check(d.isOrderedToSit() && d.mode() == PetMode.SIT, "assis garde (mode Assis)");
        s.done();
    }

}
