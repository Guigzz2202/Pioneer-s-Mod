package fr.pioneers.tech.bronze.steam;

import net.minecraft.nbt.CompoundTag;

/**
 * Une reserve de vapeur : contenance (mB), vapeur contenue (mB de gaz {@code pioneers:steam}) et humidite (0 = vapeur seche, 1 = de l'eau)
 * qui suit les melanges. 1.0.2-beta (lot 3, Guillaume : « supprimer la notion de pression ; les machines consomment de la vapeur ») : plus de
 * pression, la vapeur est une QUANTITE : la chaudiere la produit jusqu'a remplir sa reserve, les machines la prennent a debit fixe.
 * Sauvegarde inchangee (cles {@code steam} et {@code wet} : les mB d'avant sont les memes mB) ; la contenance n'est pas sauvee (elle vient de
 * la machine), une reserve d'avant plus pleine que la contenance est ramenee a celle-ci au chargement.
 */
public class SteamVolume {

    private double capacity;
    private double steam;
    private double wet;

    public SteamVolume(double capacity) {
        this.capacity = capacity;
    }

    /** Contenance (mB). */
    public double capacity() {
        return capacity;
    }

    public void setCapacity(double capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /** Vapeur contenue (mB). */
    public double steam() {
        return steam;
    }

    /** Remplissage 0..1 (sert aux echanges entre deux reserves collees et aux jauges). */
    public double fill() {
        return capacity <= 0 ? 0 : steam / capacity;
    }

    /** Place libre (mB). */
    public double room() {
        return Math.max(0, capacity - steam);
    }

    public boolean full() {
        return steam >= capacity - 1e-6;
    }

    public double wet() {
        return wet;
    }

    /** Ajoute jusqu'a {@code amount} mB de vapeur d'humidite {@code wetness} (borne a la contenance) ; renvoie ce qui est entre. */
    public double add(double amount, double wetness) {
        double put = Math.min(amount, room());
        if (put <= 0) {
            return 0;
        }
        double total = steam + put;
        wet = total <= 0 ? 0 : (wet * steam + wetness * put) / total;
        steam = total;
        return put;
    }

    /** Retire jusqu'a {@code amount} ; renvoie ce qui a ete retire. */
    public double take(double amount) {
        double got = Math.max(0, Math.min(amount, steam));
        steam -= got;
        if (steam < 1e-6) {
            steam = 0;
        }
        return got;
    }

    /** Ce qui manque pour une prise de {@code need} mB : 1 = tout est la, 0 = rien. */
    public double supply(double need) {
        return need <= 0 ? 1 : Math.max(0, Math.min(1, steam / need));
    }

    public void dry(double rate) {
        wet = Math.max(0, wet - rate);
    }

    public void clear() {
        steam = 0;
        wet = 0;
    }

    public void save(CompoundTag tag) {
        tag.putDouble("steam", steam);
        tag.putDouble("wet", wet);
    }

    public void load(CompoundTag tag) {
        steam = Math.min(capacity, tag.getDouble("steam"));
        wet = tag.getDouble("wet");
    }
}
