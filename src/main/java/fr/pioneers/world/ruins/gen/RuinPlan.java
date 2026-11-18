package fr.pioneers.world.ruins.gen;

import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ShapeTemplate;
import fr.pioneers.world.ruins.RuinCatalog;
import fr.pioneers.world.ruins.ScrapCategory;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;

/**
 * 1.9 (MAJ RP, bloc 1) : ce que la graine d'un site decide sans lire le monde : multibloc tire dans le catalogue des ruines (tier, taille,
 * poids), orientation (rotation seulement, jamais de miroir : la restauration reconstruit {@code layout(0, controleur, forward)}), position
 * du controleur (la boite du gabarit est centree sur le centre du chunk du site), annexes (sorte, variante, position, orientation). Calcule
 * a l'identique par la feature de la ruine et par celles des annexes.
 */
public final class RuinPlan {

    private static final long SALT_PLAN = 0x504C414EL, SALT_ANNEX = 0x414E4E58L;

    public final RuinGrid.Site site;
    public final String machine;
    public final IndustryMachine industry;
    public final ShapeTemplate template;
    public final ScrapCategory category;
    /** Controleur (x, z du monde) ; {@code forward} : direction du plan ({@code IndustrialBlueprint.layout(0, controleur, forward)}). */
    public final int ctrlX, ctrlZ;
    public final Direction forward;
    /** Emprise du gabarit dans le monde (x, z, bornes comprises, sans la marge). */
    public final int boxMinX, boxMaxX, boxMinZ, boxMaxZ;

    private RuinPlan(RuinGrid.Site site, String machine, IndustryMachine industry, ShapeTemplate template, ScrapCategory category, Direction forward,
                     int ctrlX, int ctrlZ, int[] box) {
        this.site = site;
        this.machine = machine;
        this.industry = industry;
        this.template = template;
        this.category = category;
        this.forward = forward;
        this.ctrlX = ctrlX;
        this.ctrlZ = ctrlZ;
        this.boxMinX = ctrlX + box[0];
        this.boxMaxX = ctrlX + box[1];
        this.boxMinZ = ctrlZ + box[2];
        this.boxMaxZ = ctrlZ + box[3];
    }

