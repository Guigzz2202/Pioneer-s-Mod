package fr.pioneers.core.profile;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.network.PacketGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP) : vie du profil cote serveur : envoi a la connexion, a la reapparition et au changement de dimension ; identite aux joueurs
 * qui commencent a suivre ; initiation (ouverte tant qu'elle n'est pas validee, joueur protege pendant ce temps) ; actions du client
 * (etapes de l'initiation) ; gains d'XP regroupes toutes les 10 ticks.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ProfileEvents {

    public static final int NAME_MAX = 24;

    private ProfileEvents() {
    }

    static {
        RpNet.onServer(RpActions.INIT_STEP, ProfileEvents::initStep);
        RpNet.onServer(RpActions.INIT_SUBMIT, ProfileEvents::initSubmit);
        RpNet.onServer(RpActions.REQUEST_PROFILE, ProfileEvents::requestProfile);
    }

    /** Charge la classe (et ses traitements reseau) depuis le constructeur du mod. */
    public static void init() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PioneerProfile p = PioneerCap.get(player);
        if (p.name.isEmpty()) {
            p.name = player.getGameProfile().getName();
        }
        ProfileSync.defs(player);
        ProfileSync.full(player);
        ProfileSync.identity(player);
        if (!p.initiated) {
            if (PiCommonConfig.get(RpConfig.INITIATION, true)) {
                openInitiation(player);
            } else if (!player.isDeadOrDying()) {
                finish(player, p);
            }
        }
        //1.9 (MAJ RP, lot 19) : progres du pionnier deja merites (profil d'avant les progres)
        PioneerApi.advancements(player, p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProfileSync.full(player);
            ProfileSync.identity(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProfileSync.full(player);
            ProfileSync.identity(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            ProfileSync.identityTo(viewer, target);
        }
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            ProfileSync.defs(event.getPlayer());
        } else {
            for (ServerPlayer p : event.getPlayerList().getPlayers()) {
                ProfileSync.defs(p);
                PioneerCap.get(p).touch();
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        int t = event.getServer().getTickCount();
        if (t % 10 == 0) {
            PioneerApi.Notifier.flush(event.getServer());
        }
        if (t % 100 == 0 && PiCommonConfig.get(RpConfig.INITIATION, true)) {
            //L'initiation se rouvre tant qu'elle n'est pas validee (ecran ferme de force, client qui triche). 1.0.4-beta (lot 8) : jamais
            //pour un joueur mort (ecran de mort) ni sans profil reel : openInitiation le verifie
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                openInitiation(p);
            }
        }
    }

    /** Pendant l'initiation, le joueur ne subit aucun degat (il lit, il ne joue pas encore). */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !PioneerCap.get(p).initiated && PiCommonConfig.get(RpConfig.INITIATION, true)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PioneerApi.AntiCheat.forget(event.getEntity().getUUID());
        PioneerApi.Notifier.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        PioneerApi.AntiCheat.clear();
    }

    // ------------------------------------------------------------------ initiation
    /**
     * 1.0.4-beta (lot 8) : un joueur vivant, present dans le monde, dont le profil REEL ({@link PioneerCap#find}) existe et n'est pas
     * initie. Seule porte de l'ecran de creation d'identite : le serveur ne l'ouvre jamais a un joueur qui a deja son identite, ni a un joueur
     * mort (ecran de mort : ses capacites sont invalidees jusqu'a la reapparition).
     */
    public static boolean needsInitiation(ServerPlayer player) {
        if (player.isRemoved() || player.isDeadOrDying() || player.hasDisconnected()) {
            return false;
        }
        PioneerProfile p = PioneerCap.find(player);
        return p != null && !p.initiated;
    }

    static void openInitiation(ServerPlayer player) {
        if (!needsInitiation(player)) {
            return;
        }
        CompoundTag t = new CompoundTag();
        t.putInt("step", PioneerCap.get(player).initStep);
        RpNet.send(player, RpActions.OPEN_INITIATION, t);
    }

    /**
     * {@code REQUEST_PROFILE} : le client redemande son profil (apres une reapparition, ou parce qu'il croit ne pas etre initie). Avec
     * {@code open} (touche J, objet Carnet, lien « En savoir plus » quand le profil local dit « non initie ») : le SERVEUR tranche : Carnet
     * ({@code OPEN_NOTEBOOK}, onglet {@code tab}) si l'identite existe, initiation sinon. 1.0.4-beta (lot 8).
     */
    private static void requestProfile(ServerPlayer player, CompoundTag t) {
        ProfileSync.full(player);
        if (!t.getBoolean("open")) {
            return;
        }
        PioneerProfile p = PioneerCap.find(player);
        if (p == null || player.isDeadOrDying()) {
            return;
        }
        if (p.initiated || !PiCommonConfig.get(RpConfig.INITIATION, true)) {
            CompoundTag o = new CompoundTag();
            o.putString("tab", PacketGuard.sanitize(t.getString("tab"), 32));
            RpNet.send(player, RpActions.OPEN_NOTEBOOK, o);
        } else {
            openInitiation(player);
        }
    }

    /** Nom du personnage filtre (lettres, chiffres, espace, tiret, apostrophe, point), ou null s'il est refuse. */
    public static String cleanName(String raw) {
        String s = PacketGuard.sanitize(raw == null ? "" : raw, NAME_MAX);
        if (s.isEmpty()) {
            return null;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'' || c == '.')) {
                return null;
            }
        }
        return s;
    }

    /** Vocation choisissable (connue, et son interrupteur actif). */
    public static boolean choosable(String vocation) {
        PioneerDefs.Vocation v = PioneerDefs.get().vocation(vocation);
        return v != null && PioneerApi.hasFeature(v.feature());
    }

    private static void initStep(ServerPlayer player, CompoundTag t) {
        //1.0.4-beta (lot 8) : seulement dans le profil REEL d'un joueur vivant (jamais dans un profil de repli)
        PioneerProfile p = PioneerCap.find(player);
        if (p == null || p.initiated || player.isDeadOrDying()) {
            return;
        }
        if (t.contains("name")) {
            String n = cleanName(t.getString("name"));
            if (n != null) {
                p.name = n;
            }
        }
        if (t.contains("vocation") && choosable(t.getString("vocation"))) {
            p.vocation = t.getString("vocation");
        }
        p.initStep = Math.max(0, Math.min(2, t.getInt("step")));
        p.touch();
    }

    private static void initSubmit(ServerPlayer player, CompoundTag t) {
        PioneerProfile p = PioneerCap.find(player);
        if (p == null || player.isDeadOrDying()) {
            return;
        }
        if (p.initiated) {
            //1.0.4-beta (lot 8) : un ecran d'initiation reste ouvert chez un joueur deja initie : on le ferme (profil a jour + « initiated »)
            ProfileSync.full(player);
            CompoundTag r = new CompoundTag();
            r.putString("kind", "initiated_already");
            RpNet.send(player, RpActions.TOAST, r);
            return;
        }
        String name = cleanName(t.getString("name"));
        String vocation = t.getString("vocation");
        if (name == null || !choosable(vocation) || !t.getBoolean("read")) {
            CompoundTag r = new CompoundTag();
            r.putString("kind", "refused");
            r.putString("why", name == null ? "name" : !choosable(vocation) ? "vocation" : "read");
            RpNet.send(player, RpActions.TOAST, r);
            openInitiation(player);
            return;
        }
        p.name = name;
        String before = p.vocation;
        p.vocation = vocation;
        finish(player, p);
        MinecraftForge.EVENT_BUS.post(new VocationChangedEvent(player, before, vocation));
    }

    private static void finish(ServerPlayer player, PioneerProfile p) {
        long day = PioneerApi.day(player.getServer());
        p.initiated = true;
        p.initStep = 2;
        p.createdDay = day;
        p.journal(day, "pioneer.pioneers.journal.initiated", p.name);
        if (!p.notebookGiven) {
            p.notebookGiven = true;
            ItemStack book = new ItemStack(RpItems.NOTEBOOK.get());
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        }
        p.touch();
        ProfileSync.full(player);
        ProfileSync.identity(player);
        CompoundTag r = new CompoundTag();
        r.putString("kind", "initiated");
        RpNet.send(player, RpActions.TOAST, r);
        player.displayClientMessage(Component.translatable("pioneer.pioneers.welcome", p.name), false);
    }
}
