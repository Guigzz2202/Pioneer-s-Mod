package fr.pioneers.society.clan.seal;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.territory.TerritoryDefs;
import fr.pioneers.society.clan.territory.TerritoryNet;
import fr.pioneers.society.clan.territory.TerritoryRules;
import fr.pioneers.society.clan.treasury.ClanFinance;
import fr.pioneers.core.profile.PioneerApi;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.9 (MAJ RP, bloc 4) : les designations des Sceaux de fonction. Un bloc du territoire porte au plus une fonction ({@code Clan.functions} :
 * "dimension|position" -> fonction, le bloc designe est note dans {@code Clan.extra} pour reconnaitre un remplacement). La designation
 * tombe si le bloc est casse, deplace ou remplace, si le chunk quitte le territoire, ou si on la leve : le Sceau est rendu sur place (ou
 * plus tard a un membre autorise si le chunk n'est pas charge). Effets lus par le reste du jeu : {@link #marketTaxAt}, rayon de brigade
 * pres d'un Atelier, apparition a la Mairie ou au Baraquement, interface du clan a la Mairie, stock des Entrepots, Archives.
 */
public final class ClanSeals {

    public static final String TOWN_HALL = "town_hall", WAREHOUSE = "warehouse", WORKSHOP = "workshop", MARKET = "market",
          WATCHTOWER = "watchtower", BARRACKS = "barracks", ARCHIVES = "archives";
    /** Cles de {@code Clan.extra}. */
    static final String BLOCKS = "t_fn_blocks", BY = "t_fn_by", OWED = "t_seals_owed";

    /** Interface du clan (onglet Clan du Carnet, branche par l'onglet) ouverte par un clic droit sur la Mairie. */
    @Nullable
    public static volatile Consumer<ServerPlayer> clanScreen;

    /**
     * Positions designees par dimension (index de lecture, refait au demarrage et a chaque designation). Indexe par la cle de dimension
     * (aucune chaine construite : il est lu a chaque mise a jour de bloc).
     */
    private static final Map<ResourceKey<Level>, LongOpenHashSet> DESIGNATED = new HashMap<>();

    private static ResourceKey<Level> dimKey(String dim) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim));
    }

    private ClanSeals() {
    }

    // ------------------------------------------------------------------ lecture
    public static String key(Level level, BlockPos pos) {
        return ClanApi.dim(level) + "|" + pos.asLong();
    }

    public static String dimOf(String key) {
        return key.substring(0, key.indexOf('|'));
    }

    public static BlockPos posOf(String key) {
        return BlockPos.of(Long.parseLong(key.substring(key.indexOf('|') + 1)));
    }

    /** Premiere designation de cette fonction (cle "dimension|position") ou null. */
    @Nullable
    public static String first(Clan c, String fn) {
        for (Map.Entry<String, String> e : c.functions.entrySet()) {
            if (e.getValue().equals(fn)) {
                return e.getKey();
            }
        }
        return null;
    }

    public static List<String> all(Clan c, String fn) {
        List<String> out = new ArrayList<>();
        c.functions.forEach((k, v) -> {
            if (v.equals(fn)) {
                out.add(k);
            }
        });
        return out;
    }

    public static boolean designated(Level level, BlockPos pos) {
        if (DESIGNATED.isEmpty()) {
            return false;
        }
        LongOpenHashSet s = DESIGNATED.get(level.dimension());
        return s != null && s.contains(pos.asLong());
    }

    /** Clan qui tient ce bloc designe (le bloc est dans un de ses claims) et sa fonction. */
    @Nullable
    public static Map.Entry<Clan, String> at(MinecraftServer server, Level level, BlockPos pos) {
        if (!designated(level, pos)) {
            return null;
        }
        UUID id = ClaimIndex.owner(ClanApi.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        Clan c = id == null ? null : ClanData.get(server).clan(id);
        String fn = c == null ? null : c.functions.get(key(level, pos));
        return fn == null ? null : Map.entry(c, fn);
    }

    /** Index refait depuis les clans (demarrage). */
    public static void rebuild(MinecraftServer server) {
        DESIGNATED.clear();
        for (Clan c : ClanData.get(server).clans()) {
            for (String k : c.functions.keySet()) {
                DESIGNATED.computeIfAbsent(dimKey(dimOf(k)), d -> new LongOpenHashSet()).add(posOf(k).asLong());
            }
        }
    }

    public static void clear() {
        DESIGNATED.clear();
    }

    // ------------------------------------------------------------------ designer
    /** Ce qu'un clic droit avec le Sceau propose (fiche envoyee au client) : refus avec message, ou choix des fonctions. */
    public static void offer(ServerPlayer p, BlockPos pos) {
        Clan c = owner(p, pos);
        if (c == null) {
            return;
        }
        TerritoryDefs d = TerritoryDefs.get();
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putString("current", c.functions.getOrDefault(key(p.level(), pos), ""));
        t.putInt("level", c.level);
        ListTag opts = new ListTag();
        for (TerritoryDefs.Function f : d.functions.values()) {
            CompoundTag o = new CompoundTag();
            o.putString("id", f.id());
            o.putInt("level", f.level());
            o.putInt("max", f.max());
            o.putInt("count", TerritoryRules.count(c.functions, f.id()));
            opts.add(o);
        }
        t.put("options", opts);
        TerritoryNet.sendSealChoice(p, t);
    }

    /** Le joueur peut-il designer ici : bloc de son territoire, permission SEALS, a portee. Renvoie le clan ou null (message envoye). */
    @Nullable
    private static Clan owner(ServerPlayer p, BlockPos pos) {
        if (!ClanApi.enabled()) {
            ClanMessages.refuse(p, "clan.pioneers.territory.disabled");
            return null;
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null || c.claim(ClanApi.dim(p.level()), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)) == null) {
            ClanMessages.refuse(p, "clan.pioneers.seal.not_territory");
            return null;
        }
        if (!ClanTerritory.may(c, p.getUUID(), Permission.SEALS)) {
            ClanMessages.refuse(p, "clan.pioneers.territory.no_permission");
            return null;
        }
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64 || p.level().getBlockState(pos).isAir()) {
            return null;
        }
        return c;
    }

    /** Designation choisie dans la fiche (tout est revu cote serveur). */
    public static void designate(ServerPlayer p, BlockPos pos, String fn) {
        Clan c = owner(p, pos);
        if (c == null) {
            return;
        }
        TerritoryDefs.Function f = TerritoryDefs.get().function(fn);
        String key = key(p.level(), pos);
        if (f == null) {
            return;
        }
        if (c.functions.containsKey(key)) {
            ClanMessages.refuse(p, "clan.pioneers.seal.already");
            return;
        }
        if (c.level < f.level()) {
            ClanMessages.refuse(p, "clan.pioneers.seal.level", Component.translatable("clan.pioneers.function." + fn), f.level());
            return;
        }
        if (TerritoryRules.count(c.functions, fn) >= f.max()) {
            ClanMessages.refuse(p, "clan.pioneers.seal.max", Component.translatable("clan.pioneers.function." + fn), f.max());
            return;
        }
        if (WAREHOUSE.equals(fn) && !container(p.level(), pos)) {
            ClanMessages.refuse(p, "clan.pioneers.seal.warehouse_container");
            return;
        }
        ItemStack seal = sealInHand(p);
        if (seal.isEmpty()) {
            ClanMessages.refuse(p, "clan.pioneers.seal.no_seal");
            return;
        }
        if (!p.isCreative()) {
            seal.shrink(1);
        }
        c.functions.put(key, fn);
        BlockState state = p.level().getBlockState(pos);
        blocks(c).putString(key, String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock())));
        by(c).putString(key, p.getUUID().toString());
        DESIGNATED.computeIfAbsent(p.level().dimension(), d -> new LongOpenHashSet()).add(pos.asLong());
        if (TOWN_HALL.equals(fn)) {
            bannerAbove(p.level(), pos, c);
        }
        c.log(PioneerApi.day(p.getServer()), "clan.pioneers.log.seal", ClanMessages.character(p), "#clan.pioneers.function." + fn,
              pos.getX() + " " + pos.getY() + " " + pos.getZ());
        ClanData.get(p.getServer()).changed();
        TerritoryNet.bump();
        ClanMessages.tell(p, "clan.pioneers.seal.done", Component.translatable("clan.pioneers.function." + fn));
    }

    /** Lever le Sceau d'un bloc designe : le Sceau revient au joueur. */
    public static void lift(ServerPlayer p, BlockPos pos) {
        Clan c = owner(p, pos);
        if (c == null) {
            return;
        }
        String key = key(p.level(), pos);
        String fn = c.functions.get(key);
        if (fn == null) {
            return;
        }
        remove(c, key);
        ItemStack back = new ItemStack(SealItems.FUNCTION_SEAL.get());
        if (!p.getInventory().add(back)) {
            p.drop(back, false);
        }
        c.log(PioneerApi.day(p.getServer()), "clan.pioneers.log.seal_lift", ClanMessages.character(p), "#clan.pioneers.function." + fn);
        ClanData.get(p.getServer()).changed();
        TerritoryNet.bump();
        ClanMessages.tell(p, "clan.pioneers.seal.lifted", Component.translatable("clan.pioneers.function." + fn));
    }

    static ItemStack sealInHand(ServerPlayer p) {
        for (ItemStack s : List.of(p.getMainHandItem(), p.getOffhandItem())) {
            if (s.is(SealItems.FUNCTION_SEAL.get())) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    static boolean container(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof Container || be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
    }

    private static CompoundTag blocks(Clan c) {
        if (!c.extra.contains(BLOCKS)) {
            c.extra.put(BLOCKS, new CompoundTag());
        }
        return c.extra.getCompound(BLOCKS);
    }

    private static CompoundTag by(Clan c) {
        if (!c.extra.contains(BY)) {
            c.extra.put(BY, new CompoundTag());
        }
        return c.extra.getCompound(BY);
    }

    private static void remove(Clan c, String key) {
        c.functions.remove(key);
        blocks(c).remove(key);
        by(c).remove(key);
        LongOpenHashSet s = DESIGNATED.get(dimKey(dimOf(key)));
        if (s != null) {
            s.remove(posOf(key).asLong());
        }
    }

    // ------------------------------------------------------------------ chute d'une designation
    /** Le bloc designe a disparu, bouge ou change : la designation tombe et le Sceau est rendu sur place. */
    public static void fall(MinecraftServer server, Level level, BlockPos pos) {
        Map.Entry<Clan, String> e = at(server, level, pos);
        if (e == null) {
            //bloc designe d'un chunk qui n'est plus au clan : l'index est simplement nettoye
            LongOpenHashSet s = DESIGNATED.get(level.dimension());
            if (s != null) {
                s.remove(pos.asLong());
            }
            return;
        }
        Clan c = e.getKey();
        remove(c, key(level, pos));
        giveBack(server, level, pos, c);
        c.log(PioneerApi.day(server), "clan.pioneers.log.seal_fell", "#clan.pioneers.function." + e.getValue(),
              pos.getX() + " " + pos.getY() + " " + pos.getZ());
        ClanData.get(server).changed();
        TerritoryNet.bump();
        ClanMessages.members(server, c, Permission.SEALS, ClanMessages.tagged(c, Component.translatable("clan.pioneers.seal.fell",
              Component.translatable("clan.pioneers.function." + e.getValue()))));
    }

    /** Membre exclu : les designations qu'il avait posees tombent ; leurs Sceaux reviennent au clan (a un membre autorise). */
    public static void dropBy(MinecraftServer server, Clan c, UUID player) {
        String who = player.toString();
        int n = 0;
        for (String key : new ArrayList<>(c.functions.keySet())) {
            if (who.equals(by(c).getString(key))) {
                remove(c, key);
                n++;
            }
        }
        if (n > 0) {
            c.extra.putInt(OWED, c.extra.getInt(OWED) + n);
            ClanData.get(server).changed();
            TerritoryNet.bump();
        }
    }

    /** Le chunk quitte le territoire (abandon, declin, capture, dissolution) : ses designations tombent. */
    public static void dropChunk(MinecraftServer server, Clan c, String dim, long chunk, boolean returnSeals) {
        ServerLevel level = ClanTerritory.level(server, dim);
        for (String key : new ArrayList<>(c.functions.keySet())) {
            if (!dimOf(key).equals(dim)) {
                continue;
            }
            BlockPos pos = posOf(key);
            if (ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4) != chunk) {
                continue;
            }
            remove(c, key);
            if (returnSeals) {
                giveBack(server, level, pos, c);
            }
        }
    }

    /** Sceau rendu au pied du bloc si le chunk est charge, sinon garde pour un membre autorise (remis a sa prochaine presence). */
    static void giveBack(MinecraftServer server, @Nullable Level level, BlockPos pos, Clan c) {
        if (level != null && level.isLoaded(pos)) {
            ItemEntity it = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new ItemStack(SealItems.FUNCTION_SEAL.get()));
            it.setDefaultPickUpDelay();
            level.addFreshEntity(it);
        } else {
            c.extra.putInt(OWED, c.extra.getInt(OWED) + 1);
        }
    }

    /** Sceaux dus remis a un membre autorise connecte ; designations dont le bloc a change (chunks charges) relues. Chaque minute. */
    public static void tickMinute(MinecraftServer server) {
        for (Clan c : ClanData.get(server).clans()) {
            int owed = c.extra.getInt(OWED);
            if (owed > 0) {
                for (UUID u : c.members.keySet()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p != null && ClanTerritory.may(c, u, Permission.SEALS)) {
                        ItemStack back = new ItemStack(SealItems.FUNCTION_SEAL.get(), owed);
                        if (!p.getInventory().add(back)) {
                            p.drop(back, false);
                        }
                        c.extra.remove(OWED);
                        ClanMessages.tell(p, "clan.pioneers.seal.returned", owed);
                        ClanData.get(server).changed();
                        break;
                    }
                }
            }
            CompoundTag blocks = blocks(c);
            for (String key : new ArrayList<>(c.functions.keySet())) {
                ServerLevel level = ClanTerritory.level(server, dimOf(key));
                BlockPos pos = posOf(key);
                if (level == null || !level.isLoaded(pos)) {
                    continue;
                }
                String now = String.valueOf(ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock()));
                if (level.getBlockState(pos).isAir() || blocks.contains(key) && !blocks.getString(key).equals(now)) {
                    fall(server, level, pos);
                }
            }
        }
    }

    // ------------------------------------------------------------------ effets
    /**
     * Taxe de marche (%) qui s'applique a un etal pose ici : celle du clan qui tient le chunk, s'il a un Marche assez pres ; 0 sinon. Une seule
     * regle pour tout le jeu : celle du tresor des clans ({@code ClanFinance.marketTaxFor}, rayon et plafond des regles).
     */
    public static int marketTaxAt(Level level, BlockPos pos) {
        if (level.isClientSide || level.getServer() == null || !ClanApi.enabled()) {
            return 0;
        }
        return ClanFinance.marketTaxFor(level, pos);
    }

    /** Clan dont le Marche taxe un etal pose ici (null : aucun). */
    @Nullable
    public static Clan marketClanAt(Level level, BlockPos pos) {
        if (marketTaxAt(level, pos) <= 0) {
            return null;
        }
        UUID id = ClaimIndex.owner(ClanApi.dim(level), ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return id == null ? null : ClanData.get(level.getServer()).clan(id);
    }

    /** Une designation de cette fonction du clan est-elle a moins de son rayon (donnees) de la position ? */
    public static boolean near(Clan c, String dim, BlockPos pos, String fn) {
        TerritoryDefs.Function f = TerritoryDefs.get().function(fn);
        if (f == null) {
            return false;
        }
        double r2 = (double) f.radius() * f.radius();
        for (Map.Entry<String, String> e : c.functions.entrySet()) {
            if (e.getValue().equals(fn) && dimOf(e.getKey()).equals(dim) && posOf(e.getKey()).distSqr(pos) <= r2) {
                return true;
            }
        }
        return false;
    }

    /** Rayon de la brigade d'un joueur : celui de l'Atelier (48 blocs) quand il travaille pres d'un Atelier de son clan. */
    public static int brigadeRadius(ServerPlayer p, int base) {
        if (!ClanApi.enabled()) {
            return base;
        }
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null || !c.functions.containsValue(WORKSHOP) || !near(c, ClanApi.dim(p.level()), p.blockPosition(), WORKSHOP)) {
            return base;
        }
        TerritoryDefs.Function f = TerritoryDefs.get().function(WORKSHOP);
        return Math.max(base, f == null ? base : f.radius());
    }

    /** Une machine est-elle pres d'un Atelier de ce clan (production des machines du clan alentour) ? */
    public static boolean nearWorkshop(Clan c, Level level, BlockPos pos) {
        return near(c, ClanApi.dim(level), pos, WORKSHOP);
    }

    /** Stock des Entrepots du clan (chunks charges seulement) : objet -> quantite. */
    public static Map<Item, Long> warehouseStock(MinecraftServer server, Clan c) {
        Map<Item, Long> out = new LinkedHashMap<>();
        for (String key : all(c, WAREHOUSE)) {
            ServerLevel level = ClanTerritory.level(server, dimOf(key));
            BlockPos pos = posOf(key);
            if (level == null || !level.isLoaded(pos)) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            IItemHandler h = be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            if (h != null) {
                for (int i = 0; i < h.getSlots(); i++) {
                    ItemStack s = h.getStackInSlot(i);
                    if (!s.isEmpty()) {
                        out.merge(s.getItem(), (long) s.getCount(), Long::sum);
                    }
                }
            } else if (be instanceof Container ct) {
                for (int i = 0; i < ct.getContainerSize(); i++) {
                    ItemStack s = ct.getItem(i);
                    if (!s.isEmpty()) {
                        out.merge(s.getItem(), (long) s.getCount(), Long::sum);
                    }
                }
            }
        }
        return out;
    }

    /** Une banniere posee juste au-dessus de la Mairie devient l'embleme du clan (couleur de fond et motifs). */
    static boolean bannerAbove(Level level, BlockPos hall, Clan c) {
        BlockPos up = hall.above();
        BlockState s = level.getBlockState(up);
        if (!(s.getBlock() instanceof AbstractBannerBlock banner) || !(level.getBlockEntity(up) instanceof BannerBlockEntity be)) {
            return false;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("Base", banner.getColor().getId());
        CompoundTag data = be.saveWithoutMetadata();
        if (data.contains("Patterns")) {
            t.put("Patterns", data.getList("Patterns", 10));
        }
        c.banner = t;
        return true;
    }
}
