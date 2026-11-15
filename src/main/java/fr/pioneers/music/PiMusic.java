package fr.pioneers.music;

import fr.pioneers.Pioneers;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.item.ItemWithDescription;
import fr.pioneers.registries.PiSounds;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.RecordItem;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.6 : musiques de la Terre et disques Pioneer's (demande de Guillaume : « ajouter 10 musiques de jeu et 10 CD »). Dix pieces composees
 * pour le mod qui suivent la progression (aube, ferme, moulins, forge, vapeur, industrie, chimie, rail et mer, atome, nuit etoilee) :
 * <ul>
 *     <li>en fond dans l'Overworld, a la place d'une partie des musiques vanilla (client : {@code fr.pioneers.client.music.EarthMusic},
 *     reglages {@code PiClientConfig.EARTH_MUSIC*}) ;</li>
 *     <li>sur disque : un disque par piece, grave a l'etabli du luthier dans un disque vierge (gomme-laque : colophane, cire, charbon ;
 *     ou plastique primitif), butin rare de certains coffres et des creepers tues par un squelette.</li>
 * </ul>
 * Tout est produit par {@code tools/gc/music/pioneers_tracks.py} (sons, textures, modeles, recettes, butins, tags, langue, catalogue
 * {@link GeneratedMusic}).
 */
public final class PiMusic {

    private PiMusic() {
    }

    public static void init() {
    }

    /** Disque vierge (a graver a l'etabli du luthier). */
    public static final RegistryObject<Item> BLANK_DISC = PiCoreRegistries.ITEMS.register("blank_music_disc",
          () -> new ItemWithDescription(new Item.Properties().stacksTo(16)));

    /** Sons des disques (juke-box, fichiers mono : le son s'attenue avec la distance). */
    public static final Map<String, RegistryObject<SoundEvent>> DISC_SOUNDS = new LinkedHashMap<>();
    /** Disques, dans l'ordre de la progression. */
    public static final Map<String, RegistryObject<Item>> DISCS = new LinkedHashMap<>();

    static {
        for (MusicTrack track : GeneratedMusic.TRACKS) {
            String name = "music_disc." + track.id();
            RegistryObject<SoundEvent> sound = PiSounds.SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Pioneers.rl(name)));
            DISC_SOUNDS.put(track.id(), sound);
            DISCS.put(track.id(), PiCoreRegistries.ITEMS.register(track.disc(), () -> new RecordItem(track.comparator(), sound,
                  new Item.Properties().stacksTo(1).rarity(Rarity.RARE), track.seconds() * 20)));
        }
    }
}
