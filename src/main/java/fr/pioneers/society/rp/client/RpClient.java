package fr.pioneers.society.rp.client;

import fr.pioneers.core.profile.PioneerCap;
import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvent;

/**
 * 1.9 (MAJ RP) : cote client des paquets {@code RpNet.S2C}. Le profil du joueur local est range dans SA capacite (memes calculs de
 * modificateurs que le serveur : vitesse de minage predite), l'identite des autres joueurs dans {@link #SEEN}. Les autres blocs de la MAJ
 * enregistrent leurs traitements par {@link #on}.
 */
public final class RpClient {

    /** Nom de personnage, vocation et couleur du clan des joueurs visibles (id d'entite). */
    public static final Map<Integer, Seen> SEEN = new HashMap<>();
    /** Gains a afficher au-dessus de la barre de vie. */
    public static final Deque<Popup> POPUPS = new ArrayDeque<>();
    private static final Map<Integer, Consumer<CompoundTag>> HANDLERS = new HashMap<>();
    public static int brigade;

    /** {@code clanColor} : 1.9 (MAJ RP, bloc 4) teinture du clan du joueur (minimap), -1 sans clan. 1.12.0 : plus d'allure. */
    public record Seen(String name, String vocation, boolean initiated, int clanColor) {
    }

    public record Popup(String text, boolean auto, long born) {
    }

    private RpClient() {
    }

    public static void on(int action, Consumer<CompoundTag> handler) {
        HANDLERS.put(action, handler);
    }

