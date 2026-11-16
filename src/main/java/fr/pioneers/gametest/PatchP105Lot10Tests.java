package fr.pioneers.gametest;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.world.fauna.FaunaCounter;
import fr.pioneers.world.fauna.FaunaGroup;
import fr.pioneers.world.fauna.FaunaRules;
import fr.pioneers.world.fauna.FaunaSpawner;
import fr.pioneers.world.hunt.Firearms;
import fr.pioneers.world.hunt.GunRackBlock;
import fr.pioneers.world.hunt.GunRackTile;
import fr.pioneers.world.hunt.HarpoonEntity;
import fr.pioneers.world.hunt.HarpoonItem;
import fr.pioneers.world.hunt.HerdData;
import fr.pioneers.world.hunt.HerdEvents;
import fr.pioneers.world.hunt.HuntRules;
import fr.pioneers.world.hunt.HuntSettings;
import fr.pioneers.world.hunt.HuntSkill;
import fr.pioneers.world.hunt.MusketItem;
import fr.pioneers.world.hunt.PiHunt;
import fr.pioneers.world.pet.HuntEvents;
import fr.pioneers.world.pet.PetDog;
import fr.pioneers.world.pet.PetHomeBlock;
import fr.pioneers.world.pet.PetMode;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.pet.PiPets;
import fr.pioneers.world.wild.Bear;
import fr.pioneers.world.wild.Deer;
import fr.pioneers.world.wild.PiWild;
import fr.pioneers.world.wild.PiWild7;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 10 (GameTests ; specification « Le monde vivant » §5.4, §6) : troupeau qui apparait hors claim et se disperse sans
 * approche ; chien autonome qui grogne et met le bouton Chasse en avant ; mousquet qui exige balle + poudre et tire ; degats contre les
 * joueurs divises par 2 ; tir refuse sous l'eau ; harpon qui accroche une bete et se recupere ; coup de feu qui fait fuir le gibier ;
 * porte-fusil qui garde 3 armes ; trophee au butin ; XP de Chasse gagnee.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot10Tests {

    private PatchP105Lot10Tests() {
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

    static void grass(GameTestHelper h, int n) {
        for (int x = 1; x <= n; x++) {
            for (int z = 1; z <= n; z++) {
                //1.0.6 : sur le sol du gabarit (y = 1) : en y = 0 l'herbe etait sous la pierre, et le troupeau naissait sur la pierre
                h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 1, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    static BlockPos top(ServerLevel level, BlockPos c) {
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, c);
    }

    static FaunaSpawner.Limits noViewer() {
        return new FaunaSpawner.Limits(List.of(), 0, -1, true, 0);
    }

    static void forget(Gt.Scope s, HerdData.Herd herd) {
        s.onCleanup(() -> {
            for (UUID u : herd.members) {
                Entity e = s.level.getEntity(u);
                if (e != null) {
                    e.discard();
                }
            }
            HerdData.get(s.server).remove(herd.id);
        });
    }

    // ------------------------------------------------------------------ 1. troupeau hors claim, non compte, dispersion sans approche

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_10_herd", timeoutTicks = 100)
    public static void p105_10_troupeau_hors_claim_et_dispersion(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        grass(h, 30);
        Gt.check(HuntRules.nextDelay(1, 2, 0) == HuntRules.DAY && HuntRules.nextDelay(1, 2, 0.999) <= 2 * HuntRules.DAY
                 && HuntRules.size(4, 10, 0) == 4 && HuntRules.size(4, 10, 0.9999) == 10, "regles : 1 a 2 jours, 4 a 10 betes");
        BlockPos c = h.absolutePos(new BlockPos(16, 1, 16));
        long zone = FaunaRules.zoneKey(c.getX(), c.getZ());
        FaunaCounter.of(level).reconcile();
        int before = FaunaCounter.of(level).groups(zone)[FaunaGroup.GAME.ordinal()];
        //dans un claim : refuse
        String dim = ClanApi.dim(level);
        long chunk = ChunkPos.asLong(c.getX() >> 4, c.getZ() >> 4);
        boolean mine = ClaimIndex.owner(dim, chunk) == null;
        if (mine) {
            ClaimIndex.set(dim, chunk, UUID.nameUUIDFromBytes("p105_10_herd".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            s.onCleanup(() -> ClaimIndex.set(dim, chunk, null));
        }
        UUID who = UUID.randomUUID();
        Gt.check(HerdEvents.spawnAt(level, who, c.getX(), c.getZ(), "pioneers:deer", HuntSettings.DEFAULT, noViewer(), 0) == null,
              "dans un claim : aucun troupeau");
        if (mine) {
            ClaimIndex.set(dim, chunk, null);
        }
        //1.0.6 : la regle d'apparition des animaux lit la lumiere du ciel, calculee aux ticks suivants sur un sol tout juste pose : on l'attend
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, c);
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, top) >= 15, "lumiere du ciel pas encore calculee"))
              .thenExecute(() -> herdOutsideClaim(s, level, who, c, zone, before));
    }

    private static void herdOutsideClaim(Gt.Scope s, ServerLevel level, UUID who, BlockPos c, long zone, int before) {
        HerdData.Herd herd = HerdEvents.spawnAt(level, who, c.getX(), c.getZ(), "pioneers:deer", HuntSettings.DEFAULT, noViewer(), 0);
        Gt.check(herd != null && !herd.members.isEmpty(), "hors claim : un troupeau de cerfs apparait (sol " + level.getBlockState(top(level, c).below())
              + ", lumiere " + level.getRawBrightness(top(level, c), 0) + ", heure " + level.getDayTime() % 24000 + ")");
        forget(s, herd);
        List<Entity> beasts = new ArrayList<>();
        for (UUID u : herd.members) {
            Entity e = level.getEntity(u);
            Gt.check(e != null && FaunaCounter.herd(e), "chaque bete porte la marque du troupeau");
            beasts.add(e);
        }
        FaunaCounter.of(level).reconcile();
        Gt.equal(before, FaunaCounter.of(level).groups(zone)[FaunaGroup.GAME.ordinal()], "les betes du troupeau ne comptent pas (repeuplement)");
        //personne ne s'en approche : un jour plus tard, il se disperse, puis disparait
        HerdEvents.update(level, herd.start + HuntRules.HERD_LIFE + 1);
        Gt.check(herd.disperse >= 0, "un jour sans approche : dispersion");
        HerdEvents.update(level, herd.disperse + HuntRules.DISPERSE_TICKS + 1);
        Gt.check(beasts.stream().allMatch(Entity::isRemoved), "les betes disparaissent");
        Gt.check(HerdData.get(s.server).herd(herd.id) == null, "le troupeau est fini");
        s.done();
    }

    // ------------------------------------------------------------------ 2. le chien autonome grogne, Chasse mis en avant

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_10_dog", timeoutTicks = 200)
    public static void p105_10_chien_grogne_chasse_en_avant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h, 30);
        ServerPlayer p = s.player("hunta", "Diane", new BlockPos(4, 1, 4));
        BlockPos kennel = h.absolutePos(new BlockPos(6, 1, 10));
        s.level.setBlock(kennel, PiPets.KENNELS.get(0).getBlock().defaultBlockState(), 3);
        PetDog dog = add(s, PiPets.DOGS.get(1).get(), new BlockPos(6, 1, 12));
        dog.tame(p);
        PetHomeBlock.link(dog, s.level, kennel, p);
        Gt.equal(PetMode.AUTONOMOUS, dog.mode(), "chien lie a sa niche : autonome");
        Gt.check(!HuntEvents.highlighted(dog, p), "sans troupeau : Chasse n'est pas mis en avant");
        BlockPos c = h.absolutePos(new BlockPos(26, 1, 26));
        HerdData.Herd herd = HerdEvents.spawnAt(s.level, p.getUUID(), c.getX(), c.getZ(), "pioneers:deer", HuntSettings.DEFAULT, noViewer(), 0);
        Gt.check(herd != null, "troupeau pose");
        forget(s, herd);
        Gt.check(HuntEvents.highlighted(dog, p), "troupeau a portee : bouton Chasse mis en avant");
        Gt.check(HuntEvents.herdNearby(dog), "le chien le sent");
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(dog.brain().herdAlertUntil > s.level.getGameTime(), "le chien grogne (alerte)"))
              .thenExecute(() -> {
                  dog.setMode(PetMode.HUNT);
                  Gt.check(HuntEvents.trailOf(dog, 64) != null, "en mode Chasse : il trouve la piste du troupeau");
                  HerdEvents.update(s.level, herd.start + HuntRules.HERD_LIFE + 1);
                  Gt.equal(PetMode.AUTONOMOUS, dog.mode(), "fin du troupeau : retour en autonome");
              })
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 3. mousquet : balle + poudre, puis tir

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_musket", timeoutTicks = 60)
    public static void p105_10_mousquet_balle_poudre_tir(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("huntb", "Hubert", new BlockPos(3, 1, 10));
        p.getInventory().clearContent();
        Gt.check(!MusketItem.hasCharge(p) && !MusketItem.load(p), "sans munitions : impossible de charger");
        p.getInventory().add(new ItemStack(PiHunt.LEAD_BALL.get(), 1));
        Gt.check(!MusketItem.hasCharge(p), "une balle sans poudre : impossible");
        p.getInventory().add(new ItemStack(Items.GUNPOWDER, 1));
        Gt.check(MusketItem.hasCharge(p) && MusketItem.load(p), "balle + poudre : charge");
        Gt.check(!p.getInventory().contains(new ItemStack(PiHunt.LEAD_BALL.get())) && !p.getInventory().contains(new ItemStack(Items.GUNPOWDER)),
              "balle et poudre consommees");
        Gt.equal(HuntRules.MUSKET_RELOAD, MusketItem.reloadTicks(null), "chargement de 3 s");
        Deer d = add(s, PiWild.DEER.get(), new BlockPos(14, 1, 10));
        d.setNoAi(true);
        float hp = d.getHealth();
        Vec3 eye = p.getEyePosition();
        Vec3 dir = d.position().add(0, d.getBbHeight() * 0.5, 0).subtract(eye).normalize();
        Firearms.Shot shot = Firearms.ray(s.level, p, eye, dir, HuntRules.MUSKET_DAMAGE, HuntRules.MUSKET_RANGE);
        Gt.check(shot.hit() == d && d.getHealth() < hp, "le coup touche le cerf");
        s.done();
    }

    // ------------------------------------------------------------------ 4. degats contre un joueur divises par 2

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_pvp", timeoutTicks = 40)
    public static void p105_10_degats_pvp_divises(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.onCleanup(() -> HuntSettings.forced = null);
        HuntSettings.forced = HuntSettings.DEFAULT;
        Gt.check(Math.abs(HuntRules.pvp(20F, HuntRules.PVP_FACTOR) - 10F) < 1e-4, "regle : x 0,5 par defaut");
        ServerPlayer a = s.player("huntc", "Gaspard", new BlockPos(3, 1, 10)), b = s.player("huntd", "Marius", new BlockPos(12, 1, 10));
        Vec3 eye = a.getEyePosition();
        Vec3 dir = b.position().add(0, 1.0, 0).subtract(eye).normalize();
        Firearms.Shot shot = Firearms.ray(s.level, a, eye, dir, 12F, 64);
        Gt.check(shot.hit() == b, "le tir touche le joueur");
        if (s.server.isPvpAllowed()) {
            Gt.check(Math.abs(shot.damage() - 6F) < 1e-3, "degats x 0,5 contre un joueur : " + shot.damage());
        }
        s.done();
    }

    // ------------------------------------------------------------------ 5. tir refuse sous l'eau

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_wet", timeoutTicks = 40)
    public static void p105_10_tir_refuse_sous_l_eau(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (int y = 1; y <= 3; y++) {
            for (int x = 8; x <= 10; x++) {
                for (int z = 8; z <= 10; z++) {
                    s.level.setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        ServerPlayer p = s.player("hunte", "Ondine", new BlockPos(9, 1, 9));
        ItemStack musket = new ItemStack(PiHunt.MUSKET.get());
        MusketItem.setLoaded(musket, true);
        p.setItemInHand(InteractionHand.MAIN_HAND, musket);
        p.baseTick();
        Gt.check(Firearms.wet(p), "la tete du joueur est sous l'eau");
        Gt.check(!p.getItemInHand(InteractionHand.MAIN_HAND).use(s.level, p, InteractionHand.MAIN_HAND).getResult().consumesAction(),
              "sous l'eau : le mousquet ne tire pas");
        Gt.check(MusketItem.loaded(p.getItemInHand(InteractionHand.MAIN_HAND)), "il reste charge");
        s.done();
    }

    // ------------------------------------------------------------------ 6. harpon : accroche, puis se recupere

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_harpoon", timeoutTicks = 200)
    public static void p105_10_harpon_accroche_et_revient(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("huntf", "Achab", new BlockPos(3, 1, 10));
        Deer d = add(s, PiWild.DEER.get(), new BlockPos(9, 1, 10));
        d.setNoAi(true);
        d.setHealth(d.getMaxHealth());
        d.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        d.setHealth(200);
        Vec3 to = d.position().add(0, d.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
        p.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        p.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)))));
        ItemStack stack = new ItemStack(PiHunt.HARPOON.get());
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        HarpoonEntity hp = HarpoonItem.launch(s.level, p);
        stack.getOrCreateTag().putUUID(HarpoonItem.THROWN, hp.getUUID());
        s.onCleanup(hp::discard);
        Gt.check(HarpoonEntity.marine(PiWild7.BEAR.get().create(s.level)) == false && HuntRules.harpoonDamage(true) > HuntRules.harpoonDamage(false),
              "regle : degats x 2,5 contre la faune marine seulement");
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(hp.attached() == d, "le harpon s'accroche au cerf"))
              .thenExecute(() -> Gt.check(d.getHealth() < 200, "le cerf est blesse"))
              .thenExecute(() -> p.getItemInHand(InteractionHand.MAIN_HAND).use(s.level, p, InteractionHand.MAIN_HAND))
              .thenWaitUntil(() -> Gt.check(hp.isRemoved() && HarpoonItem.thrown(p.getItemInHand(InteractionHand.MAIN_HAND)) == null,
                    "corde ramenee : le harpon revient dans la main"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 7. un coup de feu fait fuir le gibier

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_scare", timeoutTicks = 40)
    public static void p105_10_coup_de_feu_fait_fuir(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("huntg", "Lucien", new BlockPos(3, 1, 10));
        Deer d = add(s, PiWild.DEER.get(), new BlockPos(16, 1, 10));
        Gt.check(!d.alarmed(), "cerf calme");
        int n = Firearms.scare(s.level, p, p.position());
        Gt.check(n >= 1 && d.alarmed(), "coup de feu : le cerf a 13 blocs prend la fuite");
        s.done();
    }

    // ------------------------------------------------------------------ 8. porte-fusil : 3 armes gardees

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_rack", timeoutTicks = 40)
    public static void p105_10_porte_fusil_trois_armes(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos wall = h.absolutePos(new BlockPos(9, 1, 11)), pos = h.absolutePos(new BlockPos(9, 1, 10));
        s.level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        s.level.setBlock(pos, PiHunt.GUN_RACKS.get(0).getBlock().defaultBlockState().setValue(GunRackBlock.FACING, Direction.NORTH), 3);
        Gt.check(s.level.getBlockEntity(pos) instanceof GunRackTile, "tuile du porte-fusil");
        GunRackTile t = (GunRackTile) s.level.getBlockEntity(pos);
        ItemStack[] w = {new ItemStack(PiHunt.MUSKET.get()), new ItemStack(PiHunt.REVOLVER.get()), new ItemStack(PiHunt.HARPOON.get())};
        Gt.check(GunRackTile.accepts(w[0]) && GunRackTile.accepts(w[1]) && GunRackTile.accepts(w[2]) && !GunRackTile.accepts(new ItemStack(Items.STICK)),
              "accepte mousquet, revolver, harpon ; pas un baton");
        for (int i = 0; i < 3; i++) {
            t.set(t.firstFree(), w[i].copy());
        }
        Gt.check(t.firstFree() < 0 && t.count() == 3, "trois armes posees, plus de place");
        CompoundTag tag = t.saveWithFullMetadata();
        GunRackTile copy = new GunRackTile(pos, t.getBlockState());
        copy.load(tag);
        Gt.check(copy.count() == 3 && copy.get(2).is(PiHunt.HARPOON.get()), "sauvegarde : les trois armes restent");
        Gt.equal(0, GunRackBlock.rowAt(12.5 / 16.0), "rang du haut");
        Gt.equal(2, GunRackBlock.rowAt(3.5 / 16.0), "rang du bas");
        s.done();
    }

    // ------------------------------------------------------------------ 9. trophee au butin

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_trophy", timeoutTicks = 60)
    public static void p105_10_trophee_au_butin(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.onCleanup(() -> HuntSkill.forcedTrophy = null);
        HuntSkill.forcedTrophy = 1.0;
        Gt.check(Math.abs(HuntRules.trophyChance(0) - 0.05) < 1e-9 && Math.abs(HuntRules.trophyChance(1.0) - 0.10) < 1e-9, "regle : 5 % -> 10 %");
        ServerPlayer p = s.player("hunth", "Artemis", new BlockPos(3, 1, 10));
        Bear b = add(s, PiWild7.BEAR.get(), new BlockPos(9, 1, 10));
        b.setNoAi(true);
        b.hurt(s.level.damageSources().playerAttack(p), 10_000F);
        AABB box = new AABB(h.absolutePos(new BlockPos(0, 0, 0)), h.absolutePos(new BlockPos(19, 6, 19)));
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(s.level.getEntitiesOfClass(ItemEntity.class, box, i -> i.getItem().is(PiHunt.BEAR_TROPHY.get().asItem()))
                    .size() == 1, "l'ours abattu laisse son tapis (trophee)"))
              .thenExecute(() -> s.level.getEntitiesOfClass(ItemEntity.class, box, i -> true).forEach(Entity::discard))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 10. XP de Chasse

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_10_xp", timeoutTicks = 60)
    public static void p105_10_xp_de_chasse(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("hunti", "Orion", new BlockPos(3, 1, 10));
        double before = PioneerCap.get(p).xp("hunting");
        Deer d = add(s, PiWild.DEER.get(), new BlockPos(9, 1, 10));
        d.setNoAi(true);
        Gt.check(HuntSkill.game(d) && !HuntSkill.dangerous(d), "le cerf est un gibier, pas une bete dangereuse");
        Gt.check(HuntRules.killXp(1, 30, false) == 2 && HuntRules.killXp(1, 5, true) == 2 && HuntRules.killXp(1, 30, true) == 4,
              "regle : x 2 de loin, x 2 bete dangereuse");
        d.hurt(s.level.damageSources().playerAttack(p), 10_000F);
        AABB box = new AABB(h.absolutePos(new BlockPos(0, 0, 0)), h.absolutePos(new BlockPos(19, 6, 19)));
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(PioneerCap.get(p).xp("hunting") > before, "gibier abattu : XP de Chasse gagnee"))
              .thenExecute(() -> s.level.getEntitiesOfClass(ItemEntity.class, box, i -> true).forEach(Entity::discard))
              .thenExecute(s::done);
    }
}
