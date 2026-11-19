package fr.pioneers.space.sky.client;

import com.mojang.logging.LogUtils;
import fr.pioneers.Pioneers;
import fr.pioneers.space.mega.MegaWorld;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.mega.client.MegaClient;
import fr.pioneers.space.sky.client.mega.MegaModels;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 1.0.7 (« ciel grandiose ») : CAPTURES DU CIEL en jeu, pour les essais sans ecran (CI, job « ciel ») : actif seulement avec la propriete
 * systeme {@code pioneers.skyshots} (Gradle : {@code -PskyShots=mars_elevator,mercury_diadem}, ou {@code all} : toutes les megastructures
 * qui ont leur modele en 3D). Dans un monde solo deja ouvert, pour chaque megastructure : deblocage, joueur en creatif volant sur son
 * astre (au-dessus du relief alentour), puis une serie de vues (midi, fin d'apres-midi, crepuscule, nuit ; au repos et en fonctionnement force) capturees dans
 * {@code run/screenshots/sky_<id>_<n>_<vue>.png}, interface masquee. Le journal finit par « SKYSHOTS : FIN ».
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyShots {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String PLAN = System.getProperty("pioneers.skyshots");
    /** Hauteurs du Soleil : midi et minuit (codes), sinon degres (apres-midi). */
    private static final double NOON = 99, MIDNIGHT = -99;

    private record Shot(int mega, String name, double sunAlt, boolean working, double yawOffset, double pitch) {
    }

    private static List<Shot> shots;
    private static int index, stage, wait;
    private static boolean done;

    private SkyShots() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (PLAN == null || done || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null) {
            return;
        }
        if (shots == null) {
            shots = plan();
            index = 0;
            stage = 0;
            wait = 200;
            LOG.info("SKYSHOTS : {} captures prevues", shots.size());
        }
        mc.options.hideGui = true;
        if (mc.screen != null) {
            //ecrans d'accueil (carnet de premiere connexion...) : fermes, la vue reste au ciel
            mc.setScreen(null);
        }
        if (index >= shots.size()) {
            done = true;
            MegaClient.force(0);
            LOG.info("SKYSHOTS : FIN");
            return;
        }
        Shot shot = shots.get(index);
        if (stage >= 2) {
            view(mc, shot);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Pioneers.rl(Megastructures.body(shot.mega).name().toLowerCase()));
        switch (stage) {
            case 0 -> {
                //deblocage, creatif volant, en altitude au-dessus de l'origine de l'astre
                server.execute(() -> {
                    MegaWorld.get(server).unlock(server, shot.mega);
                    ServerLevel level = server.getLevel(dim);
                    ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
                    if (level != null && player != null) {
                        player.setGameMode(GameType.CREATIVE);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, 0.5, 200, 0.5, 0, 0);
                        level.setWeatherParameters(24000, 0, false, false);
                    }
                    server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                    server.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                });
                LOG.info("SKYSHOTS : {} ({})", Megastructures.id(shot.mega), dim.location());
                stage = 1;
                wait = 400;
            }
            case 1 -> {
                //au sol, une fois les tranches chargees
                if (!mc.level.dimension().equals(dim)) {
                    wait = 40;
                    return;
                }
                //au-dessus du plus haut relief alentour (128 blocs) : la vue degagee jusqu'a l'horizon
                int h = mc.level.getMinBuildHeight() + 1;
                for (int x = -64; x <= 64; x += 4) {
                    for (int z = -64; z <= 64; z += 4) {
                        h = Math.max(h, mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                    }
                }
                double y = h + 4;
                server.execute(() -> {
                    ServerLevel level = server.getLevel(dim);
                    ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
                    if (level != null && player != null) {
                        player.teleportTo(level, 0.5, y, 0.5, 0, 0);
                    }
                });
                stage = 2;
                wait = 100;
            }
            case 2 -> {
                //heure, fonctionnement, regard
                long dayTime = dayTime(mc, shot);
                server.execute(() -> server.overworld().setDayTime(dayTime));
                MegaClient.force(shot.working ? 1L << shot.mega : 0);
                stage = 3;
                wait = 90;
            }
            default -> {
                String file = String.format("sky_%s_%d_%s.png", Megastructures.id(shot.mega), index, shot.name);
                Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> {
                });
                LOG.info("SKYSHOTS : capture {}", file);
                index++;
                stage = index < shots.size() && shots.get(index).mega == shot.mega ? 2 : 0;
                wait = 10;
            }
        }
    }

    /** Regard de la vue : azimut de la megastructure (repere du ciel : 0 = nord, sens horaire) plus decalage, hauteur. */
    private static void view(Minecraft mc, Shot shot) {
        double az = Megastructures.skyParams(shot.mega)[0] + shot.yawOffset;
        float yaw = (float) (az + 180), pitch = (float) -shot.pitch;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
    }

    /** Heure du monde ou le Soleil de l'astre est a la hauteur voulue l'apres-midi (meme calcul que le ciel). */
    private static long dayTime(Minecraft mc, Shot shot) {
        if (shot.sunAlt >= NOON) {
            return 6000;
        }
        if (shot.sunAlt <= MIDNIGHT) {
            return 18000;
        }
        SkyScene.Observer obs = SkyScene.observer(Celestial.Body.valueOf(Megastructures.body(shot.mega).name()));
        double[] g = new double[3];
        for (long t = 6000; t <= 18000; t += 5) {
            double timeDeg = mc.level.dimensionType().timeOfDay(t) * 360;
            SkyScene s = SkyScene.compute(obs, t / 24000.0, timeDeg, 0L);
            if (s.sun == null) {
                break;
            }
            SkyScene.toGround(s.sun.x, s.sun.y, s.sun.z, timeDeg, g);
            double l = Math.sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2]);
            if (Math.toDegrees(Math.asin(g[1] / l)) < shot.sunAlt) {
                return t;
            }
        }
        return 13000;
    }

    private static List<Shot> plan() {
        List<Shot> list = new ArrayList<>();
        for (int i = 0; i < Megastructures.COUNT; i++) {
            if (!MegaModels.has(i) || !("all".equals(PLAN) || ("," + PLAN + ",").contains("," + Megastructures.id(i) + ","))) {
                continue;
            }
            double pitch = MegaModels.viewPitch(i);
            list.add(new Shot(i, "midi", NOON, false, 0, pitch));
            list.add(new Shot(i, "midi_horizon", NOON, false, 0, 8));
            list.add(new Shot(i, "midi_de_biais", NOON, false, 50, 22));
            list.add(new Shot(i, "fin_apres_midi", 10, false, 0, pitch));
            list.add(new Shot(i, "crepuscule", -3, false, 0, pitch));
            list.add(new Shot(i, "crepuscule_en_marche", -3, true, 0, pitch));
            list.add(new Shot(i, "crepuscule_en_marche_de_biais", -3, true, 50, 22));
            list.add(new Shot(i, "nuit", MIDNIGHT, false, 0, pitch));
            list.add(new Shot(i, "nuit_en_marche", MIDNIGHT, true, 0, pitch));
        }
        return list;
    }
}
