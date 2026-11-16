package fr.pioneers.gametest;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.farm.device.DeviceBlock;
import fr.pioneers.farm.device.DeviceSites;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.world.pet.Pet;
import fr.pioneers.world.pet.PetCat;
import fr.pioneers.world.pet.PetCore;
import fr.pioneers.world.pet.PetDog;
import fr.pioneers.world.pet.PetHomeBlock;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.pet.PiPets;
import fr.pioneers.world.season.SeasonData;
import fr.pioneers.world.season.SeasonEvents;
import fr.pioneers.logistics.warehouse.PiWarehouse;
import fr.pioneers.world.wild.Bear;
import fr.pioneers.world.wild.BirdHouseBlock;
import fr.pioneers.world.wild.GrayWolf;
import fr.pioneers.world.wild.PiWild7;
import fr.pioneers.world.wild.Rat;
import fr.pioneers.world.wild.RatTrapBlock;
import fr.pioneers.world.wild.Snake;
import fr.pioneers.world.wild.Wild7Rules;
import fr.pioneers.society.clan.Clan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 7 (GameTests ; specification « Le monde vivant » §4, §9, §11 n° 9, 12, 13, 14) : loup qui fuit un joueur qui tient une
 * torche ; loup qui chasse un mouton la nuit (pas le jour) et chien autonome qui le combat ; ours qui casse une ruche du mod (pas une ruche
 * vanilla, et pas dans un claim) ; rat qui vole dans un coffre ouvert mais pas dans une caisse d'entrepot ni un four ; reglage du vol coupe ->
 * aucun vol ; piege qui attrape un rat puis se vide et se rearme ; chat autonome qui chasse un rat ; nichoir qui donne des plumes (3 au plus
 * au sol) ; serpent qui mord (poison leger).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot7Tests {

    private PatchP105Lot7Tests() {
    }

    static <T extends Entity> T add(Gt.Scope s, EntityType<T> type, BlockPos rel) {
        BlockPos abs = s.h.absolutePos(rel);
        T e = type.create(s.level);
        Gt.check(e != null, "creation de " + type);
        BlockPos at = Gt.standing(s.level, abs);   // 1.0.6 : sur le sol du gabarit, pas dedans
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(e);
        s.onCleanup(() -> {
            if (!e.isRemoved()) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(e.getUUID());
        });
        return e;
    }

    static void keepTime(Gt.Scope s) {
        long offset = SeasonData.get(s.server.overworld()).offset(), time = s.level.getDayTime();
        s.onCleanup(() -> {
            s.level.setDayTime(time);
            SeasonEvents.setOffset(s.server, offset);
        });
    }

    static void grass(GameTestHelper h) {
        for (int x = 1; x <= 18; x++) {
            for (int z = 1; z <= 18; z++) {
                h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    // ------------------------------------------------------------------ 1. le loup fuit la torche

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_torch", timeoutTicks = 200)
    public static void p105_7_loup_fuit_la_torche(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        GrayWolf w = add(s, PiWild7.GRAY_WOLF.get(), new BlockPos(9, 1, 9));
        ServerPlayer p = s.player("wolft", "Lucien", new BlockPos(5, 1, 9));
        Gt.check(Wild7Rules.wolfAttacksPlayer(true, 4, true, false) && !Wild7Rules.wolfAttacksPlayer(true, 4, true, true)
                 && !Wild7Rules.wolfAttacksPlayer(true, 4, false, false) && !Wild7Rules.wolfAttacksPlayer(false, 8, true, false),
              "regles : nuit + isole, jamais s'il a peur, jamais un joueur accompagne, le jour seulement tout pres");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
        Gt.check(GrayWolf.holdsFire(p), "le joueur tient une torche (tag pioneers:wolf_repellents)");
        double start = w.distanceTo(p);
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(w.fear() != null && w.getTarget() == null, "le loup a peur du feu et n'attaque pas"))
              .thenWaitUntil(() -> Gt.check(w.distanceTo(p) > start + 1.5, "le loup s'eloigne de la torche"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 2. le loup chasse le mouton la nuit ; le chien le combat

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_pack", timeoutTicks = 300)
    public static void p105_7_loup_chasse_mouton_chien_defend(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        keepTime(s);
        s.level.setDayTime(18000);
        //deux joueurs ensemble : aucun n'est isole (le loup les laisse)
        ServerPlayer p = s.player("wolfa", "Basile", new BlockPos(2, 1, 2));
        ServerPlayer q = s.player("wolfb", "Rose", new BlockPos(2, 1, 3));
        Gt.check(!GrayWolf.isolated(p) && !GrayWolf.isolated(q), "joueurs accompagnes : pas isoles");
        Sheep sheep = add(s, EntityType.SHEEP, new BlockPos(10, 1, 14));
        GrayWolf w = add(s, PiWild7.GRAY_WOLF.get(), new BlockPos(10, 1, 6));
        Gt.check(GrayWolf.livestock(sheep) && !GrayWolf.livestock(w), "le mouton est du betail, pas le loup");
        Gt.check(Wild7Rules.wolfHuntsLivestock(true, 8, false) && !Wild7Rules.wolfHuntsLivestock(false, 8, false), "le betail : la nuit seulement");
        Gt.check(PetCore.predator(w), "le loup est un predateur du betail (tag pioneers:livestock_predators)");
        BlockPos kennel = h.absolutePos(new BlockPos(14, 1, 10));
        s.level.setBlock(kennel, PiPets.KENNELS.get(0).getBlock().defaultBlockState(), 3);
        PetDog dog = add(s, PiPets.DOGS.get(0).get(), new BlockPos(14, 1, 12));
        dog.tame(p);
        PetHomeBlock.link(dog, s.level, kennel, p);
        h.startSequence()
              //1.0.6 : le mouton vanilla devient l'entite de sa race au tick suivant (1.0.3-beta lot 8, BreedConversion), meme UUID
              .thenWaitUntil(() -> Gt.check(w.getTarget() != null && w.getTarget().getUUID().equals(sheep.getUUID()),
                    "la nuit, le loup chasse le mouton (cible " + w.getTarget() + ", nuit " + s.level.isNight() + ")"))
              .thenWaitUntil(() -> Gt.check(dog.getTarget() == w, "le chien autonome defend le betail contre le loup"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 3. l'ours casse une ruche du mod, pas une vanilla, pas dans un claim

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_bear", timeoutTicks = 60)
    public static void p105_7_ours_casse_ruche_du_mod(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        BlockPos apiary = h.absolutePos(new BlockPos(6, 1, 6)), beehive = h.absolutePos(new BlockPos(6, 1, 10));
        s.level.setBlock(apiary, PiFarmDevices.DEVICES.get(DeviceBlock.Kind.APIARY).get().defaultBlockState(), 3);
        s.level.setBlock(beehive, Blocks.BEEHIVE.defaultBlockState(), 3);
        Bear b = add(s, PiWild7.BEAR.get(), new BlockPos(7, 1, 8));
        Gt.check(Wild7Rules.bearBreaks(false, true) && !Wild7Rules.bearBreaks(true, true) && !Wild7Rules.bearBreaks(false, false),
              "regle : casse hors claim (et si les degats des creatures sont permis)");
        Gt.check(DeviceSites.near(s.level, b.getX(), b.getY(), b.getZ(), Wild7Rules.HIVE_RANGE, DeviceSites.Kind.APIARY).contains(apiary),
              "la ruche du mod est au registre des sites (l'ours la trouve)");
        Gt.check(!b.raid(beehive) && s.level.getBlockState(beehive).is(Blocks.BEEHIVE), "la ruche vanilla ne l'interesse pas");
        Gt.check(b.raid(apiary) && s.level.getBlockState(apiary).isAir(), "l'ours casse la ruche du mod");
        //dans un claim : il menace sans casser
        s.level.setBlock(apiary, PiFarmDevices.DEVICES.get(DeviceBlock.Kind.APIARY).get().defaultBlockState(), 3);
        ServerPlayer p = s.player("beara", "Armand", new BlockPos(2, 1, 2));
        Clan c = s.clan("Ruchers", p);
        s.claim(c, apiary);
        Gt.check(PiWild7.claimed(s.level, apiary), "ruche dans le claim");
        Gt.check(!b.raid(apiary) && !s.level.getBlockState(apiary).isAir(), "dans un claim, l'ours menace mais ne casse pas");
        s.done();
    }

    // ------------------------------------------------------------------ 4. le rat vole dans un coffre ouvert, pas dans une caisse ni un four

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_rat", timeoutTicks = 40)
    public static void p105_7_rat_vole_coffre_pas_caisse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        BlockPos chest = h.absolutePos(new BlockPos(5, 1, 5)), crate = h.absolutePos(new BlockPos(9, 1, 5)), furnace = h.absolutePos(new BlockPos(13, 1, 5));
        s.level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        s.level.setBlock(crate, PiWarehouse.CRATE_HATCH.getBlock().defaultBlockState(), 3);
        s.level.setBlock(furnace, Blocks.FURNACE.defaultBlockState(), 3);
        ((Container) s.level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        ((Container) s.level.getBlockEntity(chest)).setItem(3, new ItemStack(Items.BREAD, 5));
        ((Container) s.level.getBlockEntity(furnace)).setItem(0, new ItemStack(Items.BEEF, 3));
        Rat r1 = add(s, PiWild7.RAT.get(), new BlockPos(5, 1, 6)), r2 = add(s, PiWild7.RAT.get(), new BlockPos(9, 1, 6)),
              r3 = add(s, PiWild7.RAT.get(), new BlockPos(13, 1, 6));
        Gt.check(Rat.theftEnabled(), "vol des rats actif par defaut");
        Gt.check(Rat.robbable(s.level, chest) && !Rat.robbable(s.level, crate) && !Rat.robbable(s.level, furnace),
              "coffre ouvert : oui ; caisse d'entrepot, four : non");
        ItemStack got = r1.steal(chest);
        Gt.check(got.is(Items.BREAD) && got.getCount() == 1, "le rat prend UN objet, de la nourriture d'abord");
        Gt.equal(4, ((Container) s.level.getBlockEntity(chest)).getItem(3).getCount(), "un pain de moins dans le coffre");
        Gt.check(r1.loot().is(Items.BREAD), "il l'emporte");
        Gt.check(r2.steal(crate).isEmpty() && r3.steal(furnace).isEmpty(), "rien dans la caisse d'entrepot ni dans le four");
        Gt.equal(3, ((Container) s.level.getBlockEntity(furnace)).getItem(0).getCount(), "le four est intact");
        r1.kill();
        //le pain vanilla qui tombe au sol devient le pain du mod (fr.pioneers.core.vanilla.VanillaEvents.onJoin, vanilla_replacements.json)
        net.minecraft.world.item.Item bread = fr.pioneers.core.vanilla.VanillaReplacements.convert(new ItemStack(Items.BREAD)).getItem();
        Gt.check(!s.level.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(4), e -> e.getItem().is(bread)).isEmpty(),
              "tue, il lache son butin (vivant " + r1.isAlive() + ", butin " + r1.loot() + ", en " + r1.blockPosition() + ", objets au sol "
              + s.level.getEntitiesOfClass(ItemEntity.class, new AABB(chest).inflate(8)).stream().map(e -> e.getItem().toString()).toList() + ")");
        s.done();
    }

    // ------------------------------------------------------------------ 5. reglage coupe : aucun vol

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_ratoff", timeoutTicks = 40)
    public static void p105_7_vol_coupe_par_reglage(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        boolean before = PiCommonConfig.get(PiCommonConfig.FAUNA_RAT_THEFT, true);
        s.onCleanup(() -> PiCommonConfig.FAUNA_RAT_THEFT.set(before));
        PiCommonConfig.FAUNA_RAT_THEFT.set(false);
        BlockPos barrel = h.absolutePos(new BlockPos(5, 1, 5));
        s.level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 3);
        ((Container) s.level.getBlockEntity(barrel)).setItem(0, new ItemStack(Items.APPLE, 8));
        Rat r = add(s, PiWild7.RAT.get(), new BlockPos(5, 1, 6));
        Gt.check(!Rat.theftEnabled() && !Rat.robbable(s.level, barrel) && !Wild7Rules.ratMayRob(false, true, false, false), "vol coupe par le reglage");
        Gt.check(r.steal(barrel).isEmpty(), "aucun vol");
        Gt.equal(8, ((Container) s.level.getBlockEntity(barrel)).getItem(0).getCount(), "le tonneau est intact");
        PiCommonConfig.FAUNA_RAT_THEFT.set(true);
        Gt.check(Rat.robbable(s.level, barrel), "reglage remis : le tonneau ouvert peut etre vole");
        s.done();
    }

    // ------------------------------------------------------------------ 6. le piege attrape un rat, puis se vide et se rearme

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_trap", timeoutTicks = 40)
    public static void p105_7_piege_attrape_un_rat(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        BlockPos trap = h.absolutePos(new BlockPos(8, 1, 8));
        BlockState empty = PiWild7.RAT_TRAP.getBlock().defaultBlockState();
        s.level.setBlock(trap, empty, 3);
        ServerPlayer p = s.player("trapa", "Firmin", new BlockPos(2, 1, 2));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(trap), Direction.UP, trap, false);
        s.level.getBlockState(trap).use(s.level, p, InteractionHand.MAIN_HAND, hit);
        Gt.equal(RatTrapBlock.Trap.SEEDS, s.level.getBlockState(trap).getValue(RatTrapBlock.TRAP), "arme aux graines");
        Gt.equal(2, p.getMainHandItem().getCount(), "une graine d'appat");
        Rat a = add(s, PiWild7.RAT.get(), new BlockPos(8, 1, 8)), b = add(s, PiWild7.RAT.get(), new BlockPos(11, 1, 8));
        Gt.check(RatTrapBlock.work(s.level, trap, s.level.getBlockState(trap)), "le piege attrape le rat sur l'appat");
        Gt.check(!a.isAlive(), "le rat pris est mort");
        Gt.equal(RatTrapBlock.Trap.CAUGHT, s.level.getBlockState(trap).getValue(RatTrapBlock.TRAP), "piege pris");
        b.moveTo(trap.getX() + 0.5, trap.getY(), trap.getZ() + 0.5);
        Gt.check(!RatTrapBlock.work(s.level, trap, s.level.getBlockState(trap)) && b.isAlive(), "un seul rat par appat : le second survit");
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        s.level.getBlockState(trap).use(s.level, p, InteractionHand.MAIN_HAND, hit);
        Gt.equal(RatTrapBlock.Trap.EMPTY, s.level.getBlockState(trap).getValue(RatTrapBlock.TRAP), "vide au clic droit");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS));
        s.level.getBlockState(trap).use(s.level, p, InteractionHand.MAIN_HAND, hit);
        Gt.check(s.level.getBlockState(trap).getValue(RatTrapBlock.TRAP).armed(), "rearme");
        s.done();
    }

    // ------------------------------------------------------------------ 7. le chat chasse le rat

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_cat", timeoutTicks = 200)
    public static void p105_7_chat_chasse_le_rat(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        ServerPlayer p = s.player("catr", "Margot", new BlockPos(2, 1, 2));
        BlockPos basket = h.absolutePos(new BlockPos(10, 1, 10));
        s.level.setBlock(basket, PiPets.BASKETS.get(0).getBlock().defaultBlockState(), 3);
        PetCat cat = add(s, PiPets.CATS.get(0).get(), new BlockPos(9, 1, 9));
        cat.tame(p);
        PetHomeBlock.link((Pet) cat, s.level, basket, p);
        Rat r = add(s, PiWild7.RAT.get(), new BlockPos(13, 1, 13));
        Gt.check(r.getType().is(PiPets.CAT_PREY), "le rat est une proie du chat (tag pioneers:cat_prey)");
        Gt.check(!PetCore.game(r), "le rat n'est pas un gibier pour le chien de chasse");
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(cat.getTarget() == r || !r.isAlive(), "le chat chasse le rat"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 8. le nichoir donne des plumes

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_house", timeoutTicks = 40)
    public static void p105_7_nichoir_donne_des_plumes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        BlockPos post = h.absolutePos(new BlockPos(8, 1, 8)), house = post.above();
        s.level.setBlock(post, Blocks.OAK_FENCE.defaultBlockState(), 3);
        BlockState st = PiWild7.BIRD_HOUSES.get(0).getBlock().defaultBlockState().setValue(BirdHouseBlock.POST, true);
        Gt.check(st.canSurvive(s.level, house), "le nichoir tient sur un poteau");
        s.level.setBlock(house, st, 3);
        Gt.check(!BirdHouseBlock.feather(s.level, house, Wild7Rules.FEATHER_ODDS - 1), "pas de plume a chaque fois");
        int n = 0;
        for (int i = 0; i < 6; i++) {
            if (BirdHouseBlock.feather(s.level, house, 0)) {
                n++;
            }
        }
        Gt.equal(Wild7Rules.FEATHER_MAX, n, "des plumes tombent, 3 au plus au sol");
        Gt.check(!s.level.getEntitiesOfClass(ItemEntity.class, new AABB(house).inflate(2, 3, 2), e -> e.getItem().is(Items.FEATHER)).isEmpty(),
              "plumes au pied du nichoir");
        BlockPos wall = h.absolutePos(new BlockPos(4, 1, 8));
        s.level.setBlock(wall, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        BlockState onWall = PiWild7.BIRD_HOUSES.get(0).getBlock().defaultBlockState().setValue(BirdHouseBlock.FACING, Direction.EAST);
        Gt.check(onWall.canSurvive(s.level, wall.east()), "le nichoir tient contre un mur");
        s.done();
    }

    // ------------------------------------------------------------------ 9. le serpent mord : poison leger

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_7_snake", timeoutTicks = 120)
    public static void p105_7_serpent_mord_poison_leger(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        s.notPeaceful();
        //1.0.6 : joueur debout sur le sol du gabarit (y = 2) ; un joueur qui arrive est invulnerable 60 ticks (ServerPlayer.spawnInvulnerableTime) :
        //la morsure etait toujours refusee. Le serpent est pose une fois cette protection passee.
        ServerPlayer p = s.player("snake", "Hector", new BlockPos(8, 2, 9));
        Gt.check(Wild7Rules.snakeBites(1.5, false) && !Wild7Rules.snakeBites(3, false) && Wild7Rules.snakeBites(5, true), "regle : moins de 2 blocs ou blesse");
        h.runAfterDelay(61, () -> {
            Snake sn = add(s, PiWild7.SNAKE.get(), new BlockPos(8, 1, 8));
            Gt.check(sn.provoke() == p, "le joueur trop pres est vise");
            Gt.check(sn.doHurtTarget(p), "morsure (vie " + p.getHealth() + ", mode " + p.gameMode.getGameModeForPlayer() + ")");
            Gt.check(p.hasEffect(MobEffects.POISON) && p.getEffect(MobEffects.POISON).getAmplifier() == 0, "poison leger (niveau I)");
            Gt.check(sn.getTarget() == null, "apres la morsure, il se calme");
            s.done();
        });
    }
}
