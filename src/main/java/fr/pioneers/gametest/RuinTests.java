package fr.pioneers.gametest;

import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.world.ruins.ArchiveCabinetTile;
import fr.pioneers.world.ruins.DerelictBlock;
import fr.pioneers.world.ruins.DerelictControllerTile;
import fr.pioneers.world.ruins.Restoration;
import fr.pioneers.world.ruins.RuinApi;
import fr.pioneers.world.ruins.RuinBlocks;
import fr.pioneers.world.ruins.RuinItems;
import fr.pioneers.world.ruins.RuinSites;
import fr.pioneers.world.ruins.ScrapCategory;
import fr.pioneers.world.ruins.ScrapItem;
import fr.pioneers.world.ruins.SiteTile;
import fr.pioneers.world.ruins.gen.AnnexFeature;
import fr.pioneers.world.ruins.gen.GenData;
import fr.pioneers.world.ruins.gen.RuinFeature;
import fr.pioneers.world.ruins.gen.RuinGrid;
import fr.pioneers.world.ruins.gen.RuinPlan;
import fr.pioneers.world.ruins.sentinel.GuardianEntity;
import fr.pioneers.world.ruins.sentinel.SentinelData;
import fr.pioneers.world.ruins.sentinel.SentinelEntity;
import fr.pioneers.world.ruins.sentinel.SentinelRules;
import fr.pioneers.world.ruins.sentinel.SentinelTypes;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 1 (ruines, Sentinelles, Console, demontage, recycleur, restauration), dans un vrai monde : ce que le banc
 * hors jeu ({@code tools/rptest}) ne voit pas (entites qui tickent, evenements Forge, donnees de monde relues, chunks decharges).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class RuinTests {

    /**
     * Cle d'une nomenclature de restauration MKI ({@code data/pioneers/ruins/restoration/chemical_reactor.json}). 1.0.6 : la graveuse a
     * l'acide ({@code acid_etcher}) est absorbee depuis les fusions du 1.0.4-beta et n'a plus de nomenclature (« restore.unknown » : aucun
     * chantier ne s'ouvrait).
     */
    static final String MACHINE = "chemical_reactor";

    private RuinTests() {
    }

    // ================================================================== outils
    /** Site de ruine sur toute la structure du test (controleur au centre du sol), cree comme le ferait le Controleur delabre. */
    static RuinSites.Site site(Gt.Scope s, int size, int tier) {
        BlockPos min = s.h.absolutePos(new BlockPos(0, 2, 0)), max = s.h.absolutePos(new BlockPos(size - 1, 5, size - 1));
        BlockPos ctrl = s.h.absolutePos(new BlockPos(size / 2, 2, size / 2));
        return RuinSites.get(s.level).ensure(s.level, ctrl.asLong(), tier, MACHINE, min, max);
    }

    /** Gardien endormi a son poste (position relative), du site donne (0 = sans site). */
    static GuardianEntity guardian(Gt.Scope s, BlockPos rel, long site, int tier) {
        GuardianEntity g = s.h.spawn(SentinelTypes.GUARDIAN.get(), rel);
        g.setup(tier, site, s.h.absolutePos(rel), 0F);
        Gt.check(g.asleep(), "le Gardien pose par setup doit dormir");
        return g;
    }

    static Item item(String id, Item fallback) {
        Item i = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        return i == Items.AIR ? fallback : i;
    }

    /** Site lointain d'un tier, dans un chunk propre au test (16 chunks par chunk d'ecart entre deux tests : aucun chevauchement). */
    static RuinGrid.Site farSite(Gt.Scope s, int tier, int salt) {
        BlockPos o = s.h.absolutePos(BlockPos.ZERO);
        int cx = (o.getX() >> 4) * 16 + 256 + salt * 8, cz = (o.getZ() >> 4) * 16 + 256;
        long seed = s.level.getSeed() ^ (cx * 341873128712L + cz * 132897987541L);
        return new RuinGrid.Site(tier, 0, 0, 0, cx, cz, seed);
    }

    /** Garde charges les 3 x 3 chunks autour d'un chunk (ecritures de la generation) jusqu'a la fin du test. */
    static void force(Gt.Scope s, int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx, z = cz + dz;
                s.level.setChunkForced(x, z, true);
                s.onCleanup(() -> s.level.setChunkForced(x, z, false));
            }
        }
    }

    /** Les Sentinelles d'une boite disparaissent a la fin du test (elles sont persistantes). */
    static void discardSentinels(Gt.Scope s, AABB box) {
        s.onCleanup(() -> s.level.getEntitiesOfClass(SentinelEntity.class, box).forEach(e -> e.discard()));
    }

    // ================================================================== generation
    /** Generation d'une ruine MKI dans le chunk de son site : plan reel degrade, Controleur qui cree son site, Console, Armoire, Sentinelles. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_ruines_generation_mk1", timeoutTicks = 200)
    public static void ruine_mk1_generee_dans_son_chunk(GameTestHelper h) {
        generated(h, 1);
    }

    /** Generation d'une ruine MKII (Sentinelles du tier, composition bornee par {@code spawn.json}). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_ruines_generation_mk2", timeoutTicks = 200)
    public static void ruine_mk2_generee_dans_son_chunk(GameTestHelper h) {
        generated(h, 2);
    }

    private static void generated(GameTestHelper h, int tier) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();   // 1.0.6 : les Sentinelles (monstres) disparaissent en paisible pendant l'attente du chunk
        Gt.check(GenData.current() != null, "donnees de generation des ruines non chargees (data/pioneers/ruins/generation.json)");
        RuinGrid.Site site = farSite(s, tier, 0);
        force(s, site.chunkX(), site.chunkZ());
        RuinPlan plan = RuinFeature.placeForTest(s.level, site);
        Gt.check(plan != null, "aucune ruine posee pour le site MK" + tier);
        int x0 = (site.chunkX() - 1) << 4, x1 = ((site.chunkX() + 1) << 4) + 15;
        int z0 = (site.chunkZ() - 1) << 4, z1 = ((site.chunkZ() + 1) << 4) + 15;
        discardSentinels(s, new AABB(x0, s.level.getMinBuildHeight(), z0, x1 + 1, s.level.getMaxBuildHeight(), z1 + 1));
        //Controleur delabre : dans la colonne du plan, il cree son site au premier chargement
        DerelictControllerTile tile = null;
        for (int y = s.level.getMinBuildHeight(); y < s.level.getMaxBuildHeight() && tile == null; y++) {
            BlockEntity be = s.level.getBlockEntity(new BlockPos(plan.ctrlX, y, plan.ctrlZ));
            if (be instanceof DerelictControllerTile t) {
                tile = t;
            }
        }
        Gt.check(tile != null, "aucun Controleur delabre dans la colonne du plan");
        Gt.equal(plan.industry.key, tile.machine(), "multibloc d'origine ecrit dans le Controleur");
        Gt.equal(tier, tile.tier(), "tier du Controleur");
        RuinSites.Site rs = tile.site(s.level);
        Gt.check(rs != null, "le Controleur n'a pas cree son site");
        Gt.equal(tier, rs.tier, "tier du site");
        Gt.check(rs.state == RuinSites.State.INTACT, "un site neuf est intact");
        Gt.check(rs.contains(tile.getBlockPos(), 0), "le Controleur est dans la boite de son site");
        //ruine posee dans son chunk (ecritures bornees aux 3 x 3 chunks autour)
        Gt.check(rs.min.getX() >= x0 && rs.max.getX() <= x1 && rs.min.getZ() >= z0 && rs.max.getZ() <= z1, "boite du site hors des 3 x 3 chunks");
        int derelict = 0, consoles = 0, cabinets = 0;
        for (BlockPos p : BlockPos.betweenClosed(rs.min, rs.max)) {
            BlockState st = s.level.getBlockState(p);
            if (st.getBlock() instanceof DerelictBlock d) {
                derelict++;
                Gt.equal(tier, d.tier, "tier d'un bloc delabre en " + p);
            }
            BlockEntity be = s.level.getBlockEntity(p);
            if (be instanceof SiteTile st2) {
                consoles++;
                Gt.equal(rs.id, st2.site(), "Console reliee au site");
            }
            if (be instanceof ArchiveCabinetTile) {
                cabinets++;
                String table = be.saveWithoutMetadata().getString("LootTable");
                Gt.check(table.startsWith("pioneers:archives/mk" + tier), "table de butin de l'Armoire : " + table);
            }
        }
        Gt.check(derelict >= 8, "trop peu de blocs delabres : " + derelict);
        Gt.check(consoles <= 1, "plus d'une Console : " + consoles);
        Gt.check(cabinets >= 1, "aucune Armoire d'archives");
        //Sentinelles : endormies, du site, du tier, nombre borne par la composition du tier
        //1.0.6 : les entites d'un chunk tout juste force ne sont visibles qu'une fois ses tickets propages (ticks suivants) : on attend le chunk
        long chunk = net.minecraft.world.level.ChunkPos.asLong(site.chunkX(), site.chunkZ());
        RuinSites.Site site2 = rs;
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(s.level.areEntitiesLoaded(chunk), "entites du chunk du site pas encore chargees"))
              .thenExecute(() -> {
                  List<SentinelEntity> sentinels = s.level.getEntitiesOfClass(SentinelEntity.class, site2.box(16));
                  SentinelRules.Composition c = SentinelData.rules().composition(tier);
                  Gt.check(!sentinels.isEmpty(), "aucune Sentinelle posee");
                  Gt.check(sentinels.size() <= c.max + c.foremen, "trop de Sentinelles : " + sentinels.size());
                  for (SentinelEntity e : sentinels) {
                      Gt.check(e.asleep(), "Sentinelle eveillee a la generation");
                      Gt.equal(site2.id, e.site(), "site de la Sentinelle");
                      Gt.check(e.tier() >= 1 && e.tier() <= tier, "tier de la Sentinelle : " + e.tier());
                  }
                  s.done();
              });
    }

    /** Annexes d'un site : blocs vanilla seulement, un coffre a la table des annexes, aucune Sentinelle, hors de l'emprise de la ruine. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_ruines_annexes", timeoutTicks = 200)
    public static void annexes_vanilla_avec_coffre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        GenData.Snapshot data = GenData.current();
        Gt.check(data != null, "donnees de generation des ruines non chargees");
        RuinGrid.Site site = farSite(s, 1, 1);
        RuinPlan plan = RuinPlan.of(site, data.settings());
        Gt.check(plan != null, "aucun plan pour le site");
        List<RuinPlan.Annex> planned = plan.annexes(data.settings(), data, 2);
        Gt.check(!planned.isEmpty(), "aucune annexe prevue par le plan");
        int half = RuinPlan.ANNEX_HALF + 1;
        //etat d'avant : seuls les blocs que l'annexe change sont juges (le terrain d'un monde reel peut porter des blocs du mod)
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        java.util.Map<RuinPlan.Annex, Integer> tops = new java.util.HashMap<>();
        for (RuinPlan.Annex a : planned) {
            force(s, a.x() >> 4, a.z() >> 4);
            int top = s.level.getHeight(Heightmap.Types.WORLD_SURFACE, a.x(), a.z());
            tops.put(a, top);
            for (BlockPos p : BlockPos.betweenClosed(a.x() - half, top - 24, a.z() - half, a.x() + half, top + 16, a.z() + half)) {
                before.put(p.immutable(), s.level.getBlockState(p));
            }
        }
        List<RuinPlan.Annex> placed = AnnexFeature.placeAnnexesForTest(s.level, plan, 2);
        Gt.check(!placed.isEmpty(), "aucune annexe posee sur un terrain plat");
        for (RuinPlan.Annex a : placed) {
            Gt.check(a.x() < plan.boxMinX || a.x() > plan.boxMaxX || a.z() < plan.boxMinZ || a.z() > plan.boxMaxZ, "annexe sur l'emprise de la ruine");
            int top = tops.get(a), chests = 0, changed = 0;
            for (BlockPos p : BlockPos.betweenClosed(a.x() - half, top - 24, a.z() - half, a.x() + half, top + 16, a.z() + half)) {
                BlockState st = s.level.getBlockState(p);
                if (st == before.get(p) || st.isAir()) {
                    continue;
                }
                changed++;
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
                Gt.equal("minecraft", id.getNamespace(), "bloc d'annexe non vanilla en " + p + " (" + id + ")");
                if (s.level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity box
                    && box.saveWithoutMetadata().getString("LootTable").startsWith("pioneers:chests/annex_mk1")) {
                    chests++;
                    //le butin d'une annexe ne contient jamais de page de lore precise (Armoires seulement)
                    box.unpackLootTable(null);
                    for (int k = 0; k < box.getContainerSize(); k++) {
                        Gt.check(!box.getItem(k).is(RuinItems.LORE_PAGE.get()), "page de lore dans le coffre d'une annexe");
                    }
                }
            }
            Gt.check(changed >= 20, "annexe " + a.type() + " a peine posee : " + changed + " blocs");
            Gt.check(chests >= 1, "annexe " + a.type() + " sans coffre a la table des annexes");
            AABB area = new AABB(a.x() - half - 4, top - 20, a.z() - half - 4, a.x() + half + 4, top + 8, a.z() + half + 4);
            Gt.check(s.level.getEntitiesOfClass(SentinelEntity.class, area).isEmpty(), "Sentinelle dans une annexe");
        }
        s.done();
    }

    // ================================================================== Sentinelles
    /** Un joueur qui approche (6 blocs, sans s'accroupir) eveille une Sentinelle : scene d'alerte puis combat, voyant orange. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_sentinelle_eveil", timeoutTicks = 120)
    public static void sentinelle_s_eveille_pres_d_un_joueur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        GuardianEntity g = guardian(s, new BlockPos(2, 2, 4), 0, 1);
        ServerPlayer p = s.player("gtwake", "Veilleur", new BlockPos(5, 2, 4));
        h.succeedWhen(() -> {
            Gt.check(!g.asleep(), "la Sentinelle dort encore");
            Gt.equal(SentinelEntity.LIGHT_ALERT, g.light(), "voyant d'alerte");
            Gt.check(p.isAlive(), "joueur");
            s.done();
        });
    }

    /** Hostile aux seuls joueurs : un zombie et un villageois colles a elle ne la reveillent pas, elle ne peut pas les viser. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_sentinelle_joueurs_seuls", timeoutTicks = 120)
    public static void sentinelle_ignore_les_non_joueurs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        GuardianEntity g = guardian(s, new BlockPos(2, 2, 4), 0, 1);
        Zombie z = h.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        Villager v = h.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 2, 6));
        h.runAfterDelay(60, () -> {
            Gt.check(g.asleep(), "un zombie ou un villageois a reveille la Sentinelle");
            Gt.check(!g.canAttack(z) && !g.canAttack(v), "une Sentinelle ne vise que les joueurs");
            s.done();
        });
    }

    /** Laisse stricte : a plus de {@code leash} (40) blocs de son poste, la Sentinelle est ramenee sur le cercle de la laisse. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_sentinelle_laisse", timeoutTicks = 80)
    public static void sentinelle_tenue_en_laisse_de_40_blocs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        double leash = SentinelData.rules().leash();
        Gt.check(Math.abs(leash - 40) < 1e-6, "laisse attendue a 40 blocs (rules.json) : " + leash);
        BlockPos here = h.absolutePos(new BlockPos(1, 2, 4));
        BlockPos post = here.offset(45, 0, 0);
        s.level.setChunkForced(post.getX() >> 4, post.getZ() >> 4, true);
        s.onCleanup(() -> s.level.setChunkForced(post.getX() >> 4, post.getZ() >> 4, false));
        discardSentinels(s, new AABB(post).inflate(60));
        //le chunk du poste se charge d'abord (ticket force), puis la Sentinelle y prend son poste et en est eloignee de 45 blocs
        h.runAfterDelay(10, () -> {
            GuardianEntity g = s.h.spawn(SentinelTypes.GUARDIAN.get(), new BlockPos(1, 2, 4));
            g.setup(1, 0, post, 0F);
            g.moveTo(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
            h.runAfterDelay(3, () -> {
                double dx = g.getX() - (post.getX() + 0.5), dz = g.getZ() - (post.getZ() + 0.5);
                double d = Math.sqrt(dx * dx + dz * dz);
                Gt.check(d <= leash + 0.05, "Sentinelle a " + d + " blocs de son poste (laisse " + leash + ")");
                Gt.check(d >= leash - 1, "Sentinelle ramenee trop loin vers son poste : " + d);
                s.done();
            });
        });
    }

    /** Sans cible, une Sentinelle eveillee rentre a son poste et s'y rendort apres {@code sleep_after} ticks (30 s). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_sentinelle_rendormie", timeoutTicks = 1600)
    public static void sentinelle_se_rendort_sans_cible(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        GuardianEntity g = guardian(s, new BlockPos(2, 2, 4), 0, 1);
        g.wakeUp(null);
        Gt.check(!g.asleep(), "eveil demande de l'exterieur refuse");
        long woke = h.getTick();
        int sleepAfter = SentinelData.rules().sleepAfter();
        h.succeedWhen(() -> {
            Gt.check(g.asleep(), "toujours eveillee");
            Gt.check(h.getTick() - woke >= sleepAfter, "rendormie trop tot : " + (h.getTick() - woke) + " ticks (attendu " + sleepAfter + ")");
            Gt.equal(SentinelEntity.LIGHT_OFF, g.light(), "voyants eteints");
            s.done();
        });
    }

    /** Veille : les Sentinelles d'un site en Veille ignorent un joueur colle a elles, voyant bleu pale. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_sentinelle_veille", timeoutTicks = 120)
    public static void veille_garde_les_sentinelles_endormies(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        GuardianEntity g = guardian(s, new BlockPos(2, 2, 4), site.id, 1);
        RuinApi.setStandby(s.level, site, null);
        Gt.check(RuinApi.standby(s.level, site.id), "site en Veille");
        s.player("gtstand", "Passant", new BlockPos(4, 2, 4));
        h.runAfterDelay(60, () -> {
            Gt.check(g.asleep(), "une Sentinelle en Veille s'est eveillee");
            Gt.equal(SentinelEntity.LIGHT_STANDBY, g.light(), "voyant de Veille");
            s.done();
        });
    }

    /** Decision 21 : frapper une Sentinelle pendant la Veille rompt la Veille du site et l'eveille. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_sentinelle_veille_rompue", timeoutTicks = 80)
    public static void veille_rompue_par_un_coup(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        GuardianEntity g = guardian(s, new BlockPos(2, 2, 4), site.id, 1);
        RuinApi.setStandby(s.level, site, null);
        ServerPlayer p = s.player("gthit", "Frappeur", new BlockPos(3, 2, 4));
        h.runAfterDelay(15, () -> {
            Gt.check(g.asleep(), "endormie avant le coup");
            g.hurt(s.level.damageSources().playerAttack(p), 1.0F);
            Gt.check(!RuinApi.standby(s.level, site.id), "la Veille tient malgre le coup");
            h.runAfterDelay(15, () -> {
                Gt.check(!g.asleep(), "la Sentinelle frappee dort encore");
                s.done();
            });
        });
    }

    // ================================================================== Console
    static BlockPos console(Gt.Scope s, RuinSites.Site site, BlockPos rel) {
        s.h.setBlock(rel, RuinBlocks.get("console", site.tier).defaultBlockState());
        Gt.check(s.h.getBlockEntity(rel) instanceof SiteTile, "la Console n'a pas de tuile");
        ((SiteTile) s.h.getBlockEntity(rel)).setSite(site.id);
        return s.h.absolutePos(rel);
    }

    static CompoundTag code(BlockPos console, int code) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", console.asLong());
        t.putString("code", String.format("%04d", Math.floorMod(code, 10000)));
        return t;
    }

    /** Le bon code de la Console met le site en Veille un jour de jeu ; les Sentinelles s'y rendorment. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_console_bon_code", timeoutTicks = 80)
    public static void console_bon_code_met_en_veille(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        GuardianEntity g = guardian(s, new BlockPos(6, 2, 6), site.id, 1);
        BlockPos at = console(s, site, new BlockPos(2, 2, 2));
        ServerPlayer p = s.player("gtcode", "Opératrice", new BlockPos(3, 2, 2));
        Gt.check(!RuinApi.standby(s.level, site.id), "pas de Veille avant le code");
        Gt.check(RpNet.dispatch(p, RpActions.CONSOLE_CODE, code(at, site.code)), "action Console non enregistree");
        Gt.check(RuinApi.standby(s.level, site.id), "le bon code n'a pas mis le site en Veille");
        long left = site.standbyUntil - s.level.getGameTime();
        Gt.check(left > RuinApi.STANDBY_TICKS - 5 && left <= RuinApi.STANDBY_TICKS, "Veille d'un jour de jeu : " + left);
        Gt.equal(0, site.wrong, "codes faux remis a zero");
        h.runAfterDelay(25, () -> {
            Gt.equal(SentinelEntity.LIGHT_STANDBY, g.light(), "voyant de Veille de la Sentinelle du site");
            s.done();
        });
    }

    /** Trois codes faux : alarme (le site s'eveille) et Console verrouillee 5 minutes, meme pour le bon code. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_console_alarme", timeoutTicks = 80)
    public static void console_trois_codes_faux_alarme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        GuardianEntity g = guardian(s, new BlockPos(6, 2, 6), site.id, 1);
        BlockPos at = console(s, site, new BlockPos(2, 2, 2));
        ServerPlayer p = s.player("gtalarm", "Maladroit", new BlockPos(3, 2, 2));
        RpNet.dispatch(p, RpActions.CONSOLE_CODE, code(at, site.code + 1));
        RpNet.dispatch(p, RpActions.CONSOLE_CODE, code(at, site.code + 2));
        Gt.equal(2, site.wrong, "deux codes faux comptes");
        Gt.check(g.asleep(), "pas d'alarme avant le troisieme code faux");
        RpNet.dispatch(p, RpActions.CONSOLE_CODE, code(at, site.code + 3));
        long now = s.level.getGameTime();
        Gt.check(site.lockedUntil > now + 5000, "Console non verrouillee apres trois codes faux");
        Gt.equal(0, site.wrong, "compteur remis a zero par l'alarme");
        Gt.check(!g.asleep(), "l'alarme n'a pas eveille la Sentinelle du site");
        RpNet.dispatch(p, RpActions.CONSOLE_CODE, code(at, site.code));
        Gt.check(!RuinApi.standby(s.level, site.id), "Console verrouillee : le bon code ne doit pas passer");
        s.done();
    }

    // ================================================================== demontage
    /** Casser un bloc delabre : scraps du tier, site demonte (plus restaurable), Sentinelles proches eveillees. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_demontage", timeoutTicks = 80)
    public static void demontage_bloc_delabre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        GuardianEntity g = guardian(s, new BlockPos(6, 2, 6), site.id, 1);
        BlockPos rel = new BlockPos(2, 2, 2);
        h.setBlock(rel, RuinBlocks.get("casing", 1).defaultBlockState());
        ServerPlayer p = s.player("gtbreak", "Ferrailleur", new BlockPos(3, 2, 3));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("pioneers:steel_pickaxe", Items.IRON_PICKAXE)));
        Gt.check(p.gameMode.destroyBlock(h.absolutePos(rel)), "casse refusee");
        h.assertBlockNotPresent(RuinBlocks.get("casing", 1), rel);
        Gt.check(site.state == RuinSites.State.DISMANTLED, "site non marque demonte : " + site.state);
        Gt.equal(1, site.broken, "blocs casses comptes");
        h.runAfterDelay(2, () -> {
            boolean scrap = false;
            for (ItemEntity e : h.getEntities(EntityType.ITEM, rel, 2.0)) {
                if (e.getItem().getItem() instanceof ScrapItem) {
                    scrap = true;
                    Gt.check(e.getItem().is(RuinItems.scrap(ScrapCategory.STRUCTURE, 1)), "scrap de structure MKI attendu : " + e.getItem());
                }
            }
            Gt.check(scrap, "aucun scrap lache");
            Gt.check(!g.asleep(), "la Sentinelle a moins de 12 blocs dort encore");
            s.done();
        });
    }

    // ================================================================== recycleur
    static AtelierTile recycler(Gt.Scope s, CableTier tier, BlockPos rel) {
        Block b = BuiltInRegistries.BLOCK.get(new ResourceLocation("pioneers", IndustryMachine.RECYCLER.atelierId(tier)));
        Gt.check(b != Blocks.AIR, "recycleur " + tier.id() + " absent");
        s.h.setBlock(rel, b.defaultBlockState());
        Gt.check(s.h.getBlockEntity(rel) instanceof AtelierTile, "le recycleur n'a pas de tuile d'atelier");
        AtelierTile t = (AtelierTile) s.h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        return t;
    }

    static int outputs(AtelierTile t) {
        int n = 0;
        for (ItemSlot slot : t.itemOutputs()) {
            n += slot.getCount();
        }
        return n;
    }

    /** Un recycleur MKIII traite un scrap MKI (son tier et les inferieurs) : le scrap disparait, des composants sortent. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_recycleur", timeoutTicks = 600)
    public static void recycleur_traite_un_scrap(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile t = recycler(s, CableTier.ELITE, new BlockPos(4, 2, 4));
        ItemStack scrap = new ItemStack(RuinItems.scrap(ScrapCategory.STRUCTURE, 1));
        ItemStack rest = t.itemInputs().get(0).insert(scrap, Action.EXECUTE, Automation.MANUAL);
        Gt.check(rest.isEmpty(), "le recycleur refuse un scrap de structure MKI");
        h.succeedWhen(() -> {
            Gt.check(t.itemInputs().get(0).isEmpty(), "scrap pas encore consomme");
            Gt.check(outputs(t) > 0, "aucun composant sorti");
            s.done();
        });
    }

    /** Un recycleur MKI ne traite pas un scrap MKII (tier superieur) : rien ne sort. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_recycleur_tier", timeoutTicks = 200)
    public static void recycleur_refuse_un_tier_superieur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        AtelierTile t = recycler(s, CableTier.BASIC, new BlockPos(4, 2, 4));
        ItemStack rest = t.itemInputs().get(0).insert(new ItemStack(RuinItems.scrap(ScrapCategory.STRUCTURE, 2)), Action.EXECUTE, Automation.MANUAL);
        h.runAfterDelay(120, () -> {
            Gt.equal(0, outputs(t), "un recycleur MKI a traite un scrap MKII");
            if (rest.isEmpty()) {
                Gt.check(!t.itemInputs().get(0).isEmpty(), "le scrap MKII a disparu");
                Gt.equal(AtelierTile.TIER, t.guiValue(6), "etat « tier insuffisant »");
            }
            s.done();
        });
    }

    // ================================================================== restauration
    static CompoundTag act(RuinSites.Site site, String what) {
        CompoundTag t = new CompoundTag();
        t.putLong("site", site.id);
        t.putString("do", what);
        return t;
    }

    /** Le chantier ne s'ouvre pas tant qu'une Sentinelle du site vit ; il s'ouvre (blocs proteges) quand le site est securise. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_restauration_sentinelle", timeoutTicks = 60)
    public static void restauration_refusee_tant_qu_une_sentinelle_vit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        GuardianEntity g = guardian(s, new BlockPos(6, 2, 6), site.id, 1);
        ServerPlayer p = s.player("gtresto", "Restauratrice", new BlockPos(3, 2, 3));
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "open"));
        Gt.check(site.state == RuinSites.State.INTACT && site.project == null, "chantier ouvert malgre une Sentinelle vivante");
        g.discard();
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "open"));
        Gt.check(site.state == RuinSites.State.RESTORING, "chantier non ouvert sur un site securise : " + site.state);
        Gt.check(site.project != null && site.project.getInt("stage") == Restoration.CLEARING, "etape Degagement attendue");
        Gt.check(site.contributors.containsKey(p.getUUID()), "l'ouvreur n'est pas contributeur");
        Gt.check(RuinApi.isProtected(s.level, site.controller()), "blocs du chantier non proteges");
        s.done();
    }

    /** Chantier ouvert : un bloc delabre de la ruine ne se casse plus ; des gravats si (etape Degagement). */
    @GameTest(template = Gt.SMALL, batch = "pioneers_restauration_protection", timeoutTicks = 60)
    public static void restauration_protege_ses_blocs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        BlockPos casing = new BlockPos(2, 2, 2), rubble = new BlockPos(6, 2, 2);
        h.setBlock(casing, RuinBlocks.get("casing", 1).defaultBlockState());
        h.setBlock(rubble, RuinBlocks.get("rubble", 1).defaultBlockState());
        ServerPlayer p = s.player("gtprot", "Chantier", new BlockPos(4, 2, 4));
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "open"));
        Gt.check(site.state == RuinSites.State.RESTORING, "chantier non ouvert");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("pioneers:steel_pickaxe", Items.IRON_PICKAXE)));
        Gt.check(!p.gameMode.destroyBlock(h.absolutePos(casing)), "un bloc du chantier a ete casse");
        h.assertBlockPresent(RuinBlocks.get("casing", 1), casing);
        Gt.check(p.gameMode.destroyBlock(h.absolutePos(rubble)), "les gravats doivent se retirer pendant le degagement");
        Gt.check(site.state == RuinSites.State.RESTORING, "retirer des gravats ne demonte pas la ruine");
        s.done();
    }

    /** Annulation : la moitie de ce qui reste dans la reserve revient a l'ouvreur (objets et jetons), le site redevient intact. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_restauration_annulation", timeoutTicks = 60)
    public static void restauration_annulee_rend_la_moitie(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        RuinSites.Site site = site(s, Gt.SMALL_SIZE, 1);
        ServerPlayer p = s.player("gtcancel", "Indecise", new BlockPos(3, 2, 3));
        EconomyApi.credit(p, 1_000_000, EconomyApi.Entry.of("gametest"));
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "open"));
        Gt.check(site.project != null, "chantier non ouvert");
        //une ligne d'objet nomme (pas un tag) : on en depose au plus 40, sans depasser le besoin
        ListTag needs = site.project.getList("needs", Tag.TAG_COMPOUND);
        String id = null;
        int n = 0;
        for (int i = 0; i < needs.size() && id == null; i++) {
            String x = needs.getCompound(i).getString("id");
            if (!x.startsWith("#") && !"tokens".equals(x) && needs.getCompound(i).getInt("need") >= 2) {
                id = x;
                n = Math.min(40, needs.getCompound(i).getInt("need")) & ~1;
            }
        }
        Gt.check(id != null, "nomenclature sans objet nomme");
        Item it = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
        p.getInventory().clearContent();
        p.getInventory().add(new ItemStack(it, n));
        long before = EconomyApi.balance(p);
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "deposit"));
        Gt.equal(0, p.getInventory().countItem(it), "objets deposes");
        long paid = before - EconomyApi.balance(p);
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "cancel"));
        Gt.check(site.project == null && site.state == RuinSites.State.INTACT, "chantier non annule");
        Gt.equal(n / 2, p.getInventory().countItem(it), "moitie des objets rendue");
        Gt.equal(before - paid + paid / 2, EconomyApi.balance(p), "moitie des jetons rendue");
        s.done();
    }

    /**
     * Restauration ouverte et payee, donnees de monde sauvees puis relues (comme au redemarrage), puis 100 jours de jeu chunk decharge : le
     * chantier avance seul jusqu'au bout de sa duree, rien n'est perdu, et la mise en service attend que le chunk se charge.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_restauration_rechargee", timeoutTicks = 1500)
    public static void restauration_rechargee_et_100_jours_chunk_decharge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        //1.0.6 : heure et calendrier remis a la fin (le test avance de 100 jours ; les series suivantes ne doivent pas en dependre)
        long offset = fr.pioneers.world.season.SeasonData.get(s.server.overworld()).offset(), time = level.getDayTime();
        s.onCleanup(() -> {
            level.setDayTime(time);
            fr.pioneers.world.season.SeasonEvents.setOffset(s.server, offset);
        });
        BlockPos far = h.absolutePos(new BlockPos(4, 2, 4)).offset(3000, 0, 0);
        RuinSites.Site site = RuinSites.get(level).ensure(level, far.asLong(), 1, MACHINE, far.offset(-4, 0, -4), far.offset(4, 6, 4));
        ServerPlayer p = s.player("gtfar", "Lointaine", new BlockPos(4, 2, 4));
        p.teleportTo(level, far.getX() + 2.5, far.getY(), far.getZ() + 0.5, 0, 0);
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "open"));
        Gt.check(site.project != null, "chantier non ouvert sur le site lointain");
        //reserve pleine (le depot est verifie par le test d'annulation)
        ListTag needs = site.project.getList("needs", Tag.TAG_COMPOUND);
        for (int i = 0; i < needs.size(); i++) {
            needs.getCompound(i).putInt("have", needs.getCompound(i).getInt("need"));
        }
        RpNet.dispatch(p, RpActions.RESTORATION_ACTION, act(site, "repair"));
        Gt.equal(Restoration.REPAIR, site.project.getInt("stage"), "etape Remise en etat");
        s.move(p, new BlockPos(4, 2, 4));
        //1.0.6 : ouvrir le chantier lit le site, ce qui charge son chunk (ticket d'un tick) : il se decharge aux ticks suivants, on l'attend
        long[] today = new long[1];
        int[] days = new int[1];
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(!level.isLoaded(site.controller()), "le chunk du site devrait etre decharge"))
              .thenExecute(() -> {
                  CompoundTag before = site.project.copy();
                  days[0] = before.getInt("days");
                  //sauvegarde et relecture du registre des ruines
                  RuinSites reloaded = Gt.reload(RuinSites.get(level));
                  level.getDataStorage().set(RuinSites.NAME, reloaded);
                  RuinSites.Site again = RuinSites.get(level).site(site.id);
                  Gt.check(again != null && again != site, "site absent apres relecture");
                  Gt.check(again.state == RuinSites.State.RESTORING, "etat perdu a la relecture : " + again.state);
                  Gt.equal(before, again.project, "projet de restauration relu a l'identique");
                  Gt.check(again.contributors.containsKey(p.getUUID()), "contributeurs perdus");
                  today[0] = fr.pioneers.core.profile.PioneerApi.day(level);
                  level.setDayTime(level.getDayTime() + 100L * 24000L);
              })
              .thenWaitUntil(() -> {
                  RuinSites.Site x = RuinSites.get(level).site(site.id);
                  Gt.check(x != null && x.project != null, "projet perdu");
                  Gt.equal(days[0], x.project.getInt("done"), "jours de chantier faits chunk decharge");
                  Gt.equal(today[0] + 100, x.project.getLong("lastDay"), "dernier jour compte");
                  Gt.equal(Restoration.REPAIR, x.project.getInt("stage"), "mise en service avant le chargement du chunk");
                  Gt.check(x.state == RuinSites.State.RESTORING, "etat du site");
                  Gt.check(!level.isLoaded(x.controller()), "chunk du site charge pendant le test");
              })
              .thenExecute(s::done);
    }
}
