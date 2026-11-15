package fr.pioneers.client.kinetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.kinetics.kitchen.KitchenMachineTile;
import fr.pioneers.kinetics.kitchen.KitchenMachineType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 : parties mobiles des postes mecaniques de la cuisine (repere local : facade +Z, arbre des machines horizontales selon X a y 8, z 8 ;
 * machines verticales : arbre par dessous au centre). Les chassis fixes sont des modeles JSON ({@code tools/gc/mechanics/kitchen_machines.py},
 * memes cotes que les constantes ci-dessous). Dessin pur : l'etat vient d'un {@link Look} (le banc d'essai hors jeu en fabrique un).
 * <ul>
 * <li>Presse : deux vis sans fin sur l'arbre menent deux roues dentees horizontales qui font tourner les deux vis de presse ; la traverse
 * (mouton) descend le long des vis avec l'avancement et presse le plateau dans la claie ; le jus remplit la maie.</li>
 * <li>Baratte : batteur a quatre palettes sur l'arbre, dans le tonneau couche ouvert par sa trappe ; creme puis grains de beurre.</li>
 * <li>Hachoir : deux cames levent le levier du couteau qui retombe sur le billot (deux coups par tour).</li>
 * <li>Petrin : la cuve tourne sur l'arbre vertical, la spirale descend de la potence et tourne en sens contraire, deux fois plus vite.</li>
 * <li>Extracteur : la cage a quatre cadres tourne dans la cuve, le miel s'amasse au fond.</li>
 * </ul>
 */
public final class KitchenVisuals {

    private KitchenVisuals() {
    }

    /**
     * Etat affiche : avancement (0..1), au travail, remplissage des entrees (0..1), niveaux des reservoirs (0..1), textures des liquides
     * (null : texture par defaut) et leurs teintes, entree chargee (cadres pleins...).
     */
    public record Look(float progress, boolean working, float fill, float liquidIn, float liquidOut, @Nullable KDraw.Tex fluidIn, int tintIn,
                       @Nullable KDraw.Tex fluidOut, int tintOut, boolean loaded) {
    }

    /** Icone et objet en main : un peu de contenu, rien ne travaille. */
    public static final Look ICON = new Look(0.3F, false, 0.6F, 0.55F, 0.45F, null, -1, null, -1, true);

    // Presse : vis de presse (x), axe des vis (z), hauteurs de la traverse
    static final float SCREW_L = 3.5F, SCREW_R = 12.5F, SCREW_Z = 11.4F, HEAD_TOP = 13.4F, HEAD_DROP = 4.2F;

    /** Partie mobile d'un poste ; {@code angle} = angle de l'arbre (degres). */
    public static void machine(KDraw d, KitchenMachineType type, float angle, Look look) {
        switch (type) {
            case PRESS -> press(d, angle, look);
            case CHURN -> churn(d, angle, look);
            case CHOPPER -> chopper(d, angle, look);
            case KNEADER -> kneader(d, angle, look);
            case HONEY_EXTRACTOR -> extractor(d, angle, look);
        }
    }

    // ------------------------------------------------------------------
    // Etat d'un poste pose (client)
    // ------------------------------------------------------------------

    public static Look look(KitchenMachineTile tile, float partial) {
        KitchenMachineType type = tile.type();
        FluidStack in = tile.fluidIn(), out = tile.fluidOut();
        float liquidIn = in.isEmpty() ? 0 : Math.min(1, in.getAmount() / (float) KitchenMachineType.TANK);
        float liquidOut = out.isEmpty() ? 0 : Math.min(1, out.getAmount() / (float) KitchenMachineType.TANK);
        float fill = Math.min(1, tile.inputCount() / (type == KitchenMachineType.KNEADER ? 10F : 8F));
        if (type == KitchenMachineType.CHURN) {
            fill = Math.max(fill, liquidIn);
        }
        return new Look(tile.progress(partial), tile.working(), fill, liquidIn, liquidOut, fluidTex(in), fluidTint(in), fluidTex(out), fluidTint(out),
              !tile.workpiece().isEmpty());
    }

    @Nullable
    static KDraw.Tex fluidTex(FluidStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(stack.getFluid());
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(stack)));
    }

    static int fluidTint(FluidStack stack) {
        return stack.isEmpty() ? -1 : IClientFluidTypeExtensions.of(stack.getFluid()).getTintColor(stack) | 0xFF000000;
    }

    /** Boite d'un liquide : texture du liquide teintee, sinon la texture par defaut. */
    private static void liquid(KDraw d, float x0, float y0, float z0, float x1, float y1, float z1, @Nullable KDraw.Tex tex, int tint, String fallback) {
        if (tex == null) {
            d.box(x0, y0, z0, x1, y1, z1, fallback, fallback);
            return;
        }
        d.tint(tint);
        d.box(x0, y0, z0, x1, y1, z1, tex, tex);
        d.tint(0xFFFFFFFF);
    }

    // ------------------------------------------------------------------
    // Aides de dessin
    // ------------------------------------------------------------------

    private static final float TAN8 = (float) Math.tan(Math.PI / 8);

    /** Anneau octogonal d'axe Y (centre cx, cz ; rayon exterieur r, epaisseur t). */
    static void ringY(KDraw d, float cx, float cz, float r, float y0, float y1, float t, String tex, String ends) {
        float half = r * TAN8 + 0.05F;
        for (int k = 0; k < 8; k++) {
            d.push().rotate(Direction.Axis.Y, 45 * k, cx, 8, cz);
            d.box(cx - half, y0, cz + r - t, cx + half, y1, cz + r, tex, ends);
            d.pop();
        }
    }

    /**
     * Prisme octogonal plein d'axe Y, de rayon interieur r : deux rectangles en croix (r x r.tan 22,5) et les memes tournes de 45 degres,
     * dont les coins tombent sur les sommets (KDraw.octa, un carre et son double tourne, fait une etoile : bien pour un arbre, pas pour un
     * fond de cuve ou un liquide).
     */
    static void octagon(KDraw d, float cx, float cz, float r, float y0, float y1, KDraw.Tex side, KDraw.Tex ends) {
        float h = r * TAN8;
        for (int k = 0; k < 2; k++) {
            d.push().rotate(Direction.Axis.Y, 45 * k, cx, 8, cz);
            d.box(cx - r, y0, cz - h, cx + r, y1, cz + h, side, ends);
            d.box(cx - h, y0, cz - r, cx + h, y1, cz + r, side, ends);
            d.pop();
        }
    }

    static void octagon(KDraw d, float cx, float cz, float r, float y0, float y1, String side, String ends) {
        octagon(d, cx, cz, r, y0, y1, KDraw.sprite(side), KDraw.sprite(ends));
    }

    /** Pignon horizontal (axe Y) a dents : moyeu octogonal et {@code teeth} dents. */
    static void gearY(KDraw d, float cx, float cz, float r, float y0, float y1, int teeth, String tex, String toothTex) {
        d.octa(cx, cz, r - 0.4F, y0, y1, tex, tex);
        for (int k = 0; k < teeth; k++) {
            d.push().rotate(Direction.Axis.Y, 360F / teeth * k, cx, 8, cz);
            d.box(cx - 0.3F, y0 + 0.1F, cz + r - 0.6F, cx + 0.3F, y1 - 0.1F, cz + r + 0.3F, toothTex, toothTex);
            d.pop();
        }
    }

    /** Filet d'une vis verticale (helice de petites dents) autour de (cx, cz), entre y0 et y1. */
    static void threadY(KDraw d, float cx, float cz, float r, float y0, float y1, float spin, String tex) {
        int n = Math.max(2, Math.round((y1 - y0) / 0.32F));
        for (int k = 0; k < n; k++) {
            float y = y0 + k * (y1 - y0) / n;
            d.push().rotate(Direction.Axis.Y, spin + k * 45, cx, 8, cz);
            d.box(cx - 0.25F, y, cz + r * 0.4F, cx + 0.25F, y + 0.28F, cz + r, tex, tex);
            d.pop();
        }
    }

    // ------------------------------------------------------------------
    // Presse a deux vis
    // ------------------------------------------------------------------

    static void press(KDraw d, float angle, Look look) {
        MachineVisuals.crossShaft(d, angle, 0, 16);
        float wheel = angle / 8;                                                        //vis sans fin a un filet, roue a 8 dents
        for (float sx : new float[]{SCREW_L, SCREW_R}) {
            //Vis sans fin sur l'arbre : noyau et filet en helice (avance le long de X avec l'angle)
            d.push().align(Direction.Axis.X).rotate(Direction.Axis.Y, angle, 8, 8, 8);
            d.octa(8, 8, 1.2F, sx - 1.3F, sx + 1.3F, "iron", "iron_bright");
            d.pop();
            d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
            for (int k = 0; k < 10; k++) {
                float x = sx - 1.2F + k * 0.26F;
                d.push().rotate(Direction.Axis.X, k * 72, 8, 8, 8);
                d.box(x - 0.13F, 9.1F, 7.3F, x + 0.13F, 9.75F, 8.7F, "iron_bright", "iron_bright");
                d.pop();
            }
            d.pop();
            //Roue dentee horizontale de la vis de presse (devant la vis sans fin) et vis de presse qui tourne avec elle
            float spin = sx < 8 ? wheel : -wheel;
            d.push().rotate(Direction.Axis.Y, spin, sx, 8, SCREW_Z);
            gearY(d, sx, SCREW_Z, 1.85F, 7.3F, 8.7F, 8, "brass", "brass");
            d.octa(sx, SCREW_Z, 0.7F, 8.7F, 15.0F, "iron", "iron_bright");
            d.pop();
            threadY(d, sx, SCREW_Z, 1.05F, 8.9F, 14.8F, spin * 2, "iron_bright");
        }
        //Traverse (mouton) qui descend le long des vis, ecrous, tige et plateau dans la claie
        float head = HEAD_TOP - HEAD_DROP * clamp(look.progress());
        d.box(2.4F, head, 10.2F, 13.6F, head + 1.5F, 14.4F, "oak", "oak_end");
        d.box(2.2F, head + 0.4F, 10.0F, 13.8F, head + 1.1F, 14.6F, "iron", "iron");             //frette
        for (float sx : new float[]{SCREW_L, SCREW_R}) {
            d.box(sx - 1.1F, head - 0.3F, SCREW_Z - 1.1F, sx + 1.1F, head + 1.8F, SCREW_Z + 1.1F, "iron_bright", "iron");   //ecrous
        }
        float plate = head - 3.4F;
        d.box(7.3F, plate + 0.8F, 12.2F, 8.7F, head, 13.6F, "iron", "iron");                     //tige
        d.box(6.0F, plate, 11.0F, 10.0F, plate + 0.8F, 14.8F, "oak_planks", "oak_planks");       //plateau
        //Jus dans la maie (sous la claie) et filets pendant le travail
        if (look.liquidOut() > 0.001F) {
            float h = 1.5F + 0.9F * Math.min(1, look.liquidOut() * 3);
            liquid(d, 5.2F, 1.4F, 10.4F, 10.8F, h, 15.4F, look.fluidOut(), look.tintOut(), "water");
        }
        if (look.working()) {
            for (int k = 0; k < 3; k++) {
                float x = 6.2F + k * 1.8F, drop = (float) ((angle * 0.6 + k * 40) % 60 / 60.0) * 1.6F;
                if (look.fluidOut() != null) {
                    liquid(d, x, 2.6F - drop, 14.95F, x + 0.25F, 3.2F - drop, 15.2F, look.fluidOut(), look.tintOut(), "water");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Baratte a palettes
    // ------------------------------------------------------------------

    static void churn(KDraw d, float angle, Look look) {
        MachineVisuals.crossShaft(d, angle, 0, 16);
        //Creme (ou lait) au fond du tonneau : niveau selon le contenu ; grains de beurre a la fin
        if (look.fill() > 0.01F) {
            //Bain en marches d'escalier qui suivent les douelles basses (octogone de rayon interieur 5,2)
            float level = 3.0F + 5.2F * clamp(look.fill());
            float w = 0;
            for (float y0 = 2.85F; y0 < level - 0.01F; y0 += 1.0F) {
                float y1 = Math.min(level, y0 + 1.0F);
                w = Math.min(5.15F, 5.15F * 1.4142F - (8 - y0));
                if (look.liquidIn() > 0.01F && look.fluidIn() != null) {
                    liquid(d, 3.0F, y0, 8 - w, 13.0F, y1, 8 + w, look.fluidIn(), look.tintIn(), "cream");
                } else {
                    d.box(3.0F, y0, 8 - w, 13.0F, y1, 8 + w, "cream", "cream");
                }
            }
            if (look.progress() > 0.55F) {
                for (int k = 0; k < 5; k++) {
                    float x = 4.0F + k * 1.9F, z = 8 + (float) Math.sin(k * 2.1 + angle * 0.02) * w * 0.6F;
                    d.box(x, level - 0.2F, z - 0.6F, x + 1.1F, level + 0.5F, z + 0.6F, "butter", "butter");
                }
            }
        }
        //Batteur : moyeu et quatre palettes percees (planches) qui tournent avec l'arbre
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        d.box(3.2F, 6.9F, 6.9F, 12.8F, 9.1F, 9.1F, "oak", "oak_end");
        for (int k = 0; k < 4; k++) {
            d.push().rotate(Direction.Axis.X, 90 * k, 8, 8, 8);
            d.box(3.6F, 7.65F, 9.1F, 6.6F, 8.35F, 12.6F, "oak_planks", "oak_planks");
            d.box(9.4F, 7.65F, 9.1F, 12.4F, 8.35F, 12.6F, "oak_planks", "oak_planks");
            d.box(3.6F, 7.6F, 12.6F, 12.4F, 8.4F, 13.2F, "oak_dark", "oak_dark");           //latte du bout
            d.pop();
        }
        d.pop();
    }

    // ------------------------------------------------------------------
    // Hachoir a levier
    // ------------------------------------------------------------------

    static void chopper(KDraw d, float angle, Look look) {
        MachineVisuals.crossShaft(d, angle, 0, 16);
        d.push().rotate(Direction.Axis.X, angle, 8, 8, 8);
        for (int k = 0; k < 2; k++) {                                                    //deux cames : deux coups par tour
            d.push().rotate(Direction.Axis.X, 180 * k, 8, 8, 8);
            d.box(6.5F, 7.0F, 8.0F, 9.5F, 9.0F, 12.0F, "iron", "iron");
            d.pop();
        }
        d.pop();
        //Levier (pivot a l'arriere) leve par la came puis qui retombe : le couteau frappe le billot
        double phase = (angle % 180 + 180) % 180 / 180.0;
        double lift = phase < 0.7 ? phase / 0.7 : 1 - (phase - 0.7) / 0.3;
        float a = (float) (-17 * Math.max(0, lift));
        d.push().rotate(Direction.Axis.X, a, 8, 13.4F, 1.5F);
        d.box(7.0F, 12.6F, 0.5F, 9.0F, 14.2F, 15.6F, "oak", "oak_end");                    //levier
        d.box(7.2F, 13.0F, 15.6F, 8.8F, 13.8F, 17.0F, "oak_dark", "oak_end");              //poignee
        d.box(3.0F, 12.4F, 12.3F, 13.0F, 13.4F, 13.5F, "iron", "iron");                    //dos du couteau (fer)
        d.box(3.2F, 10.0F, 12.6F, 12.8F, 12.4F, 13.2F, "steel", "steel");                  //lame
        d.box(3.2F, 9.3F, 12.75F, 12.8F, 10.0F, 13.05F, "iron_bright", "iron_bright");     //fil (biseau)
        d.box(6.6F, 13.4F, 12.1F, 9.4F, 14.6F, 13.7F, "iron", "iron");                     //serre-lame
        d.pop();
    }

    // ------------------------------------------------------------------
    // Petrin a spirale (arbre vertical)
    // ------------------------------------------------------------------

    static void kneader(KDraw d, float angle, Look look) {
        float bowl = angle;
        //Arbre qui monte du socle, puis cuve qui tourne avec lui
        d.push().rotate(Direction.Axis.Y, bowl, 8, 8, 8);
        d.octa(8, 8, KineticVisuals.AXLE, 0, 4.6F, "iron_bright", "iron");
        d.octa(8, 8, 3.2F, 4.0F, 4.6F, "iron", "iron");                                   //plateau
        octagon(d, 8, 8, 5.5F, 4.6F, 5.1F, "iron_plate", "iron_plate");                   //fond de la cuve
        ringY(d, 8, 8, 5.9F, 5.1F, 11.0F, 0.55F, "steel", "iron_bright");                 //paroi
        ringY(d, 8, 8, 6.15F, 10.5F, 11.2F, 0.5F, "iron_bright", "iron_bright");          //bord roule
        //Pate : masse qui suit la cuve, plus haute avec le contenu, qui se tasse vers la fin
        if (look.fill() > 0.01F) {
            float h = 1.2F + 3.6F * clamp(look.fill()) * (1 - 0.25F * clamp(look.progress()));
            octagon(d, 8, 8, 5.0F, 5.1F, 5.1F + h, "dough", "dough");
            for (int k = 0; k < 3; k++) {
                d.push().rotate(Direction.Axis.Y, 120 * k + 20, 8, 8, 8);
                d.box(6.6F, 5.1F + h - 0.2F, 10.0F, 9.4F, 5.1F + h + 0.8F, 12.2F, "dough", "dough");   //bourrelets
                d.pop();
            }
        }
        d.pop();
        //Spirale : tige et crochet en helice, contre-rotation rapide
        float spin = -angle * 2;
        float hx = 9.6F, hz = 8.9F;
        d.push().rotate(Direction.Axis.Y, spin, hx, 8, hz);
        d.octa(hx, hz, 0.55F, 9.6F, 13.6F, "iron_bright", "iron");
        d.octa(hx, hz, 1.0F, 13.0F, 13.6F, "iron", "iron");
        for (int k = 0; k < 18; k++) {
            float y = 5.6F + k * 0.24F;
            d.push().rotate(Direction.Axis.Y, k * 34, hx, 8, hz);
            d.box(hx - 0.3F, y, hz + 0.6F, hx + 0.3F, y + 0.32F, hz + 1.4F, "steel", "steel");
            d.pop();
        }
        d.box(hx - 0.25F, 5.6F, hz - 0.25F, hx + 0.25F, 9.8F, hz + 0.25F, "steel", "steel");
        d.pop();
        //Pignon de la potence (sous la tete) qui tourne avec la spirale
        d.push().rotate(Direction.Axis.Y, spin, hx, 8, hz);
        gearY(d, hx, hz, 1.6F, 13.6F, 14.2F, 8, "brass", "brass");
        d.pop();
    }

    // ------------------------------------------------------------------
    // Extracteur de miel centrifuge (arbre vertical)
    // ------------------------------------------------------------------

    static void extractor(KDraw d, float angle, Look look) {
        //Miel au fond de la cuve
        if (look.liquidOut() > 0.001F) {
            float h = 2.6F + 2.2F * Math.min(1, look.liquidOut() * 2);
            if (look.fluidOut() != null) {
                d.tint(look.tintOut());
                octagon(d, 8, 8, 6.3F, 2.5F, h, look.fluidOut(), look.fluidOut());
                d.tint(0xFFFFFFFF);
            } else {
                octagon(d, 8, 8, 6.3F, 2.5F, h, "honey", "honey");
            }
        }
        //Axe et cage a quatre cadres
        d.push().rotate(Direction.Axis.Y, angle, 8, 8, 8);
        d.octa(8, 8, KineticVisuals.AXLE, 0, 3.2F, "iron_bright", "iron");
        d.octa(8, 8, 0.6F, 3.2F, 15.2F, "iron_bright", "iron");
        for (float y : new float[]{3.6F, 12.2F}) {                                       //croisillons haut et bas
            d.box(3.6F, y, 7.6F, 12.4F, y + 0.4F, 8.4F, "iron", "iron");
            d.box(7.6F, y, 3.6F, 8.4F, y + 0.4F, 12.4F, "iron", "iron");
        }
        String comb = look.loaded() ? "honeycomb_frame" : "oak_dark";
        for (int k = 0; k < 4; k++) {
            d.push().rotate(Direction.Axis.Y, 90 * k, 8, 8, 8);
            float z0 = 11.2F, z1 = 11.7F;
            d.box(5.2F, 4.0F, z0, 10.8F, 4.6F, z1, "oak", "oak_end");                     //cadre : traverses
            d.box(5.2F, 11.6F, z0, 10.8F, 12.2F, z1, "oak", "oak_end");
            d.box(5.2F, 4.6F, z0, 5.8F, 11.6F, z1, "oak", "oak_end");                      //montants
            d.box(10.2F, 4.6F, z0, 10.8F, 11.6F, z1, "oak", "oak_end");
            d.box(5.8F, 4.6F, z0 + 0.05F, 10.2F, 11.6F, z1 - 0.05F, comb, comb);           //rayon
            for (float x : new float[]{6.4F, 8.0F, 9.6F}) {                               //grillage de la cage
                d.box(x - 0.1F, 4.0F, z1, x + 0.1F, 12.2F, z1 + 0.25F, "iron", "iron");
            }
            d.box(5.0F, 3.8F, z1, 11.0F, 4.0F, z1 + 0.25F, "iron", "iron");
            d.box(5.0F, 12.2F, z1, 11.0F, 12.4F, z1 + 0.25F, "iron", "iron");
            d.pop();
        }
        d.pop();
    }

    private static float clamp(float v) {
        return Math.max(0, Math.min(1, v));
    }

    // ------------------------------------------------------------------
    // Objets poses (client, dans le monde)
    // ------------------------------------------------------------------

    /** Fruits ou caille dans la claie de la presse (ecrases sous le plateau), legume sur le billot du hachoir. */
    public static void items(KitchenMachineTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float partial) {
        KitchenMachineType type = tile.type();
        ItemStack work = tile.workpiece();
        if (work.isEmpty() || type != KitchenMachineType.PRESS && type != KitchenMachineType.CHOPPER) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-MachineItems.faceDegrees(tile.facing())));
        pose.translate(-0.5, 0, -0.5);
        var renderer = Minecraft.getInstance().getItemRenderer();
        if (type == KitchenMachineType.PRESS) {
            //Tas sous le plateau : jusqu'a 4 couches, aplaties a mesure que le plateau descend
            float head = HEAD_TOP - HEAD_DROP * tile.progress(partial);
            float top = head - 3.4F;
            int layers = Math.min(4, Math.max(1, (work.getCount() + 1) / 2));
            float span = Math.max(0.4F, top - 2.0F);
            for (int i = 0; i < layers; i++) {
                float y = 2.0F + span * (i + 0.5F) / layers;
                pose.pushPose();
                pose.translate(8 / 16.0, y / 16.0, 12.9 / 16.0);
                pose.mulPose(Axis.YP.rotationDegrees(i * 47));
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.scale(0.34F, 0.34F, 0.34F * Math.min(1, span / layers / 2.0F));
                renderer.renderStatic(work, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), i);
                pose.popPose();
            }
        } else {
            pose.translate(8 / 16.0, 9.45 / 16.0, 13.6 / 16.0);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.36F, 0.36F, 0.36F);
            renderer.renderStatic(work, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), 0);
        }
        pose.popPose();
    }
}