    static {
        on(RpActions.DEFS, PioneerDefs::receive);
        on(RpActions.PROFILE, RpClient::profile);
        on(RpActions.XP_GAIN, RpClient::xp);
        on(RpActions.IDENTITY_OF, t -> SEEN.put(t.getInt("id"), new Seen(t.getString("name"),
              t.getString("vocation"), t.getBoolean("initiated"), t.contains("clanColor") ? t.getInt("clanColor") : -1)));
        on(RpActions.OPEN_INITIATION, t -> {
            //1.0.4-beta (lot 8) : jamais par-dessus l'ecran de mort (le serveur ne l'envoie plus a un joueur mort ; garde du client en plus)
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !mc.player.isDeadOrDying() && !(mc.screen instanceof InitiationScreen)
                && !(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen)) {
                mc.setScreen(new InitiationScreen(t.getInt("step")));
            }
        });
        //1.0.4-beta (lot 8) : le serveur confirme l'identite : le Carnet s'ouvre (demande faite par openNotebook)
        on(RpActions.OPEN_NOTEBOOK, t -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && profile().initiated) {
                String tab = t.getString("tab");
                mc.setScreen(new NotebookScreen(tab.isEmpty() ? null : tab));
            }
        });
        on(RpActions.OPEN_DESK, t -> Minecraft.getInstance().setScreen(new DeskScreen(t)));
        on(RpActions.CEREMONY, t -> {
            if (Minecraft.getInstance().screen instanceof DeskScreen d) {
                d.ceremony(t.getInt("ticks"));
            }
        });
        on(RpActions.TOAST, RpClient::toast);
        //1.9 (bloc 1) : console de perimetre, lecture d'un ecrit
        on(RpActions.CONSOLE_OPEN, t -> Minecraft.getInstance().setScreen(new fr.pioneers.world.ruins.client.ConsoleScreen(t)));
        on(RpActions.CONSOLE_STATE, t -> {
            if (Minecraft.getInstance().screen instanceof fr.pioneers.world.ruins.client.ConsoleScreen c && c.pos() == t.getLong("pos")) {
                c.read(t);
            }
        });
        on(RpActions.RESTORATION_VIEW, t -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof fr.pioneers.world.ruins.client.RestorationScreen r && r.site() == t.getLong("site")) {
                r.read(t);
            } else {
                mc.setScreen(new fr.pioneers.world.ruins.client.RestorationScreen(t));
            }
        });
        on(RpActions.LORE_PAGE, t -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(t.contains("code") ? new LoreScreen(mc.screen, t.getString("id"), t.getString("code")) : new LoreScreen(mc.screen, t.getString("id")));
        });
        //1.9 (bloc 3) : villageois, Tableau des besoins, bulles
        fr.pioneers.society.village.client.VillageClient.handlers();
        //1.9 (bloc 3) : Bourse, gains de jetons, Reputation
        on(RpActions.BALANCE, LedgerTab::onServer);
        //1.0.4-beta (lot 7) : contrats (vue demandee ou poussee apres un changement)
        on(RpActions.CONTRACT_VIEW, fr.pioneers.society.economy.client.ContractsTab::onServer);
        on(RpActions.DOCUMENT_VIEW, fr.pioneers.society.economy.client.DocumentsTab::onServer); //1.0.5-beta (lot 26) : onglet Documents
        //1.0.4-beta (lot 29) : lettres non lues (le « ! » au-dessus des boites aux lettres) ; vue de la partie du guide
        on(RpActions.MAIL_STATE, fr.pioneers.society.economy.client.MailClient::state);
        on(RpActions.PARTY_VIEW, fr.pioneers.guide.client.PartyScreen::onServer);
        on(RpActions.TRADE_VIEW, t -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof fr.pioneers.society.economy.client.TradeScreen ts) {
                ts.read(t);
            } else {
                mc.setScreen(new fr.pioneers.society.economy.client.TradeScreen(t));
            }
        });
        on(RpActions.REPUTATION_VIEW, ReputationTab::onServer);
        //1.0.5-beta (lot 16) : liste des joueurs connus pour le selecteur de joueur
        on(RpActions.PLAYERS_VIEW, fr.pioneers.client.gui.PlayerPicker::onServer);
        //1.9 (bloc 3, etals et comptoirs) : configuration et achat
        on(RpActions.STALL_VIEW, fr.pioneers.society.economy.client.StallClient::view);
        on(RpActions.MONEY_TOAST, t -> {
            long a = t.getLong("a");
            if (a != 0 && RpClientConfig.get(RpClientConfig.XP_POPUPS, true)) {
                String text = net.minecraft.client.resources.language.I18n.get(a > 0 ? "economy.pioneers.popup_plus" : "economy.pioneers.popup_minus",
                      LedgerTab.group(Math.abs(a)));
                POPUPS.addLast(new Popup(text, a < 0, System.currentTimeMillis()));
                while (POPUPS.size() > 5) {
                    POPUPS.pollFirst();
                }
            }
        });
        on(RpActions.REPUTATION_TIER, t -> {
            play(RpSounds.SEAL.get(), t.getBoolean("up") ? 1.1F : 0.8F);
            Minecraft mc = Minecraft.getInstance();
            String name = mc.player == null ? "" : profile().name;
            Toasts.show(net.minecraft.network.chat.Component.translatable("reputation.pioneers.tier_changed", name,
                  net.minecraft.network.chat.Component.translatable("reputation.pioneers.tier." + t.getString("tier")),
                  net.minecraft.network.chat.Component.translatable("people.pioneers." + t.getString("people"))));
        });
        on(RpActions.FEATURES, t -> {
            java.util.Map<String, Boolean> m = new java.util.HashMap<>();
            for (String k : t.getAllKeys()) {
                m.put(k, t.getBoolean(k));
            }
            fr.pioneers.core.profile.PioneerApi.remoteFeatures = m;
        });
    }

    public static void receive(int action, CompoundTag tag) {
        Consumer<CompoundTag> h = HANDLERS.get(action);
        if (h != null) {
            h.accept(tag);
        }
    }

    /** Profil du joueur local (vide hors partie). */
    public static PioneerProfile profile() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? new PioneerProfile() : PioneerCap.get(mc.player);
    }

    private static void profile(CompoundTag t) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            PioneerCap.get(mc.player).deserializeNBT(t);
            //1.0.4-beta (lot 8) : un ecran d'initiation ouvert alors que le serveur dit « initie » se ferme
            if (t.getBoolean("initiated") && mc.screen instanceof InitiationScreen) {
                mc.setScreen(null);
            }
        }
        brigade = t.getInt("brigade");
    }

    /**
     * 1.0.4-beta (lot 8) : reapparition et changement de dimension : Minecraft cree un NOUVEAU joueur local, dont la capacite est vide jusqu'au
     * profil que le serveur envoie juste apres. Le profil de l'ancien joueur local est recopie tout de suite (pas d'instant « non initie »).
     */
    public static void cloned(net.minecraft.world.entity.player.Player oldPlayer, net.minecraft.world.entity.player.Player newPlayer) {
        PioneerProfile from = PioneerCap.find(oldPlayer), to = PioneerCap.find(newPlayer);
        if (from != null && to != null && from != to) {
            to.copyFrom(from);
        }
    }

    private static void xp(CompoundTag t) {
        PioneerProfile p = profile();
        ListTag l = t.getList("gains", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            String skill = c.getString("s");
            p.xp.put(skill, c.getDouble("x"));
            int shown = (int) Math.round(c.getDouble("g"));
            if (shown >= 1 && RpClientConfig.get(RpClientConfig.XP_POPUPS, true)) {
                String name = net.minecraft.client.resources.language.I18n.get("skill.pioneers." + skill);
                String text = net.minecraft.client.resources.language.I18n.get(c.getBoolean("a") ? "pioneer.pioneers.xp_auto" : "pioneer.pioneers.xp_hand",
                      shown, name);
                POPUPS.addLast(new Popup(text, c.getBoolean("a"), System.currentTimeMillis()));
                while (POPUPS.size() > 5) {
                    POPUPS.pollFirst();
                }
            }
        }
        p.touch();
    }

    private static void toast(CompoundTag t) {
        String kind = t.getString("kind");
        switch (kind) {
            case "level" -> {
                play(RpSounds.LEVEL_UP.get(), 1F);
                Toasts.show(net.minecraft.network.chat.Component.translatable("pioneer.pioneers.level_up", t.getInt("level"),
                      net.minecraft.network.chat.Component.translatable("skill.pioneers." + t.getString("skill"))));
            }
            case "mastery" -> {
                play(RpSounds.MASTERY.get(), 1F);
                Toasts.show(net.minecraft.network.chat.Component.translatable("pioneer.pioneers.mastery",
                      net.minecraft.network.chat.Component.translatable("skill.pioneers." + t.getString("skill") + ".mastery")));
            }
            case "cap" -> Toasts.show(net.minecraft.network.chat.Component.translatable("pioneer.pioneers.auto_cap"));
            case "initiated" -> {
                play(RpSounds.SEAL.get(), 1F);
                if (Minecraft.getInstance().screen instanceof InitiationScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
            }
            case "initiated_already" -> {
                //1.0.4-beta (lot 8) : le serveur a deja l'identite : l'ecran d'initiation se ferme sans rien changer
                if (Minecraft.getInstance().screen instanceof InitiationScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
            }
            case "reconverted" -> {
                play(RpSounds.SEAL.get(), 0.9F);
                Toasts.show(net.minecraft.network.chat.Component.translatable("pioneer.pioneers.ceremony"));
                if (Minecraft.getInstance().screen instanceof DeskScreen) {
                    Minecraft.getInstance().setScreen(null);
                }
            }
            case "closed", "refused" -> {
                play(RpSounds.BOOK_CLOSE.get(), 1F);
                if (Minecraft.getInstance().screen instanceof DeskScreen d) {
                    d.cancelled();
                }
            }
            default -> {
                if (t.contains("text")) {
                    Toasts.show(net.minecraft.network.chat.Component.translatable(t.getString("text")));
                }
            }
        }
    }

    public static void play(SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.8F));
    }

    /** Ouvre le Carnet (touche J, objet, lien « En savoir plus »). */
    public static void openNotebook(@Nullable String tab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (!profile().initiated) {
            //1.0.4-beta (lot 8) : le profil local peut etre en retard (reapparition) : c'est le SERVEUR qui decide (Carnet ou initiation)
            CompoundTag t = new CompoundTag();
            t.putBoolean("open", true);
            t.putString("tab", tab == null ? "" : tab);
            fr.pioneers.core.profile.RpNet.toServer(RpActions.REQUEST_PROFILE, t);
            return;
        }
        mc.setScreen(new NotebookScreen(tab));
    }

    public static void clear() {
        SEEN.clear();
        POPUPS.clear();
    }
}
