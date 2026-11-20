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

    private record Shot(int mega, String name, double sunAlt, boolean working, double yawOffset, double pitch, int climb) {

        Shot(int mega, String name, double sunAlt, boolean working, double yawOffset, double pitch) {
            this(mega, name, sunAlt, working, yawOffset, pitch, 0);
        }
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
        ResourceKey<Level> dim = dimension(shot.mega);
        switch (stage) {
            case 0 -> {
                if (shot.mega <= -1000) {
                    //1.0.7 (lot 12) : extraordinaire bati en l'air (structure MKV), le joueur devant lui ; rendu force en marche
                    fr.pioneers.tech.industry.star.Stars.forceActive = shot.working;
                    server.execute(() -> buildStar(server, shot));
                    stage = 2;
                    //tronçons a charger et structure a former (le client de la CI est lent) : le temps de plusieurs secondes
                    wait = 500;
                    return;
                }
                //deblocage, creatif volant, en altitude au-dessus de l'origine de l'astre
                server.execute(() -> {
                    if (shot.mega >= 0) {
                        MegaWorld.get(server).unlock(server, shot.mega);
                    }
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
                LOG.info("SKYSHOTS : {} ({})", shotId(shot.mega), dim.location());
                stage = 1;
                wait = 400;
            }
            case 1 -> {
                //au sol, une fois les tranches chargees
                if (!mc.level.dimension().equals(dim)) {
                    wait = 40;
                    return;
                }
                //au-dessus du plus haut relief alentour (128 blocs, tranches generees par le serveur) : la vue degagee jusqu'a l'horizon
                server.execute(() -> {
                    ServerLevel level = server.getLevel(dim);
                    ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
                    if (level != null && player != null) {
                        int h = level.getMinBuildHeight() + 1;
                        //geantes : pas de sol, le joueur arrive sur le pont d'un aerostat, au-dessus des nuees
                        if (fr.pioneers.space.sky.client.mega.MegaModels.giant(shot.mega)) {
                            h = fr.pioneers.space.giant.Aerostats.DECK_Y - 1;
                        }
                        for (int x = -64; x <= 64 && h < fr.pioneers.space.giant.Aerostats.DECK_Y - 1; x += 4) {
                            for (int z = -64; z <= 64; z += 4) {
                                h = Math.max(h, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
                            }
                        }
                        LOG.info("SKYSHOTS : vue a la hauteur {}", h + 4);
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, 0.5, h + 4, 0.5, 0, 0);
                    }
                });
                stage = 2;
                wait = 100;
            }
            case 2 -> {
                //heure, fonctionnement, regard
                long dayTime = dayTime(mc, shot);
                server.execute(() -> {
                    server.overworld().setDayTime(dayTime);
                    ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
                    if (shot.climb > 0 && player != null && player.level() instanceof ServerLevel level) {
                        //vue en altitude : le joueur monte, la megastructure doit rester posee sur le relief
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                        player.teleportTo(level, player.getX(), player.getY() + shot.climb, player.getZ(), 0, 0);
                    }
                });
                MegaClient.force(shot.working && shot.mega >= 0 ? 1L << shot.mega : 0);
                stage = 3;
                wait = 90;
            }
            default -> {
                //un ecran ouvert (carnet du premier passage...) cacherait le ciel : on le ferme et on attend
                if (mc.screen != null) {
                    mc.setScreen(null);
                    wait = 20;
                    return;
                }
                String file = String.format("sky_%s_%d_%s.png", shotId(shot.mega), index, shot.name);
                Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> {
                });
                LOG.info("SKYSHOTS : capture {}", file);
                index++;
                stage = index < shots.size() && shots.get(index).mega == shot.mega ? 2 : 0;
                wait = 10;
            }
        }
    }

    /** Monde ou la voir : celui de son astre ; l'Essaim depuis Mercure, l'Observatoire du Halo depuis la Terre. */
    private static ResourceKey<Level> dimension(int mega) {
        if (mega <= -1000) {
            return Level.OVERWORLD;
        }
        if (mega < 0) {
            return far(mega).dimension();
        }
        String body = Megastructures.body(mega).name().toLowerCase();
        return switch (body) {
            case "sun" -> ResourceKey.create(Registries.DIMENSION, Pioneers.rl("mercury"));
            case "earth" -> Level.OVERWORLD;
            default -> ResourceKey.create(Registries.DIMENSION, Pioneers.rl(body));
        };
    }

    /** Regard de la vue : azimut de la megastructure (repere du ciel : 0 = nord, sens horaire) plus decalage, hauteur (NaN : la sienne). */
    private static void view(Minecraft mc, Shot shot) {
        if (shot.mega <= -1000) {
            //extraordinaire : regard vers le centre de la structure (STAR : centre x, y, z), du point de vue de la capture
            double dx = STAR[0] - mc.player.getX(), dy = STAR[1] - mc.player.getEyeY(), dz = STAR[2] - mc.player.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            mc.player.setYHeadRot(yaw);
            mc.player.yHeadRotO = yaw;
            return;
        }
        fr.pioneers.space.sky.client.mega.MegaView v = MegaSkyRenderer.view();
        double az = shot.mega < 0 ? shot.yawOffset : MegaModels.viewYaw(shot.mega, v) + shot.yawOffset;
        double p = Double.isNaN(shot.pitch) ? MegaModels.viewPitch(shot.mega, v) : shot.pitch;
        float yaw = (float) (az + 180), pitch = (float) -p;
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        mc.player.yRotO = yaw;
        mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
        mc.player.yHeadRotO = yaw;
    }

    /** Heure du monde ou le Soleil de l'astre est a la hauteur voulue l'apres-midi (meme calcul que le ciel). */
    private static long dayTime(Minecraft mc, Shot shot) {
        if (shot.mega < 0) {
            return (long) shot.sunAlt;
        }
        if (shot.sunAlt >= NOON) {
            return 6000;
        }
        if (shot.sunAlt <= MIDNIGHT) {
            return 18000;
        }
        SkyScene.Observer obs;
        try {
            obs = SkyScene.observer(Celestial.Body.valueOf(Megastructures.body(shot.mega).name()));
        } catch (IllegalArgumentException e) {
            //exoplanetes (1.0.7 lot 11) : pas d'astre de la scene, ciel propre
            obs = null;
        }
        double[] g = new double[3];
        for (long t = 6000; t <= 18000; t += 5) {
            double timeDeg = mc.level.dimensionType().timeOfDay(t) * 360;
            if (obs == null) {
                //geantes, Soleil, station : ciel simple, soleil (-sin t, cos t, 0) comme MegaSkyRenderer.renderGiant
                if (Math.toDegrees(Math.asin(Math.cos(Math.toRadians(timeDeg)))) < shot.sunAlt) {
                    return t;
                }
                continue;
            }
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
            double pitch = Double.NaN;
            list.add(new Shot(i, "midi", NOON, false, 0, pitch));
            list.add(new Shot(i, "midi_horizon", NOON, false, 0, 8));
            list.add(new Shot(i, "midi_de_biais", NOON, false, 50, 22));
            list.add(new Shot(i, "fin_apres_midi", 10, false, 0, pitch));
            list.add(new Shot(i, "crepuscule", -3, false, 0, pitch));
            list.add(new Shot(i, "crepuscule_en_marche", -3, true, 0, pitch));
            list.add(new Shot(i, "crepuscule_en_marche_de_biais", -3, true, 50, 22));
            list.add(new Shot(i, "nuit", MIDNIGHT, false, 0, pitch));
            list.add(new Shot(i, "nuit_en_marche", MIDNIGHT, true, 0, pitch));
            list.add(new Shot(i, "en_altitude", NOON, true, 0, 10, 90));
        }
        //1.0.7 (lot 12) : multiblocs extraordinaires (★) batis en l'air au-dessus de la Terre, vus en marche : mega = -(1000 + index)
        if (("," + PLAN + ",").contains(",extraordinaires,")) {
            for (int i = 0; i < fr.pioneers.tech.industry.star.GeneratedStars.ALL.length; i++) {
                list.add(new Shot(-1000 - i, "en_marche", 6000, true, 0, 0));
                list.add(new Shot(-1000 - i, "nuit_en_marche", 18000, true, 40, 0));
            }
        }
        //1.0.7 (lot 11) : ciels des planetes des systemes lointains (pulsar, naine blanche, trou noir) : mega = -(1 + astre) ; sunAlt = heure
        //du monde ; regard (azimut, hauteur) vers l'etoile (memes reglages que J:client/PiClientRegistration)
        if ("all".equals(PLAN) || ("," + PLAN + ",").contains(",lointains,")) {
            for (Object[] f : FAR_VIEWS) {
                int code = -(1 + ((fr.pioneers.space.SolarSystem.Body) f[0]).ordinal());
                double yaw = ((Number) f[1]).doubleValue(), pitch = ((Number) f[2]).doubleValue();
                boolean fixed = (Boolean) f[3];
                list.add(new Shot(code, "ciel", 6000, false, yaw, pitch));
                list.add(new Shot(code, fixed ? "de_biais" : "matin", fixed ? 6000 : 1000, false, fixed ? yaw + 35 : 90, fixed ? pitch * 0.6 : 12));
                list.add(new Shot(code, "nuit", 18000, false, yaw + 180, 35));
            }
        }
        return list;
    }

    /** Centre de l'extraordinaire de la capture en cours (monde) ; ecrit par le serveur integre, lu par le client (meme processus). */
    private static final double[] STAR = new double[3];

    /**
     * 1.0.7 (lot 12) : bati l'extraordinaire de la capture a 150 blocs au-dessus de la Terre (plan du catalogue, toute la structure MKV, le
     * controleur face au nord) et place le joueur au nord, en hauteur ; {@code yawOffset} tourne le point de vue autour de la structure.
     */
    private static void buildStar(net.minecraft.server.MinecraftServer server, Shot shot) {
        int si = -1000 - shot.mega;
        fr.pioneers.tech.industry.IndustryMachine m = fr.pioneers.tech.industry.IndustryMachine.byKey(
              fr.pioneers.tech.industry.star.GeneratedStars.ALL[si].key());
        net.minecraft.server.level.ServerLevel level = server.overworld();
        net.minecraft.core.Direction front = net.minecraft.core.Direction.NORTH;
        java.util.Map<net.minecraft.core.BlockPos, fr.pioneers.core.common.projector.ProjectedStructure.Ghost> plan =
              fr.pioneers.tech.industry.multi.IndustrialBlueprint.of(m).layout(0, net.minecraft.core.BlockPos.ZERO, front.getOpposite());
        net.minecraft.core.BlockPos c = new net.minecraft.core.BlockPos(4000 + 96 * si, 150, 4000);
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        fr.pioneers.logistics.transport.CableTier mk5 = fr.pioneers.logistics.transport.CableTier.STELLAR;
        for (var e : plan.entrySet()) {
            net.minecraft.world.level.block.state.BlockState st = e.getValue().state();
            if (st == null) {
                continue;
            }
            net.minecraft.core.BlockPos p = c.offset(e.getKey());
            lo[0] = Math.min(lo[0], p.getX());
            lo[1] = Math.min(lo[1], p.getY());
            lo[2] = Math.min(lo[2], p.getZ());
            hi[0] = Math.max(hi[0], p.getX());
            hi[1] = Math.max(hi[1], p.getY());
            hi[2] = Math.max(hi[2], p.getZ());
            if (p.equals(c) && st.hasProperty(fr.pioneers.core.machine.MachineBlock.FACING)) {
                st = st.setValue(fr.pioneers.core.machine.MachineBlock.FACING, front);
            }
            if (st.getBlock() instanceof fr.pioneers.tech.industry.multi.CasingBlock) {
                st = fr.pioneers.tech.industry.PiIndustry.CASINGS.get(mk5).getBlock().defaultBlockState();
            } else if (st.getBlock() instanceof fr.pioneers.tech.industry.multi.IndustrialGlassBlock) {
                st = fr.pioneers.tech.industry.PiIndustry.GLASSES.get(mk5).getBlock().defaultBlockState();
            } else if (st.getBlock() instanceof fr.pioneers.tech.industry.multi.StructureFrameBlock) {
                st = fr.pioneers.tech.industry.PiIndustry.FRAMES.get(mk5).getBlock().defaultBlockState();
            } else if (st.getBlock() instanceof fr.pioneers.tech.industry.multi.SheetMetalBlock) {
                st = fr.pioneers.tech.industry.PiIndustry.SHEETS.get(mk5).getBlock().defaultBlockState();
            }
            level.setBlock(p, st, 3);
        }
        STAR[0] = (lo[0] + hi[0] + 1) / 2.0;
        STAR[1] = (lo[1] + hi[1] + 1) / 2.0;
        STAR[2] = (lo[2] + hi[2] + 1) / 2.0;
        double span = Math.max(hi[0] - lo[0], Math.max(hi[1] - lo[1], hi[2] - lo[2])) + 1;
        double dist = span * 1.25 + 6, a = Math.toRadians(shot.yawOffset);
        double ex = STAR[0] + Math.sin(a) * dist, ez = STAR[2] - Math.cos(a) * dist, ey = STAR[1] + span * 0.35;
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
        if (player != null) {
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, ex, ey, ez, 0, 0);
        }
        level.setDayTime((long) shot.sunAlt);
        server.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
        LOG.info("SKYSHOTS : extraordinaire {} bati en {}", m.key, c);
    }

    /** Planetes lointaines : astre, azimut et hauteur du regard vers l'etoile a midi, etoile fixe (rotation synchrone). */
    private static final Object[][] FAR_VIEWS = {
          {fr.pioneers.space.SolarSystem.Body.DRAUGR, 0, 72, false}, {fr.pioneers.space.SolarSystem.Body.POLTERGEIST, 0, 75, false},
          {fr.pioneers.space.SolarSystem.Body.PHOBETOR, 0, 78, false}, {fr.pioneers.space.SolarSystem.Body.VAN_MAANEN_B, 240, 30, true},
          {fr.pioneers.space.SolarSystem.Body.VAN_MAANEN_C, 0, 75, false}, {fr.pioneers.space.SolarSystem.Body.VAN_MAANEN_D, 0, 78, false},
          {fr.pioneers.space.SolarSystem.Body.GAIA_BH1_B, 110, 24, true}, {fr.pioneers.space.SolarSystem.Body.GAIA_BH1_C, 0, 70, false},
          {fr.pioneers.space.SolarSystem.Body.GAIA_BH1_D, 0, 76, false}, {fr.pioneers.space.SolarSystem.Body.FYLGJA, 0, 70, false},
          {fr.pioneers.space.SolarSystem.Body.VAN_MAANEN_E, 0, 70, false}, {fr.pioneers.space.SolarSystem.Body.GAIA_BH1_E, 0, 70, false}};

    private static fr.pioneers.space.SolarSystem.Body far(int code) {
        return fr.pioneers.space.SolarSystem.Body.values()[-code - 1];
    }

    private static String shotId(int mega) {
        if (mega <= -1000) {
            return fr.pioneers.tech.industry.star.GeneratedStars.ALL[-1000 - mega].key();
        }
        return mega < 0 ? far(mega).name().toLowerCase() : Megastructures.id(mega);
    }
}