    /** Plan du site, ou null (catalogue vide, aucune machine a la bonne taille). */
    @Nullable
    public static RuinPlan of(RuinGrid.Site site, GenSettings settings) {
        GenSettings.Tier ts = settings.tier(site.tier());
        Rng rng = new Rng(Rng.hash(site.seed(), SALT_PLAN));
        List<RuinCatalog.Entry> fits = new ArrayList<>(), loose = new ArrayList<>();
        List<ShapeTemplate> fitT = new ArrayList<>(), looseT = new ArrayList<>();
        int width = settings.maxHalf() * 2 + 1;
        for (RuinCatalog.Entry e : RuinCatalog.entries().values()) {
            if (e.minTier() > site.tier() || e.weight() <= 0) {
                continue;
            }
            IndustryMachine m = IndustryMachine.byKey(e.machine());
            if (m == null) {
                continue;
            }
            List<ShapeTemplate> l = ShapeTemplate.of(m);
            if (l.isEmpty()) {
                continue;
            }
            ShapeTemplate t = l.get(0);
            int height = t.sy + (t.attached ? 2 : 0);
            if (t.sx > width || t.sz > width || height > ts.maxHeight()) {
                continue;
            }
            loose.add(e);
            looseT.add(t);
            int cells = t.cells().length;
            if (cells >= ts.minCells() && cells <= ts.maxCells()) {
                fits.add(e);
                fitT.add(t);
            }
        }
        if (fits.isEmpty()) {
            fits = loose;
            fitT = looseT;
        }
        if (fits.isEmpty()) {
            return null;
        }
        int total = 0;
        for (RuinCatalog.Entry e : fits) {
            total += e.weight();
        }
        int r = rng.nextInt(total), pick = 0;
        for (int k = 0; k < fits.size(); k++) {
            r -= fits.get(k).weight();
            if (r < 0) {
                pick = k;
                break;
            }
        }
        RuinCatalog.Entry e = fits.get(pick);
        ShapeTemplate t = fitT.get(pick);
        //Rotation seulement (jamais de miroir) : la restauration reconstruit le plan layout(0, ancre, forward) depuis le controleur
        Direction forward = Direction.from2DDataValue(rng.nextInt(4));
        Direction right = forward.getClockWise();
        //Boite du gabarit relative au controleur : case (x, z) du gabarit -> right * x + forward * z (comme IndustrialBlueprint.layout)
        int[] xs = {-t.cx, t.sx - 1 - t.cx}, zs = {-t.cz, t.sz - 1 - t.cz};
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : xs) {
            for (int z : zs) {
                int wx = right.getStepX() * x + forward.getStepX() * z, wz = right.getStepZ() * x + forward.getStepZ() * z;
                minX = Math.min(minX, wx);
                maxX = Math.max(maxX, wx);
                minZ = Math.min(minZ, wz);
                maxZ = Math.max(maxZ, wz);
            }
        }
        int midX = Math.floorDiv(minX + maxX, 2), midZ = Math.floorDiv(minZ + maxZ, 2);
        return new RuinPlan(site, e.machine(), IndustryMachine.byKey(e.machine()), t, e.category(), forward, site.centerX() - midX,
              site.centerZ() - midZ, new int[]{minX, maxX, minZ, maxZ});
    }

    /** Une annexe prevue : sorte, variante (indice), centre (x, z), direction vers laquelle regarde sa facade, miroir, graine. */
    public record Annex(String type, int variant, int x, int z, Direction front, boolean mirror, long seed) {
    }

    /** Demi-emprise maximale d'une annexe (la plus grande fait 10 x 6). */
    public static final int ANNEX_HALF = 6;

    private boolean overlaps(double x, double z, int pad) {
        return x + pad >= boxMinX && x - pad <= boxMaxX && z + pad >= boxMinZ && z - pad <= boxMaxZ;
    }

    /** Annexes du site (1 a {@code max}, sortes distinctes du tier, 24 a 80 blocs du controleur, hors de la ruine). */
    public List<Annex> annexes(GenSettings settings, GenData.Snapshot data, int max) {
        List<Annex> out = new ArrayList<>();
        if (max <= 0) {
            return out;
        }
        Rng rng = new Rng(Rng.hash(site.seed(), SALT_ANNEX));
        List<String> types = data.typesFor(site.tier());
        if (types.isEmpty()) {
            return out;
        }
        rng.shuffle(types);
        int n = Math.min(types.size(), 1 + rng.nextInt(max));
        double base = rng.nextDouble() * Math.PI * 2;
        for (int k = 0; k < n; k++) {
            String type = types.get(k);
            int variants = data.annexes().get(type).size();
            double angle = base + k * Math.PI * 2 / n + rng.range(-0.45, 0.45);
            double dist = rng.range(settings.annexMin(), settings.annexMax());
            double x = ctrlX + Math.cos(angle) * dist, z = ctrlZ + Math.sin(angle) * dist;
            //Jamais sur la ruine : repousse le long du rayon issu du centre du site jusqu'a degager l'emprise et sa marge
            double ux = x - site.centerX(), uz = z - site.centerZ(), r = Math.hypot(ux, uz);
            if (r < 1e-3) {
                ux = Math.cos(angle);
                uz = Math.sin(angle);
                r = 1;
            }
            ux /= r;
            uz /= r;
            int pad = settings.margin() + ANNEX_HALF + 1;
            for (int step = 0; step < 96 && (r < settings.annexClearance() || overlaps(x, z, pad)); step++) {
                r += 1;
                x = site.centerX() + ux * r;
                z = site.centerZ() + uz * r;
            }
            int ax = (int) Math.floor(x), az = (int) Math.floor(z);
            int fx = ctrlX - ax, fz = ctrlZ - az;
            Direction front = Math.abs(fx) >= Math.abs(fz) ? (fx >= 0 ? Direction.EAST : Direction.WEST)
                                                            : (fz >= 0 ? Direction.SOUTH : Direction.NORTH);
            out.add(new Annex(type, rng.nextInt(variants), ax, az, front, rng.nextInt(2) == 1, rng.nextLong()));
        }
        return out;
    }
}
