package fr.pioneers.client.clan.ui;

import fr.pioneers.Pioneers;
import fr.pioneers.client.rp.NotebookScreen;
import fr.pioneers.client.rp.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : cote client de l'onglet Clan. Cache versionne des fiches recues ({@code CLAN_VIEW}, {@code CLAN_STATS},
 * {@code CLAN_DIRECTORY} ; la carte dans {@link ClanMapModel}), envoi des intentions (le serveur revoit tout), et bandeau des reponses :
 * la cle de {@code CLAN_NOTICE} et, pendant quelques secondes apres une intention, les messages systeme que le serveur adresse au joueur
 * (refus detailles, confirmations), sinon caches derriere la page du Carnet.
 */
public final class ClanUiClient {

    /** Dernieres fiches recues, par nom ({@code sheet}, {@code members}, ... {@code stats}, {@code clans}, {@code card}, {@code peoples}). */
    static final Map<String, CompoundTag> SHEETS = new HashMap<>();
    /** Version du cache : augmente a chaque fiche recue. */
    static int version;
    /** Bandeau des reponses (le plus recent en dernier). */
    static final Deque<Notice> NOTICES = new ArrayDeque<>();
    private static long lastIntent, skew;

    record Notice(Component text, boolean error, long at) {
    }

    private ClanUiClient() {
    }

    /**
     * 1.0.4-beta (lot 8) : en-tete du Carnet d'identite : clan du joueur, tel que la fiche « sheet » l'a dit ({@code null} : pas encore
     * recue, ou pas de clan) : {nom, etiquette, role, couleur (0..15), solo ("1" ou "")}.
     */
    @Nullable
    public static String[] mine() {
        CompoundTag t = SHEETS.get("sheet");
        if (t == null || !t.getString("error").isEmpty()) {
            return null;
        }
        String name = t.getBoolean("solo") && t.getString("name").isBlank() ? t.getString("solo_of") : t.getString("name");
        return new String[]{name, t.getString("tag"), t.getString("roleName"), Integer.toString(t.getInt("color")), t.getBoolean("solo") ? "1" : ""};
    }

    /** 1.0.4-beta (lot 8) : la fiche « sheet » a-t-elle repondu (avec ou sans clan) ? */
    public static boolean sheetKnown() {
        return SHEETS.containsKey("sheet");
    }

