package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.ClanTreasury;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanViews;
import fr.pioneers.society.clan.faction.FactionViews;
import fr.pioneers.society.clan.faction.Factions;
import fr.pioneers.society.clan.people.Peoples;
import fr.pioneers.society.clan.stats.ClanStats;
import fr.pioneers.society.clan.territory.ClanChunkloading;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.war.ClanWar;
import fr.pioneers.society.clan.war.Siege;
import fr.pioneers.society.clan.war.War;
import fr.pioneers.society.clan.war.WarBlocks;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.society.economy.EconomyApi;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.9 (MAJ RP, GameTests) : bloc 4 hors protection et transferts : chunkloading apres un redemarrage simule, creneaux de siege, impot
 * d'activite, aucun nom de compte chez un etranger, Solitaire -&gt; clan -&gt; Solitaire, migration d'un profil a trois reputations.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class ClanTests {

    private static final BlockPos AT = new BlockPos(4, 2, 4);

    private ClanTests() {
    }

    static String sfx(GameTestHelper h) {
        return Integer.toHexString(h.absolutePos(BlockPos.ZERO).hashCode() & 0xfff);
    }

    // ================================================================== chunkloading
    static ForcedChunksSavedData forced(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ForcedChunksSavedData::load, ForcedChunksSavedData::new, ForcedChunksSavedData.FILE_ID);
    }

    /** Un ticket au nom d'un identifiant (clan ou autre) tient-il ce chunk dans les donnees de tickets de Forge ? */
    static boolean ticketed(ForcedChunksSavedData data, long chunk) {
        ForgeChunkManager.TicketTracker<UUID> t = data.getEntityForcedChunks();
        for (LongSet set : t.getTickingChunks().values()) {
            if (set.contains(chunk)) {
                return true;
            }
        }
        for (LongSet set : t.getChunks().values()) {
            if (set.contains(chunk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chunkloading d'un clan, puis « redemarrage » : les tickets sont sauves, retires du serveur, relus et revalides par le controleur du mod
     * comme au chargement du monde. Le chunk du clan reste charge ; un ticket orphelin (chunk hors emplacements) et celui d'un clan inconnu
     * sont supprimes. L'emplacement survit aussi a la sauvegarde du clan.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_clan_chunkloading", timeoutTicks = 400)
    public static void chunkloading_retabli_apres_redemarrage(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gtload", "Gardienne", AT);
        Clan c = s.clan("GtLoad" + sfx(h), p);
        c.treasury = 100_000;
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        ChunkPos f = new ChunkPos((origin.getX() >> 4) + 40, origin.getZ() >> 4), g = new ChunkPos(f.x + 1, f.z), k = new ChunkPos(f.x + 2, f.z);
        s.claim(c, f.getWorldPosition());
        s.claim(c, g.getWorldPosition());
        UUID ghost = UUID.randomUUID();
        UUID clanId = c.id;
        s.onCleanup(() -> {
            ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, clanId, f.x, f.z, false, true);
            ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, clanId, g.x, g.z, false, true);
            ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, ghost, k.x, k.z, false, true);
        });
        String dim = ClanApi.dim(s.level);
        ClanChunkloading.load(p, s.level, f.toLong());
        Gt.check(c.chunkload.contains(ClanTerritory.key(dim, f.toLong())), "emplacement de chunkloading non pris");
        Gt.check(ticketed(forced(s.level), f.toLong()), "aucun ticket apres le chargement du chunk");
        //tickets parasites : chunk du clan hors emplacements (crash), clan inconnu
        ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, clanId, g.x, g.z, true, true);
        ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, ghost, k.x, k.z, true, true);
        //le clan sauve garde son emplacement et son claim
        Clan copy = Clan.load(c.save());
        Gt.check(copy.chunkload.contains(ClanTerritory.key(dim, f.toLong())) && copy.claim(dim, f.toLong()) != null,
              "emplacement ou claim perdu a la sauvegarde du clan");
        //redemarrage : sauvegarde des tickets, arret (tickets retires), rechargement et validation
        CompoundTag saved = forced(s.level).save(new CompoundTag());
        ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, clanId, f.x, f.z, false, true);
        ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, clanId, g.x, g.z, false, true);
        ForgeChunkManager.forceChunk(s.level, Pioneers.MODID, ghost, k.x, k.z, false, true);
        Gt.check(!ticketed(forced(s.level), f.toLong()), "le ticket aurait du etre retire avant le redemarrage");
        ForcedChunksSavedData restored = ForcedChunksSavedData.load(saved);
        s.level.getDataStorage().set(ForcedChunksSavedData.FILE_ID, restored);
        ForgeChunkManager.reinstatePersistentChunks(s.level, restored);
        Gt.check(ticketed(forced(s.level), f.toLong()), "le chunk du clan n'est plus charge apres le redemarrage");
        Gt.check(!ticketed(forced(s.level), g.toLong()), "ticket orphelin (chunk hors emplacements) garde au redemarrage");
        Gt.check(!ticketed(forced(s.level), k.toLong()), "ticket d'un clan inconnu garde au redemarrage");
        //le chunk (genere au besoin) finit par etre charge par le ticket retabli
        h.succeedWhen(() -> {
            Gt.check(s.level.getChunkSource().hasChunk(f.x, f.z), "chunk du clan non charge apres le redemarrage");
            s.done();
        });
    }

    // ================================================================== siege
    /** Essaie de planter un drapeau de siege sur le sol (position relative du sol) ; vrai si le drapeau est la. */
    static boolean plantFlag(GameTestHelper h, ServerPlayer p, BlockPos floorRel) {
        BlockPos floor = h.absolutePos(floorRel);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WarBlocks.SIEGE_FLAG, 4));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        return h.getLevel().getBlockState(floor.above()).is(WarBlocks.SIEGE_FLAG.getBlock());
    }

    /**
     * Guerre ouverte : le drapeau de siege se plante dans un chunk ennemi voisin seulement pendant un creneau du defenseur ; hors creneau il
     * est refuse et rien n'est pose ; dans le creneau, il tient le chunk (siege en cours).
     */
    @GameTest(template = Gt.BORDER, batch = "pioneers_clan_siege", timeoutTicks = 60)
    public static void siege_seulement_dans_les_creneaux(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int x = Gt.borderX(h, Gt.BORDER_SIZE);
        ServerPlayer pa = s.player("gtatk", "Assiegeante", new BlockPos(x + 2, 2, 8));
        ServerPlayer pd = s.player("gtdef", "Defenseur", new BlockPos(2, 2, 2));
        Clan a = s.clan("GtAtk" + sfx(h), pa), d = s.clan("GtDef" + sfx(h), pd);
        s.claim(a, h.absolutePos(new BlockPos(x, 2, 10)));
        s.claim(d, h.absolutePos(new BlockPos(x + 1, 2, 10)));
        War w = s.war(a, d);
        BlockPos floorRel = new BlockPos(x + 3, 1, 10), flagRel = floorRel.above();
        BlockPos flagAbs = h.absolutePos(flagRel);
        s.onCleanup(() -> s.level.removeBlock(flagAbs, false));
        int hour = ClanWar.minuteOfDay(fr.pioneers.society.clan.core.ClanClock.now()) / 60;
        //hors creneau : 6 et 10 heures plus tard (creneaux de 2 heures)
        d.pendingWindows = null;
        d.siegeWindows = new int[]{(hour + 6) % 24, (hour + 10) % 24};
        Gt.check(!plantFlag(h, pa, floorRel), "drapeau plante hors des creneaux du defenseur");
        Gt.check(!Siege.besieged(ClanApi.dim(s.level), ChunkPos.asLong(flagAbs.getX() >> 4, flagAbs.getZ() >> 4)), "siege ouvert hors creneau");
        //dans le creneau
        d.siegeWindows = new int[]{hour, (hour + 12) % 24};
        Gt.check(plantFlag(h, pa, floorRel), "drapeau refuse pendant un creneau du defenseur");
        Gt.check(Siege.besieged(ClanApi.dim(s.level), ChunkPos.asLong(flagAbs.getX() >> 4, flagAbs.getZ() >> 4)), "siege non inscrit");
        Gt.equal(3, pa.getMainHandItem().getCount(), "drapeau consomme");
        Siege.dropFlagsOf(s.server, w.id(), "clan.pioneers.siege.fell_war");
        s.done();
    }

    // ================================================================== impot
    /** Impot d'activite : partage exact joueur / tresor, et rien n'est ecrit si l'un des deux comptes deborderait. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_clan_impot", timeoutTicks = 40)
    public static void impot_d_activite_atomique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(EconomyApi.enabled(), "economie coupee");
        ServerPlayer rep = s.player("gttaxrep", "Percepteur", AT);
        ServerPlayer m = s.player("gttaxm", "Contribuable", AT);
        Clan c = s.clan("GtTax" + sfx(h), rep, m);
        c.activityTax = 20;
        c.treasury = 0;
        PioneerProfile pm = PioneerCap.get(m);
        pm.balance = 0;
        EconomyApi.Entry e = EconomyApi.Entry.of("gametest");
        Gt.equal(EconomyApi.Result.OK, ClanTreasury.creditWithTax(m, 1000, e), "gain impose");
        Gt.equal(800, pm.balance, "part du joueur");
        Gt.equal(200, c.treasury, "part du tresor");
        //tresor plein : rien ne bouge
        c.treasury = EconomyApi.MAX - 100;
        Gt.equal(EconomyApi.Result.CAP, ClanTreasury.creditWithTax(m, 1000, e), "tresor au plafond");
        Gt.equal(800, pm.balance, "le joueur a ete credite malgre le refus");
        Gt.equal(EconomyApi.MAX - 100, c.treasury, "le tresor a change malgre le refus");
        //joueur plein : rien ne bouge
        c.treasury = 0;
        pm.balance = EconomyApi.MAX - 100;
        Gt.equal(EconomyApi.Result.CAP, ClanTreasury.creditWithTax(m, 1000, e), "solde du joueur au plafond");
        Gt.equal(EconomyApi.MAX - 100, pm.balance, "le solde a change malgre le refus");
        Gt.equal(0, c.treasury, "le tresor a ete credite malgre le refus");
        s.done();
    }

    // ================================================================== noms de compte
    static void noAccount(CompoundTag t, List<String> accounts, String where, List<String> faults) {
        String text = t.toString();
        for (String a : accounts) {
            if (text.contains(a)) {
                faults.add(where + " montre le compte " + a);
            }
        }
    }

    /**
     * Aucune fiche lue par un etranger, une Recrue ou un clan allie ne contient un nom de compte : fiches du clan, de la faction, statistiques,
     * annuaires, invitation. Controle : le Representant (permission de voir les identites) voit bien les comptes.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_clan_noms", timeoutTicks = 40)
    public static void aucun_nom_de_compte_chez_un_etranger(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer rep = s.player("gtnmrep", "Aline", AT);
        ServerPlayer mem = s.player("gtnmmem", "Bertin", AT);
        ServerPlayer rec = s.player("gtnmrec", "Cosette", AT);
        ServerPlayer other = s.player("gtnmoth", "Dorian", AT);
        ServerPlayer stranger = s.player("gtnmstr", "Eudes", AT);
        Clan a = s.clan("GtNoms" + sfx(h), rep, mem, rec);
        s.role(a, mem, "member");
        Clan b = s.clan("GtAutre" + sfx(h), other);
        List<String> accounts = List.of(rep.getGameProfile().getName(), mem.getGameProfile().getName(), rec.getGameProfile().getName());
        CompoundTag pact = new CompoundTag();
        pact.putString("name", "GtFedNoms" + sfx(h));
        pact.putString("tag", "GFN");
        pact.putInt("color", 5);
        pact.putString("regime", "empire");
        Factions.create(s.server, pact, a, List.of(b), rep.getUUID());
        Gt.check(ClanCore.invite(rep, stranger) == null, "invitation refusee");
        List<String> faults = new ArrayList<>();
        for (String sheet : ClanViews.SHEETS) {
            noAccount(ClanViews.sheet(rec, sheet), accounts, "fiche " + sheet + " (Recrue)", faults);
            noAccount(ClanViews.sheet(stranger, sheet), accounts, "fiche " + sheet + " (etranger invite)", faults);
            noAccount(ClanViews.sheet(other, sheet), accounts, "fiche " + sheet + " (autre clan)", faults);
        }
        for (String sheet : FactionViews.SHEETS) {
            noAccount(FactionViews.sheet(other, sheet), accounts, "faction " + sheet + " (autre clan)", faults);
            noAccount(FactionViews.sheet(rec, sheet), accounts, "faction " + sheet + " (Recrue)", faults);
            noAccount(FactionViews.sheet(stranger, sheet), accounts, "faction " + sheet + " (etranger)", faults);
        }
        noAccount(ClanStats.sheet(stranger, a), accounts, "statistiques (etranger)", faults);
        noAccount(ClanStats.sheet(other, a), accounts, "statistiques (allie)", faults);
        noAccount(ClanStats.sheet(rec, a), accounts, "statistiques (Recrue)", faults);
        noAccount(ClanStats.card(s.server, a), accounts, "carte publique", faults);
        noAccount(ClanStats.directory(s.server), accounts, "annuaire des clans", faults);
        noAccount(Peoples.directory(s.server), accounts, "annuaire des peuples", faults);
        Gt.check(faults.isEmpty(), String.join(" ; ", faults));
        //controle positif : la fiche Membres du Representant montre les comptes (sinon le test ne prouverait rien)
        Gt.check(ClanViews.sheet(rep, "members").toString().contains(mem.getGameProfile().getName()),
              "le Representant ne voit pas les comptes : le test ne verifie plus rien");
        s.done();
    }

    // ================================================================== Solitaire
    /**
     * Un Solitaire reclame un chunk (clan solo cree), invite un joueur : le clan solo est converti (meme identifiant, claim garde, regime a
     * choisir) ; le compagnon parti, le clan redevient solo et garde son territoire.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_clan_solitaire", timeoutTicks = 40)
    public static void solo_puis_clan_puis_solo(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer lone = s.player("gtsolo", "Ermite", AT);
        ServerPlayer mate = s.player("gtmate", "Compagnon", AT);
        BlockPos origin = h.absolutePos(BlockPos.ZERO);
        long chunk = ChunkPos.asLong((origin.getX() >> 4) + 256, (origin.getZ() >> 4) + 256);
        String dim = ClanApi.dim(s.level);
        ClanTerritory.claim(lone, s.level, chunk);
        ClanData data = ClanData.get(s.server);
        Clan c = data.clanOf(lone.getUUID());
        Gt.check(c != null && c.solo, "aucun clan solo cree par le premier claim");
        Gt.check(c.claim(dim, chunk) != null, "claim du Solitaire absent");
        UUID id = c.id;
        Gt.check(ClanCore.invite(lone, mate) == null, "invitation du Solitaire refusee");
        Gt.check(ClanCore.accept(mate, null) == null, "invitation non acceptee");
        Clan now = data.clanOf(mate.getUUID());
        Gt.check(now != null && now.id.equals(id), "le compagnon n'est pas entre dans le clan du Solitaire");
        Gt.check(!now.solo, "le clan n'a pas ete converti");
        Gt.check(now.claim(dim, chunk) != null, "claim perdu a la conversion");
        Gt.check(lone.getUUID().equals(now.representative), "le Solitaire n'est plus Representant");
        Gt.check(ClanCore.leave(mate) == null, "depart refuse");
        Clan back = data.clanOf(lone.getUUID());
        Gt.check(back != null && back.id.equals(id) && back.solo, "le clan n'est pas redevenu solo");
        Gt.check(back.claim(dim, chunk) != null, "claim perdu au retour au solo");
        Gt.check(data.clanOf(mate.getUUID()) == null, "le compagnon est encore membre");
        s.done();
    }

    // ================================================================== migration
    /**
     * Ancien profil (format 1, trois champs de reputation) ecrit dans le fichier du joueur : a la connexion, il est lu dans la table
     * peuple -&gt; jauge et ressauve au format courant.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_profil_migration", timeoutTicks = 40)
    public static void migration_profil_trois_reputations(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("gtmig", "Ancienne", AT);
        UUID id = p.getUUID();
        String account = p.getGameProfile().getName();
        Gt.disconnect(s.server, id);
        File file = s.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat").toFile();
        Gt.check(file.isFile(), "fichier du joueur non ecrit a la deconnexion");
        try {
            CompoundTag all = NbtIo.readCompressed(file);
            CompoundTag caps = all.getCompound("ForgeCaps");
            CompoundTag old = new CompoundTag();
            old.putInt("format", 1);
            old.putBoolean("initiated", true);
            old.putInt("step", 3);
            old.putString("name", "Ancienne");
            old.putInt("rep_restorers", 42);
            old.putInt("rep_bare_hands", -15);
            old.putInt("rep_peddlers", 7);
            caps.put(Pioneers.rl("pioneer").toString(), old);
            all.put("ForgeCaps", caps);
            NbtIo.writeCompressed(all, file);
        } catch (java.io.IOException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("fichier du joueur illisible : " + e.getMessage());
        }
        ServerPlayer q = s.reconnect(id, account, AT);
        PioneerProfile prof = PioneerCap.get(q);
        Gt.equal(42, prof.reputation("restorers"), "Restaurateurs");
        Gt.equal(-15, prof.reputation("bare_hands"), "Mains nues");
        Gt.equal(7, prof.reputation("peddlers"), "Colporteurs");
        CompoundTag now = prof.serializeNBT();
        Gt.equal(PioneerProfile.FORMAT, now.getInt("format"), "format resauve");
        Gt.check(!now.contains("rep_restorers"), "ancien champ resauve");
        Gt.equal(42, now.getCompound("rep").getInt("restorers"), "table resauvee");
        s.done();
    }
}
