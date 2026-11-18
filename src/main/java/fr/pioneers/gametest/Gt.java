package fr.pioneers.gametest;

import com.mojang.authlib.GameProfile;
import fr.pioneers.api.machine.unit.ToolBay;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanCore;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.faction.FactionSaved;
import fr.pioneers.society.clan.territory.ClanChunkloading;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.war.ClanDiplomacy;
import fr.pioneers.society.clan.war.Siege;
import fr.pioneers.society.clan.war.War;
import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.society.reputation.PeopleApi;
import fr.pioneers.world.ruins.RuinSites;
import fr.pioneers.society.village.VillageData;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * 1.9 (MAJ RP, GameTests) : outils communs des GameTests du mod (paquet charge SEULEMENT quand Forge active les GameTests : aucune classe du
 * jeu ne cite ce paquet).
 * <ul>
 * <li>joueurs factices complets ({@link Scope#player}) : un vrai {@code ServerPlayer} entre par {@code PlayerList.placeNewPlayer} (evenements
 * de connexion, sauvegarde et relecture de ses donnees a la deconnexion), relie a un canal Netty en memoire dont les paquets sortants sont
 * jetes. {@code GameTestHelper.makeMockServerPlayerInLevel} de Forge 1.20.1 ne convient pas : sa connexion sans canal fait planter
 * {@code NetworkHooks.sendMCRegistryPackets}, et le joueur est en creatif ;</li>
 * <li>une portee par test ({@link #begin}) : elle note l'etat des donnees de monde globales (clans, factions, guerres, peuples de joueurs,
 * noms reserves, pactes, villages et ruines du niveau) et remet tout en place a la fin ({@link Scope#done}) ; un test qui echoue est nettoye
 * au debut du suivant (chaque test a son propre lot : ils passent l'un apres l'autre) ;</li>
 * <li>sauvegarde puis relecture d'une donnee de monde ({@link #reload}) : NBT ecrit et relu compresse, puis la fonction {@code load} de la
 * classe (privee : lue par reflexion, dans le meme module).</li>
 * </ul>
 */
public final class Gt {

    /**
     * 1.0.4-beta (lot 11) : bloc compact d'une machine du catalogue a ce tier : atelier, ou machine du KIT (broyeur, four industriel,
     * compacteur, qui ont absorbe d'autres machines : leurs recettes recues tournent par la voie B de KitProcess).
     */
    public static net.minecraft.world.level.block.Block compact(fr.pioneers.tech.industry.IndustryMachine m, fr.pioneers.logistics.transport.CableTier t) {
        if (m.atelier == fr.pioneers.tech.industry.IndustryMachine.Atelier.KIT) {
            return fr.pioneers.tech.machine.PiMachineKit.MACHINES.get(fr.pioneers.tech.machine.MachineKind.valueOf(m.name())).get(t).getBlock();
        }
        return fr.pioneers.tech.industry.PiIndustry.ATELIERS.get(m).get(t).getBlock();
    }


    /**
     * 1.0.5-beta (lot 44, retour 62) : la case « Outil » d'une machine (atelier, controleur de multibloc, machine a vapeur) ; le test echoue
     * si elle n'en a pas.
     */
    public static fr.pioneers.core.inventory.ItemSlot toolSlot(Object machine) {
        fr.pioneers.core.inventory.ItemSlot slot = machine instanceof net.minecraft.world.level.block.entity.BlockEntity be
              ? ToolBay.of(be) : null;
        check(slot != null, "la machine n'a pas de case Outil : " + machine);
        return slot;
    }

    /** 1.0.5-beta (lot 44) : pose un outil (matrice, forme d'extrusion, catalyseur...) dans la case Outil de la machine. */
    public static void tool(Object machine, net.minecraft.world.item.ItemStack stack) {
        toolSlot(machine).setStack(stack);
    }

    /** Gabarits ({@code data/pioneers/structures/gametest/*.nbt}, ecrits par {@code tools/gametest/templates.py}). */
    public static final String SMALL = "gametest/flat9", BORDER = "gametest/flat20", LARGE = "gametest/flat32";
    /** API Multibloc (lot 5) : multiblocs construits selon leur plan (32 x 24 x 32). */
    public static final String TALL = "gametest/tall32";
    public static final int SMALL_SIZE = 9, BORDER_SIZE = 20, LARGE_SIZE = 32;

    @Nullable
    private static Scope current;

    private Gt() {
    }

    // ------------------------------------------------------------------ portee
    /** Ouvre la portee d'un test (nettoie d'abord ce qu'un test precedent aurait laisse en echouant). */
    public static Scope begin(GameTestHelper h) {
        if (current != null) {
            current.cleanup();
        }
        current = new Scope(h);
        //1.0.6 : table des echeances sure (un rappel peut planifier a son tour sans faire planter le serveur de tests)
        GtTickMap.install(h);
        return current;
    }

    // ------------------------------------------------------------------ buffers
    /**
     * Ouvre en entree-sortie les six faces d'un buffer pose par le test. Un buffer neuf a toutes ses faces fermees (le joueur les regle) :
     * sans face ouverte, un cable ne s'y branche pas et le reseau sans fiche n'existe pas.
     */
    public static fr.pioneers.logistics.transport.buffer.BufferTile openFaces(GameTestHelper h, BlockPos abs) {
        net.minecraft.world.level.block.entity.BlockEntity be = h.getLevel().getBlockEntity(abs);
        check(be instanceof fr.pioneers.logistics.transport.buffer.BufferTile, "pas de buffer en " + abs);
        fr.pioneers.logistics.transport.buffer.BufferTile b = (fr.pioneers.logistics.transport.buffer.BufferTile) be;
        for (fr.pioneers.core.RelativeSide r : fr.pioneers.core.RelativeSide.values()) {
            b.unit().sides().set(r, fr.pioneers.core.side.SideMode.IO);
        }
        return b;
    }

    // ------------------------------------------------------------------ verifications
    public static void check(boolean ok, String what) {
        if (!ok) {
            throw new GameTestAssertException(what);
        }
    }

    public static void equal(long expected, long actual, String what) {
        if (expected != actual) {
            throw new GameTestAssertException(what + " : attendu " + expected + ", obtenu " + actual);
        }
    }

    public static void equal(@Nullable Object expected, @Nullable Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new GameTestAssertException(what + " : attendu " + expected + ", obtenu " + actual);
        }
    }

    // ------------------------------------------------------------------ donnees de monde
    /**
     * Sauvegarde puis relit une donnee de monde comme au redemarrage du serveur : {@code save}, ecriture NBT compressee, relecture, puis la
     * fonction {@code static load(CompoundTag)} de sa classe. La copie relue est independante de l'originale.
     */
    @SuppressWarnings("unchecked")
    public static <T extends SavedData> T reload(T data) {
        return (T) load(data.getClass(), data.save(new CompoundTag()));
    }

    /**
     * 1.0.6 : rend un joueur operateur de niveau 4. {@code PlayerList.op} prend le niveau du serveur ({@code getOperatorUserPermissionLevel}),
     * que le serveur de GameTests met a 0 : l'operateur n'y avait aucun droit.
     */
    public static void op(net.minecraft.server.MinecraftServer server, net.minecraft.server.level.ServerPlayer p) {
        server.getPlayerList().getOps().add(new net.minecraft.server.players.ServerOpListEntry(p.getGameProfile(), 4, false));
        server.getPlayerList().sendPlayerPermissionLevel(p);
    }

    /**
     * 1.0.6 : premiere case libre (sans collision) a partir de {@code abs} en montant (8 au plus). Dans un gabarit, le sol de pierre est en
     * y = 1 relatif (y = 0 : la couche du bloc de structure) : une bete posee en y = 1 naissait DANS la pierre (etouffee, sans chemin).
     */
    public static BlockPos standing(net.minecraft.world.level.Level level, BlockPos abs) {
        BlockPos p = abs;
        for (int i = 0; i < 8 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) {
            p = p.above();
        }
        return p;
    }

    /**
     * Relit une tile comme au chargement d'un chunk : {@code BlockEntity.loadStatic} lit le type dans la cle « id », que seule
     * {@code saveWithId} ecrit ({@code saveWithoutMetadata} ne l'a pas : la relecture rendait null). A comparer ensuite par
     * {@code saveWithoutMetadata} des deux cotes.
     */
    @javax.annotation.Nullable
    public static net.minecraft.world.level.block.entity.BlockEntity reloadTile(net.minecraft.world.level.block.entity.BlockEntity tile) {
        return net.minecraft.world.level.block.entity.BlockEntity.loadStatic(tile.getBlockPos(), tile.getBlockState(), tile.saveWithId());
    }

    /** Relit un NBT avec la fonction {@code static load(CompoundTag)} (souvent privee) d'une classe de donnee de monde du mod. */
    public static SavedData load(Class<?> type, CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            CompoundTag read = NbtIo.readCompressed(new ByteArrayInputStream(out.toByteArray()));
            Method m = type.getDeclaredMethod("load", CompoundTag.class);
            m.setAccessible(true);
            return (SavedData) m.invoke(null, read);
        } catch (IOException | ReflectiveOperationException | ClassCastException e) {
            throw new GameTestAssertException("relecture impossible de " + type.getSimpleName() + " : " + e);
        }
    }

    // ------------------------------------------------------------------ joueurs factices
    /**
     * Connecte un joueur factice de survie ({@code account} = pseudo du compte, 16 caracteres au plus). Le joueur passe par les vrais
     * evenements de connexion ; ses paquets partent dans un canal en memoire et sont jetes.
     */
    public static ServerPlayer connect(MinecraftServer server, ServerLevel level, UUID id, String account) {
        //1.0.6 : le mode de jeu reel, comme un vrai joueur (avant : toujours faux, un test qui passait le joueur en creatif ou en spectateur
        //ne le voyait jamais) ; gameMode est encore null pendant le constructeur de Player, qui lit deja ces deux methodes
        ServerPlayer p = new ServerPlayer(server, level, new GameProfile(id, account)) {
            @Override
            public boolean isSpectator() {
                return gameMode != null && gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
            }

            @Override
            public boolean isCreative() {
                return gameMode != null && gameMode.getGameModeForPlayer() == GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        //les paquets sortants s'arretent ici (au plus pres de la tete du canal) : rien ne s'accumule en memoire
        channel.pipeline().addFirst("pioneers_gametest_sink", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
                ReferenceCountUtil.release(msg);
                promise.trySuccess();
            }
        });
        channel.attr(Connection.ATTRIBUTE_PROTOCOL).set(ConnectionProtocol.PLAY);
        server.getPlayerList().placeNewPlayer(connection, p);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    /** Deconnecte un joueur factice s'il est encore la (sauvegarde de ses donnees, evenement de deconnexion). */
    public static void disconnect(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            server.getPlayerList().remove(p);
        }
    }

    /** Pseudo de compte aleatoire (lettres et chiffres, 16 caracteres au plus) : jamais deux fois le meme. */
    public static String account(String prefix) {
        String p = prefix.length() > 8 ? prefix.substring(0, 8) : prefix;
        return p + Integer.toHexString(0x1000000 + ThreadLocalRandom.current().nextInt(0xE000000));
    }

    // ------------------------------------------------------------------ geometrie
    /** Abscisse relative {@code x} de la structure dont l'absolue est le dernier bloc d'un chunk ({@code x + 1} ouvre le chunk voisin). */
    public static int borderX(GameTestHelper h, int width) {
        for (int x = 0; x < width - 1; x++) {
            if ((h.absolutePos(new BlockPos(x, 1, 0)).getX() & 15) == 15) {
                return x;
            }
        }
        throw new GameTestAssertException("aucune frontiere de chunk dans la structure");
    }

    public static long chunk(BlockPos abs) {
        return ChunkPos.asLong(abs.getX() >> 4, abs.getZ() >> 4);
    }

    // ------------------------------------------------------------------ portee d'un test
    /** Etat d'un test : joueurs crees, instantane des donnees globales, nettoyage. */
    public static final class Scope {

        public final GameTestHelper h;
        public final ServerLevel level;
        public final MinecraftServer server;
        private final List<UUID> players = new ArrayList<>();
        private final Set<UUID> clans = new HashSet<>(), factions = new HashSet<>(), pacts = new HashSet<>(), schisms = new HashSet<>(),
              schismDone = new HashSet<>();
        private final Set<String> peoples = new HashSet<>(), reserved = new HashSet<>();
        private final int wars;
        private final CompoundTag villages, sites;
        private final Difficulty difficulty;
        private final List<Runnable> undo = new ArrayList<>();
        private boolean closed;

        Scope(GameTestHelper h) {
            this.h = h;
            this.level = h.getLevel();
            this.server = level.getServer();
            ClanData data = ClanData.get(server);
            data.clans().forEach(c -> clans.add(c.id));
            data.factions().forEach(f -> factions.add(f.id));
            wars = data.wars.size();
            PeopleApi.PlayerPeoples.get(server).peoples.keySet().forEach(peoples::add);
            FactionSaved fs = FactionSaved.get(server);
            reserved.addAll(fs.reserved.keySet());
            pacts.addAll(fs.pacts.keySet());
            schisms.addAll(fs.schisms.keySet());
            schismDone.addAll(fs.schismDone);
            villages = VillageData.get(level).save(new CompoundTag());
            sites = RuinSites.get(level).save(new CompoundTag());
            difficulty = level.getDifficulty();
        }

        // -------------------------------------------------------------- joueurs
        /** Joueur factice de survie, initie (Carnet passe), nom de personnage {@code character}, pose en {@code rel} (relatif au test). */
        public ServerPlayer player(String accountPrefix, String character, BlockPos rel) {
            ServerPlayer p = connect(server, level, UUID.randomUUID(), account(accountPrefix));
            players.add(p.getUUID());
            initiate(p, character);
            move(p, rel);
            return p;
        }

        /** Reconnecte un joueur deja cree (meme compte) : ses donnees sont relues depuis sa sauvegarde. */
        public ServerPlayer reconnect(UUID id, String account, BlockPos rel) {
            ServerPlayer p = connect(server, level, id, account);
            if (!players.contains(id)) {
                players.add(id);
            }
            move(p, rel);
            return p;
        }

        public static void initiate(ServerPlayer p, String character) {
            PioneerProfile prof = PioneerCap.get(p);
            prof.initiated = true;
            prof.initStep = 2; //1.12.0 : derniere etape de l'initiation (lecture), l'allure est retiree
            prof.name = character;
            prof.touch();
        }

        public void move(ServerPlayer p, BlockPos rel) {
            Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(rel));
            p.teleportTo(level, v.x, v.y, v.z, 0, 0);
        }

        /** Le serveur ne doit pas etre en paisible (Sentinelles) : remis comme avant a la fin du test. */
        public void notPeaceful() {
            if (level.getDifficulty() == Difficulty.PEACEFUL) {
                server.setDifficulty(Difficulty.NORMAL, true);
            }
        }

        /** Action a rejouer au nettoyage (blocs poses hors de la structure, entites...). */
        public void onCleanup(Runnable r) {
            undo.add(r);
        }

        // -------------------------------------------------------------- clans
        /** Fonde un clan ordinaire (Empire) dont {@code founder} est le Representant ; les autres entrent en Recrue. */
        public Clan clan(String name, ServerPlayer founder, ServerPlayer... members) {
            check(ClanCore.enabled(), "clans coupes ou definitions des clans non chargees");
            Clan c = ClanCore.found(server, founder, List.of(), name, name.substring(0, Math.min(4, name.length())).toUpperCase(), 4, "", "empire");
            for (ServerPlayer m : members) {
                ClanCore.join(server, c, m, "gametest");
            }
            check(founder.getUUID().equals(c.representative), "le fondateur n'est pas Representant de " + name);
            for (ServerPlayer m : members) {
                check(c.members.containsKey(m.getUUID()), m.getGameProfile().getName() + " n'est pas entre dans " + name);
            }
            return c;
        }

        /** Donne un role a un membre (par le Representant). */
        public void role(Clan c, ServerPlayer member, String role) {
            String err = ClanRoles.assign(c, c.representative, member.getUUID(), role);
            check(err == null, "role " + role + " refuse : " + err);
        }

        /** Reclame le chunk de cette position pour le clan (ecriture du territoire, sans le controle du budget ni du point d'apparition). */
        public void claim(Clan c, BlockPos abs) {
            ClanTerritory.put(server, c, ClanApi.dim(level), chunk(abs), new Clan.Claim());
        }

        /** Alliance signee entre deux clans (offre de B, acceptation de A, regime Empire : effet direct). */
        public void ally(Clan a, ServerPlayer repA, Clan b, ServerPlayer repB) {
            String err = ClanDiplomacy.propose(repB, b, a, ClanDiplomacy.ALLIANCE);
            check(err == null, "offre d'alliance refusee : " + err);
            ClanDiplomacy.accept(repA, a, b, ClanDiplomacy.ALLIANCE);
            check(ClanDiplomacy.alliance(a, b), "alliance non signee");
            check(ClanApi.relation(server, a, b) == ClanApi.Relation.ALLY, "les deux clans ne sont pas allies");
        }

        /** Guerre ouverte (preavis ecoule) de {@code attacker} contre {@code defender}, inscrite directement. */
        public War war(Clan attacker, Clan defender) {
            long now = ClanClock.now();
            War w = War.create(attacker.id, false, defender.id, false, "gametest", now - 120_000, now - 60_000);
            ClanData.get(server).wars.add(w.tag);
            ClanData.get(server).changed();
            return w;
        }

        // -------------------------------------------------------------- fin
        /** Nettoie puis declare le test reussi. */
        public void done() {
            cleanup();
            if (current == this) {
                current = null;
            }
            h.succeed();
        }

        /** Remet les donnees globales comme au debut du test et deconnecte les joueurs factices. */
        void cleanup() {
            if (closed) {
                return;
            }
            closed = true;
            List<RuntimeException> errors = new ArrayList<>();
            for (Runnable r : undo) {
                try {
                    r.run();
                } catch (RuntimeException e) {
                    errors.add(e);
                }
            }
            for (UUID u : players) {
                try {
                    disconnect(server, u);
                } catch (RuntimeException e) {
                    errors.add(e);
                }
            }
            try {
                cleanClans();
            } catch (RuntimeException e) {
                errors.add(e);
            }
            try {
                level.getDataStorage().set(VillageData.NAME, load(VillageData.class, villages));
                level.getDataStorage().set(RuinSites.NAME, load(RuinSites.class, sites));
            } catch (RuntimeException e) {
                errors.add(e);
            }
            if (level.getDifficulty() != difficulty) {
                server.setDifficulty(difficulty, true);
            }
            if (!errors.isEmpty()) {
                fr.pioneers.Pioneers.LOGGER.warn("GameTests : nettoyage incomplet ({}) : {}", errors.size(), errors.get(0).toString());
            }
        }

        private void cleanClans() {
            ClanData data = ClanData.get(server);
            List<Clan> newClans = new ArrayList<>();
            for (Clan c : data.clans()) {
                if (!clans.contains(c.id)) {
                    newClans.add(c);
                }
            }
            List<Faction> newFactions = new ArrayList<>();
            for (Faction f : data.factions()) {
                if (!factions.contains(f.id)) {
                    newFactions.add(f);
                }
            }
            Set<UUID> gone = new HashSet<>();
            newClans.forEach(c -> gone.add(c.id));
            newFactions.forEach(f -> gone.add(f.id));
            //guerres des clans et factions du test : drapeaux leves, lignes retirees
            for (int i = data.wars.size() - 1; i >= 0; i--) {
                CompoundTag w = data.wars.getCompound(i);
                boolean ours = w.hasUUID("a") && gone.contains(w.getUUID("a")) || w.hasUUID("b") && gone.contains(w.getUUID("b"));
                if (ours || i >= wars) {
                    Siege.dropFlagsOf(server, new War(w).id(), "clan.pioneers.siege.fell_war");
                    data.wars.remove(i);
                }
            }
            for (Clan c : newClans) {
                ClanChunkloading.stopAll(server, c);
                Map<String, List<Long>> claims = new LinkedHashMap<>();
                c.claims.forEach((dim, m) -> claims.put(dim, new ArrayList<>(m.keySet())));
                claims.forEach((dim, list) -> list.forEach(chunk -> ClanTerritory.drop(server, c, dim, chunk, false)));
                c.faction = null;
                data.remove(c);
            }
            for (Faction f : newFactions) {
                for (Clan c : data.clans()) {
                    if (f.id.equals(c.faction)) {
                        c.faction = null;
                    }
                }
                data.remove(f);
            }
            PeopleApi.PlayerPeoples pp = PeopleApi.PlayerPeoples.get(server);
            for (String id : new ArrayList<>(pp.peoples.keySet())) {
                if (!peoples.contains(id)) {
                    pp.remove(id);
                }
            }
            FactionSaved fs = FactionSaved.get(server);
            fs.reserved.keySet().removeIf(k -> !reserved.contains(k));
            fs.pacts.keySet().removeIf(k -> !pacts.contains(k));
            fs.schisms.keySet().removeIf(k -> !schisms.contains(k));
            fs.schismDone.removeIf(k -> !schismDone.contains(k));
            fs.changed();
            data.changed();
        }
    }

    /** Copie des affinites d'un registre de village (villageois -&gt; joueur -&gt; affinite), pour comparer avant / apres. */
    public static Map<UUID, Map<UUID, Integer>> register(VillageData.Village v) {
        Map<UUID, Map<UUID, Integer>> out = new HashMap<>();
        v.register.forEach((villager, m) -> out.put(villager, new HashMap<>(m)));
        return out;
    }

}