    /** 1.0.4-beta (lot 8) : demande la fiche « sheet » (ouverture du Carnet : l'en-tete montre le clan). */
    public static void askSheet() {
        request("sheet");
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                NotebookScreen.register(new ClanTab());
                RpClient.on(RpActions.CLAN_VIEW, ClanUiClient::view);
                RpClient.on(RpActions.CLAN_NOTICE, ClanUiClient::notice);
                RpClient.on(RpActions.CLAN_STATS, t -> store("stats", t));
                RpClient.on(RpActions.CLAN_DIRECTORY, t -> store(t.getString("kind"), t));
                RpClient.on(RpActions.CLAN_MAP, ClanMapModel::receive);
                //fiches de la faction (FACTION_VIEW sorte « sheet ») : recues par le crochet du client des factions
                fr.pioneers.client.clan.faction.FactionClient.sheetHandler = t -> store("faction:" + t.getString("sheet"), t);
            });
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class Events {

        private Events() {
        }

        /** Messages systeme recus juste apres une intention, quand l'onglet Clan est a l'ecran : recopies dans le bandeau. */
        @SubscribeEvent
        public static void chat(ClientChatReceivedEvent event) {
            if (event instanceof ClientChatReceivedEvent.System s && !s.isOverlay() && System.currentTimeMillis() - lastIntent < 4000
                && ClanTab.visible()) {
                push(event.getMessage(), false);
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
            SHEETS.clear();
            NOTICES.clear();
            ClanMapModel.clear();
            version++;
        }
    }

    // ------------------------------------------------------------------ reception
    private static void store(String key, CompoundTag t) {
        SHEETS.put(key, t);
        if (t.contains("now")) {
            skew = t.getLong("now") - System.currentTimeMillis();
        }
        version++;
    }

    /** Horloge du serveur estimee (les delais des fiches s'y rapportent). */
    static long now() {
        return System.currentTimeMillis() + skew;
    }

    private static void view(CompoundTag t) {
        store(t.getString("sheet"), t);
        if (t.getBoolean("open")) {
            //clic droit sur la Mairie : le Carnet s'ouvre sur l'onglet Clan, a la Fiche
            ClanTab.select("sheet");
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof NotebookScreen nb) {
                nb.open(ClanTab.ID);
            } else {
                RpClient.openNotebook(ClanTab.ID);
            }
        }
    }

    private static void notice(CompoundTag t) {
        boolean error = "error".equals(t.getString("kind"));
        if (error) {
            RpClient.play(RpSounds.BOOK_CLOSE.get(), 1F);
            push(Component.translatable(t.getString("key")), true);
        } else if (NOTICES.isEmpty() || System.currentTimeMillis() - NOTICES.peekLast().at() > 1500) {
            //aucun message detaille du serveur : un simple accuse
            push(Component.translatable("clan.pioneers.ui.done"), false);
        }
    }

    static void push(Component c, boolean error) {
        NOTICES.addLast(new Notice(c, error, System.currentTimeMillis()));
        while (NOTICES.size() > 3) {
            NOTICES.pollFirst();
        }
    }

    @Nullable
    static CompoundTag sheet(String key) {
        return SHEETS.get(key);
    }

    static void clear() {
        SHEETS.clear();
        version++;
    }

    // ------------------------------------------------------------------ envoi
    /** Demande une fiche ({@code CLAN_REQUEST}). */
    static void request(String sheet, @Nullable CompoundTag extra) {
        CompoundTag t = extra == null ? new CompoundTag() : extra.copy();
        t.putString("sheet", sheet);
        RpNet.toServer(RpActions.CLAN_REQUEST, t);
    }

    static void request(String sheet) {
        request(sheet, null);
    }

    /** Intention ({@code CLAN_ACTION}) ; la fiche {@code refresh} revient a jour avec la reponse. */
    static void act(String op, String refresh, CompoundTag args) {
        CompoundTag t = args.copy();
        t.putString("op", op);
        t.putString("sheet", refresh);
        lastIntent = System.currentTimeMillis();
        RpNet.toServer(RpActions.CLAN_ACTION, t);
    }

    /** Intention sur la carte ({@code CLAN_MAP_REQUEST} act). */
    static void mapAct(String act, int x, int z, @Nullable CompoundTag extra) {
        CompoundTag t = extra == null ? new CompoundTag() : extra.copy();
        t.putString("op", "act");
        t.putString("act", act);
        t.putInt("x", x);
        t.putInt("z", z);
        lastIntent = System.currentTimeMillis();
        RpNet.toServer(RpActions.CLAN_MAP_REQUEST, t);
    }

    /**
     * 1.9.2 (retours de Guillaume) : zone tracee sur la carte, coins absolus (x0, z0) et (x1, z1) ; {@code apply} faux = apercu seul. Un
     * paquet par geste (jamais une rafale de claims).
     */
    static void mapArea(int x0, int z0, int x1, int z1, boolean unclaim, boolean apply) {
        CompoundTag t = new CompoundTag();
        t.putString("op", apply ? "area_apply" : "area");
        t.putInt("x", x0);
        t.putInt("z", z0);
        t.putInt("x1", x1);
        t.putInt("z1", z1);
        t.putBoolean("unclaim", unclaim);
        lastIntent = System.currentTimeMillis();
        RpNet.toServer(RpActions.CLAN_MAP_REQUEST, t);
    }

    static void mapWatch(boolean on, boolean fresh) {
        CompoundTag t = new CompoundTag();
        t.putString("op", on ? "open" : "close");
        t.putBoolean("fresh", fresh);
        RpNet.toServer(RpActions.CLAN_MAP_REQUEST, t);
    }
}
