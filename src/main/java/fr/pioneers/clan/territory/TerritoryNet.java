package fr.pioneers.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.seal.ClanSeals;
import fr.pioneers.clan.seal.SealItems;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : paquets du territoire (canal generique {@link RpNet}, plage des clans). Bordures : le joueur qui les affiche (ou
 * qui tient un Sceau) recoit toutes les 2 secondes, et seulement si quelque chose a change, les proprietaires des chunks proches (carre de
 * 2r+1 chunks, r = 8 par defaut) en indices d'une petite palette (relation, couleur), et les blocs designes de SON clan dans ce carre. Jamais
 * de nom de compte. Fiche du Sceau : choix des fonctions (serveur -> client), designation et levee (client -> serveur, revues cote serveur).
 * <p>
 * 1.9.2 (retours de Guillaume : « pas de bordure blanche bizarre ») : plus de bordures permanentes. La touche U, {@code /clan borders} et
 * chaque claim ou abandon envoient une vue ponctuelle marquee {@code flash} : le client trace un fin liseré au sol, a la couleur du clan,
 * qui s'efface en quelques secondes. Le Sceau en main garde sa vue (blocs designes), sans liseré.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class TerritoryNet {

    /** Numeros d'action (plage des clans de {@code RpActions}, hors des numeros des fondations). */
    public static final int BORDERS = 560, SEAL_CHOICE = 561;
    public static final int WATCH = 660, SEAL_SET = 661, SEAL_LIFT = 662;

    /** Relations envoyees au client (indice de palette 0 = libre). */
    public static final int REL_OWN = 1, REL_ALLY = 2, REL_NEUTRAL = 3, REL_WAR = 4;

    private static final Map<UUID, Integer> SENT = new HashMap<>();
    private static int version;

    private TerritoryNet() {
    }

    static void register() {
        //1.9.2 : la touche U demande un liseré ponctuel (plus d'abonnement)
        RpNet.onServer(WATCH, (p, t) -> flash(p));
        RpNet.onServer(SEAL_SET, (p, t) -> ClanSeals.designate(p, BlockPos.of(t.getLong("pos")), t.getString("fn")));
        RpNet.onServer(SEAL_LIFT, (p, t) -> ClanSeals.lift(p, BlockPos.of(t.getLong("pos"))));
    }

    /** Le territoire a change quelque part : les vues seront renvoyees au prochain passage. */
    public static void bump() {
        version++;
        Protection.refresh();
    }

    public static void sendSealChoice(ServerPlayer p, CompoundTag t) {
        RpNet.send(p, SEAL_CHOICE, t);
    }

    /**
     * 1.9.2 (retours de Guillaume) : {@code /clan borders}, touche U, claim ou abandon : vue ponctuelle du territoire autour du joueur,
     * marquee {@code flash} (le client trace le liseré quelques secondes). N'entre pas dans {@link #SENT} : la vue du Sceau n'est pas
     * perturbee, et le controle espace n'efface pas le liseré.
     */
    public static void flash(ServerPlayer p) {
        if (!ClanApi.enabled()) {
            return;
        }
        CompoundTag t = view(p, true);
        t.putBoolean("flash", true);
        RpNet.send(p, BORDERS, t);
    }

    static boolean watching(ServerPlayer p) {
        return p.getMainHandItem().is(SealItems.FUNCTION_SEAL.get()) || p.getOffhandItem().is(SealItems.FUNCTION_SEAL.get());
    }

    /** Controle espace (toutes les 2 secondes). */
    static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (watching(p)) {
                sendView(p, false);
            } else if (SENT.remove(p.getUUID()) != null) {
                //le joueur a range son Sceau : on efface la vue chez lui
                CompoundTag t = new CompoundTag();
                t.putBoolean("on", false);
                RpNet.send(p, BORDERS, t);
            }
        }
    }

    /** Vue du territoire autour du joueur (envoyee seulement si elle a change, ou si {@code force}). */
    static void sendView(ServerPlayer p, boolean force) {
        CompoundTag t = view(p, false);
        int hash = t.hashCode() * 31 + version;
        Integer before = SENT.get(p.getUUID());
        if (!force && before != null && before == hash) {
            return;
        }
        SENT.put(p.getUUID(), hash);
        RpNet.send(p, BORDERS, t);
    }

    static CompoundTag view(ServerPlayer p, boolean flash) {
        CompoundTag t = new CompoundTag();
        boolean on = flash || watching(p);
        t.putBoolean("on", on);
        if (!on || !ClanApi.enabled()) {
            return t;
        }
        MinecraftServer server = p.getServer();
        ClanData data = ClanData.get(server);
        Clan mine = data.clanOf(p.getUUID());
        String dim = ClanApi.dim(p.level());
        int r = TerritoryDefs.get().borderRadius;
        ChunkPos c = p.chunkPosition();
        Map<UUID, Integer> palette = new LinkedHashMap<>();
        ListTag pal = new ListTag();
        byte[] codes = new byte[(2 * r + 1) * (2 * r + 1)];
        int i = 0;
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                long chunk = ChunkPos.asLong(c.x + dx, c.z + dz);
                UUID owner = ClaimIndex.owner(dim, chunk);
                int code = 0;
                if (owner != null) {
                    Integer idx = palette.get(owner);
                    if (idx == null && palette.size() < 60) {
                        Clan o = data.clan(owner);
                        CompoundTag e = new CompoundTag();
                        ClanApi.Relation rel = o == null ? ClanApi.Relation.NEUTRAL : ClanApi.relation(server, mine, o);
                        e.putInt("rel", switch (rel) {
                            case SAME -> REL_OWN;
                            case ALLY -> REL_ALLY;
                            case WAR -> REL_WAR;
                            default -> REL_NEUTRAL;
                        });
                        //1.9.2 : -1 = clan solo (couleur jamais choisie, blanche par defaut) : le client prend l'or de la carte
                        e.putInt("color", o == null ? 8 : o.solo ? -1 : o.color & 15);
                        pal.add(e);
                        idx = palette.size() + 1;
                        palette.put(owner, idx);
                    }
                    code = idx == null ? 0 : idx;
                    if (mine != null && owner.equals(mine.id)) {
                        Clan.Claim cl = mine.claim(dim, chunk);
                        if (cl != null && cl.declineUntil > 0) {
                            code |= 64;
                        }
                    }
                }
                codes[i++] = (byte) code;
            }
        }
        t.putInt("cx", c.x);
        t.putInt("cz", c.z);
        t.putInt("r", r);
        t.putByteArray("codes", codes);
        t.put("palette", pal);
        if (mine != null) {
            List<Long> pos = new ArrayList<>();
            ListTag fns = new ListTag();
            for (Map.Entry<String, String> e : mine.functions.entrySet()) {
                if (!ClanSeals.dimOf(e.getKey()).equals(dim)) {
                    continue;
                }
                BlockPos b = ClanSeals.posOf(e.getKey());
                if (Math.abs((b.getX() >> 4) - c.x) <= r && Math.abs((b.getZ() >> 4) - c.z) <= r) {
                    pos.add(b.asLong());
                    fns.add(StringTag.valueOf(e.getValue()));
                }
            }
            t.putLongArray("seals", pos);
            t.put("fns", fns);
        }
        return t;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }

    static void clear() {
        SENT.clear();
    }
}
