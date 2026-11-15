package fr.pioneers.client.minimap;

import fr.pioneers.farm.crop.Climate;
import fr.pioneers.core.network.PacketEnvironment;
import fr.pioneers.space.environment.EnvironmentSync;
import fr.pioneers.world.season.SeasonTexts;
import fr.pioneers.world.season.Seasons;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 1.12.1 (F1) : textes et couleurs de la ligne de pictogrammes sous la minimap, d'apres le dernier environnement recu du serveur
 * ({@link PacketEnvironment#last()} : climat, oxygene, gravite). Oublie a la deconnexion et au changement de dimension (le serveur renvoie
 * l'etat a la seconde suivante).
 */
final class EnvironmentInfo {

    private EnvironmentInfo() {
    }

    static void clear() {
        PacketEnvironment.clear();
    }

    /** Etat connu (null : rien recu depuis l'arrivee dans la dimension). Le serveur n'envoie qu'aux changements : jamais perime. */
    @Nullable
    static PacketEnvironment get() {
        return PacketEnvironment.last();
    }

    /** « 17 °C · 45 % » (humidite seulement sur Terre). */
    static Component climate(PacketEnvironment e, boolean withHumidity) {
        return withHumidity && e.humidity() >= 0 ? Component.translatable("minimap.pioneers.env.climate_humid", e.temperature(), e.humidity() + " %")
              : Component.translatable("minimap.pioneers.env.climate", e.temperature());
    }

    /**
     * 1.0.5-beta (lot 1) : « 17 °C · 45 % · Automne, j. 3/10 » : la saison et le jour sur la ligne climat (masquee avec elle), dans
     * l'Overworld seulement (les autres dimensions n'ont pas de saisons) ; minimap trop etroite : juste dessous ({@code MinimapHud}).
     */
    /** 1.0.5-beta (lot 1) : « Automne, j. 3/10 » dans l'Overworld, null ailleurs ou tant que le calendrier n'est pas recu. */
    @Nullable
    static Component season() {
        Seasons.Date d = Seasons.now(Minecraft.getInstance().level);
        return d == null ? null : SeasonTexts.date(d);
    }

    static Component climate(PacketEnvironment e, boolean withHumidity, boolean withSeason) {
        Component base = climate(e, withHumidity);
        Seasons.Date d = withSeason ? Seasons.now(Minecraft.getInstance().level) : null;
        return d == null ? base : Component.translatable("minimap.pioneers.env.with_season", base, SeasonTexts.date(d));
    }

    /**
     * 1.0.5-beta (lot 14, Guillaume : « Infos de la carte : humidite et climat du biome ») : ligne climat de la minimap, d'apres la temperature
     * et l'humidite des cultures recues du serveur ({@link PacketEnvironment#climateT} / {@code climateH} = {@code Climate.temperature} /
     * {@code Climate.humidity} au lieu du joueur) : « Tempere 17 °C » (zone de {@link Climate#zone}, comme les graines) et « Humide 72 % »
     * ({@link Climate#humidityZone}). Null hors de la Terre.
     */
    @Nullable
    static Component climateZone(PacketEnvironment e) {
        if (!e.hasClimate()) {
            return null;
        }
        Component zone = Component.translatable("farm.pioneers.zone." + Climate.zone(e.climateT()));
        return Component.translatable("minimap.pioneers.env.climate_zone", capital(zone), EnvironmentSync.celsius(e.climateT()));
    }

    @Nullable
    static Component humidityZone(PacketEnvironment e) {
        if (!e.hasClimate()) {
            return null;
        }
        Component zone = Component.translatable("farm.pioneers.humidity_zone." + Climate.humidityZone(e.climateH()));
        return Component.translatable("minimap.pioneers.env.humidity_zone", capital(zone), Math.round(Math.max(0, Math.min(1, e.climateH())) * 100) + " %");
    }

    /** Premiere lettre en capitale (« tempéré » -> « Tempéré »). */
    static String capital(Component c) {
        String s = c.getString();
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + s.substring(1);
    }

    /** Couleur de la goutte : sec (sable), modere (vert d'eau), humide (bleu). */
    static int humidityColor(float h) {
        int z = Climate.humidityZone(h);
        return z <= 0 ? 0xE0C070 : z == 1 ? 0xD8D08A : z == 2 ? 0x9FD8C0 : z == 3 ? 0x6FB8F0 : 0x4F90F0;
    }

    static Component oxygen(PacketEnvironment e) {
        return Component.translatable("minimap.pioneers.env.oxygen." + switch (e.oxygen()) {
            case PacketEnvironment.AIR -> "air";
            case PacketEnvironment.PRESSURIZED -> "pressurized";
            default -> "none";
        });
    }

    /** « 1,00 g » (virgule en francais). */
    static Component gravity(PacketEnvironment e) {
        String v = String.format(Locale.ROOT, "%.2f", e.gravity());
        if (Minecraft.getInstance().getLanguageManager().getSelected().startsWith("fr")) {
            v = v.replace('.', ',');
        }
        return Component.translatable("minimap.pioneers.env.gravity", v);
    }

    /** Couleur du thermometre : glacial, froid, tempere, chaud, brulant. */
    static int temperatureColor(int celsius) {
        return celsius < -20 ? 0x9FD8FF : celsius < 5 ? 0x7FC8F0 : celsius < 26 ? 0x8FD14F : celsius < 45 ? 0xF0A040 : 0xE5484D;
    }

    static int oxygenColor(PacketEnvironment e) {
        return e.oxygen() == PacketEnvironment.AIR ? 0x8FD14F : e.oxygen() == PacketEnvironment.PRESSURIZED ? 0x5FB8E8 : 0xE5484D;
    }
}
