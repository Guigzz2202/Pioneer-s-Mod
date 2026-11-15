package fr.pioneers.clan.war;

import fr.pioneers.clan.Clan;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 1.9 (MAJ RP, bloc 4) : une guerre, rangee telle quelle dans {@code ClanData.wars}. Format lu par {@code Wars.atWar} (garde tel quel) :
 * {@code a} attaquant, {@code b} defenseur (clan ou faction : guerre federale, {@code fa}/{@code fb}), {@code start} fin du preavis (ms),
 * {@code end} fin de la guerre (0 = en cours). S'y ajoutent : {@code id}, {@code declared}, {@code reason}, captures (total, du jour reel,
 * derniere), chunks pris ({@code taken} : "dimension|chunk"), territoire du defenseur a l'ouverture ({@code bSize}), cessez-le-feu proposes
 * ({@code cfA}/{@code cfB}), issue ({@code result}), treve ({@code truce}), tribut, chunks pris hors budget a liberer ({@code free}).
 */
public final class War {

    public static final String CEASEFIRE = "ceasefire", SURRENDER = "surrender", EXPIRED = "expired", ADMIN = "admin", DISSOLVED = "dissolved";

    public final CompoundTag tag;

    public War(CompoundTag tag) {
        this.tag = tag;
    }

    public static War create(UUID a, boolean federalA, UUID b, boolean federalB, String reason, long now, long start) {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", UUID.randomUUID());
        t.putUUID("a", a);
        t.putUUID("b", b);
        t.putBoolean("fa", federalA);
        t.putBoolean("fb", federalB);
        t.putString("reason", reason);
        t.putLong("declared", now);
        t.putLong("start", start);
        t.putLong("end", 0);
        t.putInt("captures", 0);
        return new War(t);
    }

    public UUID id() {
        return tag.hasUUID("id") ? tag.getUUID("id") : new UUID(0, 0);
    }

    @Nullable
    public UUID a() {
        return tag.hasUUID("a") ? tag.getUUID("a") : null;
    }

    @Nullable
    public UUID b() {
        return tag.hasUUID("b") ? tag.getUUID("b") : null;
    }

    public boolean federalA() {
        return tag.getBoolean("fa");
    }

    public boolean federalB() {
        return tag.getBoolean("fb");
    }

    public String reason() {
        return tag.getString("reason");
    }

    public long declared() {
        return tag.getLong("declared");
    }

    public long start() {
        return tag.getLong("start");
    }

    public long end() {
        return tag.getLong("end");
    }

    public boolean ended() {
        return end() > 0;
    }

    /** Ouverte : preavis ecoule, pas finie. */
    public boolean open(long now) {
        return !ended() && start() <= now;
    }

    /** En preavis (declaree, pas encore ouverte). */
    public boolean notice(long now) {
        return !ended() && start() > now;
    }

    /** Active au sens des limites (preavis ou ouverte). */
    public boolean live() {
        return !ended();
    }

    public long truce() {
        return tag.getLong("truce");
    }

    public boolean inTruce(long now) {
        return ended() && truce() > now;
    }

    public int captures() {
        return tag.getInt("captures");
    }

    public long lastCapture() {
        return tag.getLong("last");
    }

    public int bSize() {
        return tag.getInt("bSize");
    }

    public String result() {
        return tag.getString("result");
    }

    // ------------------------------------------------------------------ camps
    /** Le clan est-il du cote attaquant (lui-meme ou sa faction) ? */
    public boolean attacker(Clan c) {
        return side(a(), c);
    }

    public boolean defender(Clan c) {
        return side(b(), c);
    }

    public boolean involves(Clan c) {
        return attacker(c) || defender(c);
    }

    private static boolean side(@Nullable UUID id, Clan c) {
        return id != null && (id.equals(c.id) || c.faction != null && id.equals(c.faction));
    }

    /** Captures du jour reel {@code day} (remises a zero au changement de jour). */
    public int capturesOn(long day) {
        return tag.getLong("capDay") == day ? tag.getInt("capToday") : 0;
    }

    public void countCapture(long day, long now, String chunkKey) {
        if (tag.getLong("capDay") != day) {
            tag.putLong("capDay", day);
            tag.putInt("capToday", 0);
        }
        tag.putInt("capToday", tag.getInt("capToday") + 1);
        tag.putInt("captures", captures() + 1);
        tag.putLong("last", now);
        ListTag l = tag.getList("taken", Tag.TAG_STRING);
        l.add(StringTag.valueOf(chunkKey));
        tag.put("taken", l);
    }

    public boolean taken(String chunkKey) {
        for (Tag x : tag.getList("taken", Tag.TAG_STRING)) {
            if (x.getAsString().equals(chunkKey)) {
                return true;
            }
        }
        return false;
    }

    /** Pertes du defenseur dans cette guerre. */
    public int lost() {
        return captures();
    }

    public void finish(String result, long now, long truceMs) {
        tag.putLong("end", now);
        tag.putString("result", result);
        tag.putLong("truce", now + truceMs);
    }

    public static String chunkKey(String dim, long chunk) {
        return dim + "|" + chunk;
    }
}
