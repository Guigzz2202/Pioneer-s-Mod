package fr.pioneers.equipment.music.client;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiClientConfig;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.equipment.music.GeneratedMusic;
import fr.pioneers.equipment.music.MusicTrack;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 : musiques de la Terre. Forge 47 n'a pas d'evenement de choix de la musique : on laisse le gestionnaire vanilla decider QUAND jouer
 * (memes silences que d'habitude), et quand il lance une musique de jeu dans l'Overworld ({@code minecraft:music.game},
 * {@code music.creative}, {@code music.overworld.*}), on la remplace une fois sur deux (reglage {@code earthMusicShare}) par une piece
 * Pioneer's choisie selon le moment de la journee, le bord de mer et la progression du joueur (progres du mod deja obtenus).
 * <p>
 * Le gestionnaire vanilla voit sa propre piste « finie » au tick suivant et attend son delai habituel (10 a 20 min) : pas de
 * chevauchement. Notre piste s'efface en fondu si l'on quitte l'Overworld ou si le reglage est coupe ; une musique vanilla qui
 * voudrait demarrer pendant la notre est ignoree.
 * <p>
 * Progression lue cote client sans paquet : {@link ClientAdvancements#setListener} rejoue l'avancement de chaque progres connu a
 * l'ecouteur qu'on lui donne ; on pose un ecouteur le temps de la lecture puis on remet {@code null} (seulement hors de tout ecran,
 * l'ecran des progres etant le seul autre utilisateur de l'ecouteur).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class EarthMusic {

    private EarthMusic() {
    }

    private static final RandomSource RANDOM = RandomSource.create();
    /** Pistes recentes (evitees au tirage). */
    private static final Deque<String> RECENT = new ArrayDeque<>();
    private static final Set<ResourceLocation> DONE = new HashSet<>();
    @Nullable
    private static Track playing;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onSound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof Track || sound.getSource() != SoundSource.MUSIC) {
            return;
        }
        ResourceLocation id = sound.getLocation();
        if (!"minecraft".equals(id.getNamespace()) || !earthMusic(id.getPath())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (playing != null && !playing.isStopped() && minecraft.getSoundManager().isActive(playing)) {
            //Notre piece joue encore : pas de seconde musique par-dessus
            event.setSound(null);
            return;
        }
        if (!PiCommonConfig.get(PiClientConfig.EARTH_MUSIC, true) || RANDOM.nextDouble() >= PiCommonConfig.get(PiClientConfig.EARTH_MUSIC_SHARE, 0.5)) {
            return;
        }
        MusicTrack track = choose(minecraft);
        if (track == null) {
            return;
        }
        playing = new Track(track);
        event.setSound(playing);
        RECENT.addFirst(track.id());
        while (RECENT.size() > 3) {
            RECENT.removeLast();
        }
        if (PiCommonConfig.get(PiClientConfig.EARTH_MUSIC_TITLE, true)) {
            minecraft.gui.setOverlayMessage(Component.translatable("music.pioneers.now_playing",
                  Component.translatable("item.pioneers." + track.disc() + ".desc")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        }
    }

    /** Musiques de jeu de l'Overworld (pas le menu, la fin, le Nether, les credits ni la musique sous l'eau). */
    private static boolean earthMusic(String path) {
        return path.equals("music.game") || path.equals("music.creative") || path.startsWith("music.overworld.");
    }

    /** Tirage pondere parmi les pistes debloquees : moment de la journee, bord de mer, pieces recentes evitees. */
    @Nullable
    private static MusicTrack choose(Minecraft minecraft) {
        long time = minecraft.level.getDayTime() % 24000L;
        int moment = time >= 22800 || time < 1200 ? MusicTrack.DAWN : time < 11800 ? MusicTrack.DAY : time < 13400 ? MusicTrack.DUSK : MusicTrack.NIGHT;
        Holder<Biome> biome = minecraft.level.getBiome(minecraft.player.blockPosition());
        boolean sea = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER);
        boolean progression = PiCommonConfig.get(PiClientConfig.EARTH_MUSIC_PROGRESSION, true);
        if (progression) {
            readProgress(minecraft);
        }
        double total = 0;
        double[] weights = new double[GeneratedMusic.TRACKS.size()];
        for (int pass = 0; pass < 2 && total <= 0; pass++) {
            for (int i = 0; i < weights.length; i++) {
                MusicTrack t = GeneratedMusic.TRACKS.get(i);
                double w = 0;
                if (!progression || unlocked(t)) {
                    //2e passe (rien ne convient a ce moment) : toutes les pistes debloquees
                    w = pass == 1 || (t.moments() & moment) != 0 ? t.weight() : 0;
                    if ((t.favourite() & moment) != 0) {
                        w *= 4;
                    }
                    if (sea && t.sea()) {
                        w *= 4;
                    }
                    if (RECENT.contains(t.id())) {
                        w *= 0.08;
                    }
                }
                weights[i] = w;
                total += w;
            }
        }
        if (total <= 0) {
            return null;
        }
        double r = RANDOM.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return GeneratedMusic.TRACKS.get(i);
            }
        }
        return GeneratedMusic.TRACKS.get(weights.length - 1);
    }

    private static boolean unlocked(MusicTrack track) {
        if (track.unlocks().isEmpty()) {
            return true;
        }
        for (String id : track.unlocks()) {
            if (DONE.contains(Pioneers.rl(id))) {
                return true;
            }
        }
        return false;
    }

    /** Progres obtenus (relus a chaque tirage, hors de tout ecran ; sinon on garde la derniere lecture). */
    private static void readProgress(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.screen != null) {
            return;
        }
        Set<ResourceLocation> done = new HashSet<>();
        ClientAdvancements advancements = connection.getAdvancements();
        try {
            advancements.setListener(new ClientAdvancements.Listener() {
                @Override
                public void onUpdateAdvancementProgress(Advancement advancement, AdvancementProgress progress) {
                    if (progress.isDone()) {
                        done.add(advancement.getId());
                    }
                }

                @Override
                public void onSelectedTabChanged(@Nullable Advancement advancement) {
                }

                @Override
                public void onAddAdvancementRoot(Advancement advancement) {
                }

                @Override
                public void onRemoveAdvancementRoot(Advancement advancement) {
                }

                @Override
                public void onAddAdvancementTask(Advancement advancement) {
                }

                @Override
                public void onRemoveAdvancementTask(Advancement advancement) {
                }

                @Override
                public void onAdvancementsCleared() {
                }
            });
        } finally {
            advancements.setListener(null);
        }
        DONE.clear();
        DONE.addAll(done);
    }

    /** Piece en cours : fondu de sortie si l'on quitte l'Overworld ou si le reglage est coupe en route. */
    private static final class Track extends AbstractTickableSoundInstance {

        Track(MusicTrack track) {
            super(SoundEvent.createVariableRangeEvent(track.background()), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.looping = false;
            this.delay = 0;
            this.volume = 1.0F;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            boolean stay = minecraft.level != null && minecraft.level.dimension() == Level.OVERWORLD && PiCommonConfig.get(PiClientConfig.EARTH_MUSIC, true);
            if (!stay) {
                volume -= 0.025F;
                if (volume <= 0) {
                    volume = 0;
                    stop();
                }
            }
        }
    }
}
