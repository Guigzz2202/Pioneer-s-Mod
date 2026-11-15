package fr.pioneers.equipment.cosmetic.client;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERE par tools/gc/cosmetics/gen.py (ne pas modifier a la main) : modeles des cosmetiques portes, un par tenue et par piece
 * (0 coiffe, 1 haut, 2 bas, 3 chaussures), atlas {@code textures/models/cosmetic/<tenue>.png} (2 texels par texel declare).
 */
public final class GeneratedCosmeticModels {

    private GeneratedCosmeticModels() {
    }

    /** Pieces a verre (rendu translucide) : « tenue/piece ». */
    public static final java.util.Set<String> TRANSLUCENT = java.util.Set.of("steam_engineer/0", "chemist/0", "alchemist/1", "aviator/0", "retro_astronaut/0");

    public static LayerDefinition create(String outfit, int piece) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition[] p = new PartDefinition[6];
        p[0] = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        p[1] = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0F, 0F, 0F));
        p[2] = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        p[3] = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        p[4] = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        p[5] = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        int size = switch (outfit) {
            case "peasant" -> o_peasant(p, piece);
            case "blacksmith" -> o_blacksmith(p, piece);
            case "miner" -> o_miner(p, piece);
            case "railwayman" -> o_railwayman(p, piece);
            case "sailor" -> o_sailor(p, piece);
            case "captain" -> o_captain(p, piece);
            case "steam_engineer" -> o_steam_engineer(p, piece);
            case "chemist" -> o_chemist(p, piece);
            case "alchemist" -> o_alchemist(p, piece);
            case "aviator" -> o_aviator(p, piece);
            case "retro_astronaut" -> o_retro_astronaut(p, piece);
            case "nuclear_scientist" -> o_nuclear_scientist(p, piece);
            case "explorer" -> o_explorer(p, piece);
            case "merchant" -> o_merchant(p, piece);
            case "village_guard" -> o_village_guard(p, piece);
            case "evening" -> o_evening(p, piece);
            case "winter" -> o_winter(p, piece);
            case "desert" -> o_desert(p, piece);
            case "fisherman" -> o_fisherman(p, piece);
            case "botanist" -> o_botanist(p, piece);
            default -> 128;
        };
        return LayerDefinition.create(mesh, size, size);
    }

    private static int o_peasant(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(16, 16).addBox(-7F, -9.05F, -6F, 14F, 0.55F, 12F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(56, 0).addBox(-6F, -9.03F, -7F, 12F, 0.55F, 14F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(96, 16).addBox(-3.9F, -12.6F, -2.9F, 7.8F, 3.6F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(68, 16).addBox(-2.9F, -12.65F, -3.9F, 5.8F, 3.7F, 7.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 40).addBox(-2.9F, -13.2F, -2.9F, 5.8F, 0.7F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(76, 30).addBox(-4.02F, -9.9F, -3.32F, 8.04F, 0.8F, 6.64F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 30).addBox(-3.32F, -9.87F, -4.02F, 6.64F, 0.8F, 8.04F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 40).addBox(-3F, 8.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(64, 40).addBox(-1F, 8.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(16, 47).addBox(-1.2F, -0.3F, -2.7F, 2.4F, 3.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(80, 40).addBox(-4F, -0.4F, -2.5F, 8F, 1F, 5F, new CubeDeformation(0.12F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(108, 0).addBox(-2F, 0F, -2F, 4F, 9.4F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 9.4F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(106, 40).addBox(-2F, 8.4F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 47).addBox(-2F, 8.4F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(24, 40).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(24, 47).addBox(-4F, 10.4F, -2F, 8F, 0.9F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(32, 30).addBox(-2.3F, 9.4F, -3.4F, 4.6F, 3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(54, 30).addBox(-2.3F, 9.4F, -3.4F, 4.6F, 3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(48, 47).addBox(-1.8F, 9F, -2.4F, 3.6F, 0.6F, 3.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(64, 47).addBox(-1.8F, 9F, -2.4F, 3.6F, 0.6F, 3.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_blacksmith(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-4.7F, -9F, -4.7F, 9.4F, 3.2F, 9.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(50, 16).addBox(-4.75F, -5.85F, 0.6F, 9.5F, 2.85F, 4.15F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(28, 41).addBox(-1F, -6F, 4.6F, 2F, 1.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(96, 35).addBox(-1.6F, -4.6F, 4.8F, 1.4F, 3.2F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0.2094F, 0F, 0.1745F));
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(102, 35).addBox(0.3F, -4.6F, 4.8F, 1.4F, 3F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0.2094F, 0F, -0.2443F));
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(96, 0).addBox(-3F, -2F, -2F, 4F, 8F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(112, 0).addBox(-1F, -2F, -2F, 4F, 8F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(102, 28).addBox(-3F, 4.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 35).addBox(-1F, 4.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 16).addBox(-3.6F, 1.6F, -2.85F, 7.2F, 10.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(16, 35).addBox(-3.6F, 12F, -2.85F, 7.2F, 4.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(108, 35).addBox(-3.4F, -0.4F, -2.75F, 1F, 2.2F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(112, 35).addBox(2.4F, -0.4F, -2.75F, 1F, 2.2F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(0, 28).addBox(-4.2F, 6.6F, -2.6F, 8.4F, 0.8F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(80, 16).addBox(-3F, 6F, -2F, 4F, 4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(96, 16).addBox(-1F, 6F, -2F, 4F, 4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(40, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(30, 28).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(34, 35).addBox(-4F, 10.6F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(34, 41).addBox(-0.9F, 10.5F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(58, 35).addBox(3.4F, 10F, -1.4F, 1.2F, 4.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(112, 16).addBox(3.6F, 10.6F, -2.2F, 0.8F, 6.2F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(116, 35).addBox(3F, 15.8F, -2.6F, 2F, 1.4F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(18, 16).addBox(-2F, 7F, -2F, 4F, 5F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(34, 16).addBox(-2F, 7F, -2F, 4F, 5F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(54, 28).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(78, 28).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 41).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(14, 41).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(64, 35).addBox(-2F, 6.9F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(80, 35).addBox(-2F, 6.9F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(40, 41).addBox(-2.5F, 10F, -3.25F, 5F, 1.4F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(52, 41).addBox(-2.5F, 10F, -3.25F, 5F, 1.4F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_miner(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(44, 16).addBox(-4.7F, -11.4F, -3.5F, 9.4F, 4F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 0).addBox(-3.5F, -11.45F, -4.7F, 7F, 4.1F, 9.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 40).addBox(-3.5F, -12.12F, -3.5F, 7F, 0.82F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(78, 16).addBox(-5.6F, -8.4F, -4.6F, 11.2F, 0.5F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -8.38F, -5.6F, 9.2F, 0.5F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(100, 48).addBox(-1.6F, -11F, -6.2F, 3.2F, 3F, 1.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(76, 55).addBox(-1.1F, -10.5F, -6.5F, 2.2F, 2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(38, 29).addBox(-4.82F, -8.3F, -4.12F, 9.64F, 0.8F, 8.24F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 29).addBox(-4.12F, -8.27F, -4.82F, 8.24F, 0.8F, 9.64F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(28, 40).addBox(-4F, -0.4F, -2.5F, 8F, 1.1F, 5F, new CubeDeformation(0.12F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(90, 55).addBox(-0.35F, 1.15F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(94, 55).addBox(-0.35F, 3.65F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(98, 55).addBox(-0.35F, 6.15F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(102, 55).addBox(-0.35F, 8.65F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 55).addBox(-3.6F, 1.6F, -2.75F, 2.6F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(8, 55).addBox(1F, 1.6F, -2.75F, 2.6F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(44, 48).addBox(-3F, 8.8F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(60, 48).addBox(-1F, 8.8F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(54, 40).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(76, 48).addBox(-4F, 10.6F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(84, 55).addBox(-0.9F, 10.5F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(16, 55).addBox(-2.2F, 3.8F, -2.9F, 4.4F, 3F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(28, 55).addBox(-2.2F, 3.8F, -2.9F, 4.4F, 3F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(112, 48).addBox(-4.4F, 10.2F, -2.4F, 2.2F, 2.4F, 1.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(40, 55).addBox(2.4F, 10.2F, -2.4F, 2F, 2F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(76, 29).addBox(-2F, 7.6F, -2F, 4F, 4.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(92, 29).addBox(-2F, 7.6F, -2F, 4F, 4.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(78, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(102, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 55).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(62, 55).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 48).addBox(-2.5F, 12.15F, -2.8F, 5F, 0.4F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(22, 48).addBox(-2.5F, 12.15F, -2.8F, 5F, 0.4F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_railwayman(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(88, 0).addBox(-4.7F, -9F, -4.7F, 9.4F, 2.8F, 9.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(12, 27).addBox(-4.4F, -10.2F, -3F, 8.8F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 16).addBox(-3F, -10.25F, -4.4F, 6F, 1.3F, 8.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(44, 27).addBox(-3F, -11.04F, -3F, 6F, 0.94F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 44).addBox(-3.5F, -6.5F, -7.35F, 7F, 0.45F, 2.6F, new CubeDeformation(0F)), PartPose.rotation(-0.1396F, 0F, 0F));
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(86, 16).addBox(-4.82F, -6.9F, -4.12F, 9.64F, 0.8F, 8.24F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(32, 16).addBox(-4.12F, -6.87F, -4.82F, 8.24F, 0.8F, 9.64F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 37).addBox(-3F, 8.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(64, 37).addBox(-1F, 8.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(70, 16).addBox(-3.4F, 2.4F, -2.85F, 6.8F, 9.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(20, 44).addBox(-3.4F, -0.4F, -2.75F, 1.2F, 3F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(26, 44).addBox(2.2F, -0.4F, -2.75F, 1.2F, 3F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 27).addBox(-3.4F, -0.4F, 2.4F, 1.2F, 9F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(6, 27).addBox(2.2F, -0.4F, 2.4F, 1.2F, 9F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(100, 44).addBox(-3.2F, 2.3F, -3.25F, 0.8F, 0.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(104, 44).addBox(2.4F, 2.3F, -3.25F, 0.8F, 0.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(82, 44).addBox(-2.2F, -0.6F, -2.7F, 4.4F, 1.4F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(94, 44).addBox(-0.9F, 0.6F, -2.9F, 1.8F, 1.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(32, 44).addBox(-1.6F, 4.2F, -3.1F, 3.2F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(104, 27).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(42, 44).addBox(-2.1F, 1F, -2.6F, 2F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 44).addBox(0.1F, 1F, -2.6F, 2F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(80, 37).addBox(-2F, 10.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.48F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(96, 37).addBox(-2F, 10.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.48F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(72, 27).addBox(-2F, 8.4F, -2F, 4F, 3.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 27).addBox(-2F, 8.4F, -2F, 4F, 3.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 37).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 37).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(54, 44).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(68, 44).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_sailor(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 16).addBox(-4.3F, -11F, -3.4F, 8.6F, 2.4F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(56, 0).addBox(-3.4F, -11.05F, -4.3F, 6.8F, 2.5F, 8.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(20, 26).addBox(-3.4F, -11.54F, -3.4F, 6.8F, 0.64F, 6.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(66, 16).addBox(-4.42F, -9.4F, -3.72F, 8.84F, 0.8F, 7.44F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(32, 16).addBox(-3.72F, -9.37F, -4.42F, 7.44F, 0.8F, 8.84F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(120, 26).addBox(-1.6F, -9.2F, 4.4F, 1F, 4.4F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, 0.1047F));
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(84, 35).addBox(0.6F, -9.2F, 4.4F, 1F, 4F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0.1396F, 0F, -0.1396F));
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 35).addBox(-3F, 8.8F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 35).addBox(-1F, 8.8F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(32, 35).addBox(-4.4F, -0.5F, 2.3F, 8.8F, 4.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(88, 35).addBox(-3F, -0.5F, -2.75F, 1.2F, 4F, 0.35F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.4189F));
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(94, 35).addBox(1.8F, -0.5F, -2.75F, 1.2F, 4F, 0.35F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.4189F));
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(28, 41).addBox(-1F, 2.6F, -3F, 2F, 1.4F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(100, 35).addBox(-0.7F, 3.8F, -2.95F, 1.4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(88, 0).addBox(-2F, 0F, -2F, 4F, 8F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(104, 0).addBox(-2F, 0F, -2F, 4F, 8F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(100, 16).addBox(-2.2F, 8F, -2.2F, 4.4F, 4F, 4.4F, new CubeDeformation(0.42F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 26).addBox(-2.2F, 8F, -2.2F, 4.4F, 4F, 4.4F, new CubeDeformation(0.42F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 26).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(106, 35).addBox(-3F, 9.4F, -2.75F, 6F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(34, 41).addBox(-2.7F, 9.7F, -3.25F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(38, 41).addBox(-2.7F, 11.1F, -3.25F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(42, 41).addBox(2.1F, 9.7F, -3.25F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(46, 41).addBox(2.1F, 11.1F, -3.25F, 0.6F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(52, 35).addBox(-2F, 10.2F, -2F, 4F, 1.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(68, 35).addBox(-2F, 10.2F, -2F, 4F, 1.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(72, 26).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(96, 26).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 41).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(14, 41).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_captain(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(48, 16).addBox(-4.8F, -10.8F, -3.7F, 9.6F, 2.6F, 7.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 0).addBox(-3.7F, -10.85F, -4.8F, 7.4F, 2.7F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(70, 29).addBox(-3.7F, -11.46F, -3.7F, 7.4F, 0.76F, 7.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 16).addBox(-4.7F, -9F, -4.7F, 9.4F, 2.4F, 9.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 53).addBox(-3.6F, -6.9F, -7.55F, 7.2F, 0.45F, 2.8F, new CubeDeformation(0F)), PartPose.rotation(-0.2094F, 0F, 0F));
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(32, 29).addBox(-4.87F, -7.6F, -4.17F, 9.74F, 1F, 8.34F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(84, 16).addBox(-4.17F, -7.57F, -4.87F, 8.34F, 1F, 9.74F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(50, 53).addBox(-1F, -9F, -5.2F, 2F, 1.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 47).addBox(-3F, 8.4F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 47).addBox(-1F, 8.4F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(32, 47).addBox(-2.6F, 0F, -2.75F, 1.4F, 5F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2443F));
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(38, 47).addBox(1.2F, 0F, -2.75F, 1.4F, 5F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2443F));
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(62, 53).addBox(-2.15F, 1.65F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(66, 53).addBox(-2.15F, 4.05F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(70, 53).addBox(-2.15F, 6.45F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(74, 53).addBox(-2.15F, 8.85F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(78, 53).addBox(1.45F, 1.65F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(82, 53).addBox(1.45F, 4.05F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(86, 53).addBox(1.45F, 6.45F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(90, 53).addBox(1.45F, 8.85F, -2.92F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(76, 47).addBox(-4.5F, 12F, 2.2F, 9F, 4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(102, 29).addBox(-4.6F, 12F, -2.3F, 0.55F, 4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(114, 29).addBox(4.05F, 12F, -2.3F, 0.55F, 4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(96, 47).addBox(-4.5F, 12F, -2.85F, 3F, 4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c19", CubeListBuilder.create().texOffs(104, 47).addBox(1.5F, 12F, -2.85F, 3F, 4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c20", CubeListBuilder.create().texOffs(0, 40).addBox(-3.6F, -2.6F, -2.6F, 5.2F, 0.9F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c21", CubeListBuilder.create().texOffs(24, 40).addBox(-1.6F, -2.6F, -2.6F, 5.2F, 0.9F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c22", CubeListBuilder.create().texOffs(56, 53).addBox(-0.9F, -0.6F, -2.75F, 1.8F, 2F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(48, 40).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(40, 16).addBox(1.95F, 0.4F, -0.5F, 0.4F, 11.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(44, 16).addBox(-2.35F, 0.4F, -0.5F, 0.4F, 11.4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 29).addBox(-2F, 5.6F, -2F, 4F, 6.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(16, 29).addBox(-2F, 5.6F, -2F, 4F, 6.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(72, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(96, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(22, 53).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(36, 53).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(44, 47).addBox(-2F, 5.5F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(60, 47).addBox(-2F, 5.5F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_steam_engineer(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 29).addBox(-5.6F, -9.05F, -4.6F, 11.2F, 0.5F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -9.03F, -5.6F, 9.2F, 0.5F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(74, 16).addBox(-3.8F, -13.6F, -3.2F, 7.6F, 4.6F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(44, 16).addBox(-3.2F, -13.65F, -3.8F, 6.4F, 4.7F, 7.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(62, 40).addBox(-3.2F, -13.96F, -3.2F, 6.4F, 0.46F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 40).addBox(-3.97F, -10.4F, -3.27F, 7.94F, 1.3F, 6.54F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(80, 29).addBox(-3.27F, -10.37F, -3.97F, 6.54F, 1.3F, 7.94F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(86, 56).addBox(-3F, -11.2F, -4.6F, 6F, 1F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(24, 56).addBox(-2.9F, -11.8F, -5.2F, 2.4F, 2.2F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(32, 56).addBox(0.5F, -11.8F, -5.2F, 2.4F, 2.2F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(68, 56).addBox(-2.5F, -11.4F, -5.45F, 1.6F, 1.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(74, 56).addBox(0.9F, -11.4F, -5.45F, 1.6F, 1.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(44, 29).addBox(-3.9F, 0.6F, -2.8F, 7.8F, 10F, 0.38F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(62, 29).addBox(-3.9F, 0.6F, 2.4F, 7.8F, 10F, 0.38F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(100, 56).addBox(-0.35F, 1.85F, -3.05F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(104, 56).addBox(-0.35F, 4.05F, -3.05F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(108, 56).addBox(-0.35F, 6.25F, -3.05F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(112, 56).addBox(-0.35F, 8.45F, -3.05F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(116, 56).addBox(0.6F, 5.4F, -3.2F, 2.6F, 0.3F, 0.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(124, 56).addBox(2.6F, 5.2F, -3.3F, 0.9F, 0.9F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(104, 49).addBox(-3F, 2.4F, -2F, 4F, 1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(0, 56).addBox(-1F, 2.4F, -2F, 4F, 1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(90, 40).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 49).addBox(-4F, 10.6F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(80, 56).addBox(-0.9F, 10.5F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(114, 40).addBox(-4.6F, 10F, -1.6F, 1.4F, 3.6F, 3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(16, 56).addBox(-4.8F, 10.4F, -1.4F, 0.3F, 1.4F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(30, 40).addBox(-2F, 7.2F, -2F, 4F, 4.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(46, 40).addBox(-2F, 7.2F, -2F, 4F, 4.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 49).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 49).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(40, 56).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(54, 56).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(72, 49).addBox(-2F, 9F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(88, 49).addBox(-2F, 9F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_chemist(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(88, 0).addBox(-4.75F, -7.4F, -4.75F, 9.5F, 1.4F, 9.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 27).addBox(-3.4F, -8.2F, -5.4F, 3F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(80, 27).addBox(0.4F, -8.2F, -5.4F, 3F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(14, 34).addBox(-3F, -7.8F, -5.65F, 2.2F, 1.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(22, 34).addBox(0.8F, -7.8F, -5.65F, 2.2F, 1.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(40, 34).addBox(-0.4F, -7.4F, -5.2F, 0.8F, 0.8F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 16).addBox(-4.5F, 12F, 2.2F, 9F, 5.4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, 12F, -2.3F, 0.55F, 5.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(12, 16).addBox(4.05F, 12F, -2.3F, 0.55F, 5.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(44, 16).addBox(-4.5F, 12F, -2.85F, 3F, 5.4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(52, 16).addBox(1.5F, 12F, -2.85F, 3F, 5.4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(60, 16).addBox(-2.6F, 0F, -2.75F, 1.4F, 5.5F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2443F));
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(66, 16).addBox(1.2F, 0F, -2.75F, 1.4F, 5.5F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2443F));
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(44, 34).addBox(0.25F, 6.05F, -2.9F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(48, 34).addBox(0.25F, 8.45F, -2.9F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(52, 34).addBox(0.25F, 10.65F, -2.9F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(88, 27).addBox(1.2F, 1.4F, -2.75F, 2.4F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(30, 34).addBox(1.6F, 0.6F, -3F, 0.4F, 2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(96, 27).addBox(-3.8F, 8.6F, -2.75F, 2.4F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(104, 27).addBox(-0.9F, -0.5F, -2.7F, 1.8F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(72, 16).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 27).addBox(-4F, 10.6F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(34, 34).addBox(-0.9F, 10.5F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 16).addBox(-2F, 9.8F, -2F, 4F, 2.2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(112, 16).addBox(-2F, 9.8F, -2F, 4F, 2.2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 27).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 27).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(110, 27).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 34).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_alchemist(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(16, 16).addBox(-6.6F, -9.05F, -5.6F, 13.2F, 0.5F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(56, 0).addBox(-5.6F, -9.03F, -6.6F, 11.2F, 0.5F, 13.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(52, 30).addBox(-3.6F, -11.8F, -3F, 7.2F, 2.8F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 30).addBox(-3F, -11.85F, -3.6F, 6F, 2.9F, 7.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(80, 30).addBox(-2.6F, -14F, -2.4F, 5.2F, 2.4F, 5.2F, new CubeDeformation(0F)), PartPose.rotation(-0.1396F, 0F, 0.1047F));
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(60, 41).addBox(-1.6F, -15.8F, -0.6F, 3.2F, 2.2F, 3.2F, new CubeDeformation(0F)), PartPose.rotation(-0.2793F, 0F, 0.1745F));
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(24, 57).addBox(-0.8F, -17F, 1.4F, 1.6F, 1.6F, 1.6F, new CubeDeformation(0F)), PartPose.rotation(-0.4538F, 0F, 0.2443F));
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(30, 41).addBox(-3.77F, -10F, -3.07F, 7.54F, 0.9F, 6.14F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 41).addBox(-3.07F, -9.97F, -3.77F, 6.14F, 0.9F, 7.54F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(38, 57).addBox(-0.8F, -11.6F, -3.85F, 1.6F, 1.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(108, 0).addBox(-3F, -2F, -2F, 4F, 10F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 16).addBox(-1F, -2F, -2F, 4F, 10F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(74, 16).addBox(-3.6F, 5F, -2.6F, 5.2F, 6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(98, 16).addBox(-1.6F, 5F, -2.6F, 5.2F, 6F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(76, 41).addBox(-4.5F, 12F, 2.2F, 9F, 6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(28, 30).addBox(-4.6F, 12F, -2.3F, 0.55F, 6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(40, 30).addBox(4.05F, 12F, -2.3F, 0.55F, 6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(96, 41).addBox(-4.5F, 12F, -2.85F, 9F, 6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(68, 16).addBox(-0.8F, 0F, -2.7F, 1.6F, 12F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(72, 50).addBox(-4F, 9.6F, -2F, 8F, 1.2F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(32, 57).addBox(1.6F, 10.4F, -3F, 1.6F, 2.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(56, 57).addBox(1.9F, 9.8F, -2.8F, 1F, 0.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(40, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 50).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 50).addBox(-2F, 10F, -2F, 4F, 2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(112, 50).addBox(-2F, 10F, -2F, 4F, 2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(24, 50).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 50).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 57).addBox(-1.2F, 10.4F, -4.6F, 2.4F, 1.2F, 2.2F, new CubeDeformation(0F)), PartPose.rotation(0.4189F, 0F, 0F));
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(12, 57).addBox(-1.2F, 10.4F, -4.6F, 2.4F, 1.2F, 2.2F, new CubeDeformation(0F)), PartPose.rotation(0.4189F, 0F, 0F));
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(44, 57).addBox(-0.6F, 9.4F, -5.4F, 1.2F, 1.2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(50, 57).addBox(-0.6F, 9.4F, -5.4F, 1.2F, 1.2F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_aviator(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-4.7F, -9F, -4.7F, 9.4F, 3.2F, 9.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(60, 16).addBox(-4.75F, -5.85F, 0.6F, 9.5F, 4.65F, 4.15F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 16).addBox(-4.85F, -6F, -3F, 0.6F, 5.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(12, 16).addBox(4.25F, -6F, -3F, 0.6F, 5.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(102, 27).addBox(-4.95F, -4.6F, -2.2F, 0.5F, 2.4F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(110, 27).addBox(4.45F, -4.6F, -2.2F, 0.5F, 2.4F, 2.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(46, 42).addBox(-4.8F, -7.6F, -4.85F, 9.6F, 1F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(80, 36).addBox(-3.4F, -8.2F, -5.5F, 2.8F, 2.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(88, 36).addBox(0.6F, -8.2F, -5.5F, 2.8F, 2.2F, 0.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(28, 42).addBox(-3F, -7.8F, -5.75F, 2F, 1.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(34, 42).addBox(1F, -7.8F, -5.75F, 2F, 1.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 27).addBox(-4.4F, -0.7F, -2.7F, 8.8F, 2.4F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(24, 16).addBox(-0.25F, 1.6F, -2.75F, 0.5F, 9.6F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 36).addBox(-3F, 8.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(16, 36).addBox(-1F, 8.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(32, 36).addBox(-4F, 10.6F, -2F, 8F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(96, 36).addBox(-3.8F, 5.6F, -2.75F, 2.6F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(104, 36).addBox(1.2F, 5.6F, -2.75F, 2.6F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(112, 36).addBox(-3.2F, 0.4F, -2.6F, 0.4F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(118, 36).addBox(2.8F, 0.4F, -2.6F, 0.4F, 2F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(28, 16).addBox(-2F, 0F, -2F, 4F, 6.5F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(44, 16).addBox(-2F, 0F, -2F, 4F, 6.5F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(90, 16).addBox(-2F, 6.5F, -2F, 4F, 5.5F, 4F, new CubeDeformation(0.34F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(106, 16).addBox(-2F, 6.5F, -2F, 4F, 5.5F, 4F, new CubeDeformation(0.34F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(30, 27).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(56, 36).addBox(-4F, 10.4F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(40, 42).addBox(-0.9F, 10.3F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 0).addBox(-2F, 3.6F, -2F, 4F, 8.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(112, 0).addBox(-2F, 3.6F, -2F, 4F, 8.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(54, 27).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(78, 27).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 42).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(14, 42).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_retro_astronaut(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-5.4F, -11F, -5.4F, 10.8F, 10.4F, 10.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(20, 38).addBox(-4.4F, -11.8F, -4.4F, 8.8F, 0.9F, 8.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(16, 22).addBox(-5.9F, -1.2F, -5.9F, 11.8F, 1.8F, 11.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(64, 22).addBox(-5.95F, -0.6F, -5.95F, 11.9F, 0.6F, 11.9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 65).addBox(3.2F, -14F, 1.8F, 0.4F, 2.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(26, 65).addBox(3F, -14.6F, 1.6F, 0.8F, 0.8F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(44, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(68, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(110, 50).addBox(-3F, 8.4F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 59).addBox(-1F, 8.4F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(64, 59).addBox(-2.6F, 2.4F, -3.4F, 5.2F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(20, 65).addBox(-1.8F, 2.9F, -3.6F, 1.2F, 1.2F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(30, 65).addBox(0.4F, 2.9F, -3.6F, 1.4F, 1F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(36, 65).addBox(-1.8F, 4.6F, -3.6F, 3.6F, 0.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(0, 38).addBox(-3.4F, 1F, 2.4F, 6.8F, 9F, 2.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(26, 50).addBox(-2.8F, 2F, 4.9F, 2F, 6.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(32, 50).addBox(0.8F, 2F, 4.9F, 2F, 6.6F, 0.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(16, 59).addBox(-3.2F, 0.6F, -2.7F, 0.4F, 2.2F, 3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(24, 59).addBox(2.8F, 0.6F, -2.7F, 0.4F, 2.2F, 3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(100, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.42F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 22).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.42F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(38, 50).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(32, 59).addBox(-2F, 4.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 59).addBox(-2F, 4.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(78, 59).addBox(-4F, 10.4F, -2F, 8F, 1F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 38).addBox(-2F, 7F, -2F, 4F, 5F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 38).addBox(-2F, 7F, -2F, 4F, 5F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(62, 50).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(86, 50).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(88, 38).addBox(-2.7F, 11.3F, -3.3F, 5.4F, 1.2F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 50).addBox(-2.7F, 11.3F, -3.3F, 5.4F, 1.2F, 6.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(102, 59).addBox(-2F, 7F, -2F, 4F, 1F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 65).addBox(-2F, 7F, -2F, 4F, 1F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_nuclear_scientist(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(48, 16).addBox(-4.8F, -11.2F, -3.5F, 9.6F, 3.6F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 0).addBox(-3.5F, -11.25F, -4.8F, 7F, 3.7F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(56, 29).addBox(-3.5F, -11.98F, -3.5F, 7F, 0.88F, 7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(82, 16).addBox(-5.8F, -8.2F, -4.8F, 11.6F, 0.5F, 9.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 16).addBox(-4.8F, -8.18F, -5.8F, 9.6F, 0.5F, 11.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(96, 47).addBox(-1.4F, -10.6F, -5F, 2.8F, 2.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(84, 29).addBox(-0.5F, -12F, -3F, 1F, 0.8F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(96, 40).addBox(-4.5F, 12F, 2.2F, 9F, 4.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(32, 29).addBox(-4.6F, 12F, -2.3F, 0.55F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(44, 29).addBox(4.05F, 12F, -2.3F, 0.55F, 4.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(116, 40).addBox(-4.5F, 12F, -2.85F, 3F, 4.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 47).addBox(1.5F, 12F, -2.85F, 3F, 4.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(8, 47).addBox(-4F, 5.2F, -2F, 8F, 1.2F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(32, 47).addBox(-3F, 7.6F, -2F, 4F, 1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(48, 47).addBox(-1F, 7.6F, -2F, 4F, 1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(44, 16).addBox(-0.25F, 0.4F, -2.75F, 0.5F, 11.4F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(104, 47).addBox(1.6F, 1.2F, -2.9F, 1.8F, 2.6F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(118, 47).addBox(1.9F, 1.6F, -3.1F, 1.2F, 0.8F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(110, 47).addBox(-3.4F, 1.4F, -2.95F, 2.4F, 2.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(98, 29).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(64, 47).addBox(-2F, 7.4F, -2F, 4F, 1F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(80, 47).addBox(-2F, 7.4F, -2F, 4F, 1F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 29).addBox(-2F, 5.4F, -2F, 4F, 6.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(16, 29).addBox(-2F, 5.4F, -2F, 4F, 6.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 40).addBox(-2.55F, 11.4F, -2.95F, 5.1F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(72, 40).addBox(-2.55F, 11.4F, -2.95F, 5.1F, 1F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_explorer(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(16, 29).addBox(-4.4F, -11.6F, -3F, 8.8F, 3.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-3F, -11.65F, -4.4F, 6F, 3.3F, 8.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 41).addBox(-3F, -12.44F, -3F, 6F, 0.94F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(16, 16).addBox(-6.2F, -9.05F, -5.2F, 12.4F, 0.55F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(24, 0).addBox(-5.2F, -9.03F, -6.2F, 10.4F, 0.55F, 12.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(48, 29).addBox(-4.57F, -9.8F, -3.87F, 9.14F, 1.2F, 7.74F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(64, 16).addBox(-3.87F, -9.77F, -4.57F, 7.74F, 1.2F, 9.14F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(48, 55).addBox(-0.5F, -12.2F, -0.5F, 1F, 0.7F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(104, 0).addBox(-3F, -2F, -2F, 4F, 8.4F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 16).addBox(-1F, -2F, -2F, 4F, 8.4F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(110, 41).addBox(-3F, 5.2F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 49).addBox(-1F, 5.2F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(52, 55).addBox(-0.35F, 1.25F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 55).addBox(-0.35F, 3.65F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(60, 55).addBox(-0.35F, 6.05F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(64, 55).addBox(-0.35F, 8.45F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(72, 49).addBox(-3.7F, 1.4F, -2.75F, 2.6F, 3F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(80, 49).addBox(1.1F, 1.4F, -2.75F, 2.6F, 3F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(14, 55).addBox(-3.4F, -2.4F, -1F, 4.8F, 0.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(28, 55).addBox(-1.4F, -2.4F, -1F, 4.8F, 0.6F, 2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(100, 16).addBox(-4.3F, 0F, -2.6F, 0.8F, 11F, 0.4F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.5585F));
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(84, 29).addBox(-2F, 0F, -2F, 4F, 5.6F, 4F, new CubeDeformation(0.42F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(100, 29).addBox(-2F, 0F, -2F, 4F, 5.6F, 4F, new CubeDeformation(0.42F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(28, 41).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(16, 49).addBox(-4F, 10.2F, -2F, 8F, 1.3F, 4F, new CubeDeformation(0.66F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(42, 55).addBox(-0.9F, 10.1F, -2.9F, 1.8F, 1.5F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(52, 41).addBox(3.6F, 10F, -1.8F, 1.6F, 3.6F, 3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(88, 49).addBox(-2.2F, 0.6F, -2.55F, 2.2F, 2.2F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(96, 49).addBox(0F, 0.6F, -2.55F, 2.2F, 2.2F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(104, 16).addBox(-2F, 4.4F, -2F, 4F, 7.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 29).addBox(-2F, 4.4F, -2F, 4F, 7.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(62, 41).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(86, 41).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(104, 49).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 55).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(40, 49).addBox(-2F, 4.3F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(56, 49).addBox(-2F, 4.3F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_merchant(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(40, 16).addBox(-5.2F, -9.05F, -4.2F, 10.4F, 0.5F, 8.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 0).addBox(-4.2F, -9.03F, -5.2F, 8.4F, 0.5F, 10.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 28).addBox(-3.9F, -12F, -2.6F, 7.8F, 3F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 16).addBox(-2.6F, -12.05F, -3.9F, 5.2F, 3.1F, 7.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(84, 28).addBox(-2.6F, -12.78F, -2.6F, 5.2F, 0.88F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(52, 28).addBox(-4.07F, -9.9F, -3.37F, 8.14F, 0.8F, 6.74F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(80, 16).addBox(-3.37F, -9.87F, -4.07F, 6.74F, 0.8F, 8.14F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 37).addBox(3.4F, -12.4F, -1F, 0.4F, 3.2F, 1.2F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2443F));
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(110, 37).addBox(-3F, 8.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 44).addBox(-1F, 8.6F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(108, 28).addBox(-2.6F, 0F, -2.75F, 1.4F, 5.6F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2443F));
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(114, 28).addBox(1.2F, 0F, -2.75F, 1.4F, 5.6F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2443F));
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(28, 16).addBox(-2.4F, 0.4F, -2.7F, 4.8F, 9.2F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(18, 50).addBox(-0.35F, 2.05F, -3F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(22, 50).addBox(-0.35F, 4.25F, -3F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(26, 50).addBox(-0.35F, 6.45F, -3F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(114, 44).addBox(-1.3F, -0.6F, -2.95F, 2.6F, 2F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(50, 44).addBox(-4.5F, 12F, 2.2F, 9F, 3.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(28, 28).addBox(-4.6F, 12F, -2.3F, 0.55F, 3.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(40, 28).addBox(4.05F, 12F, -2.3F, 0.55F, 3.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(70, 44).addBox(-4.5F, 12F, -2.85F, 3F, 3.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(78, 44).addBox(1.5F, 12F, -2.85F, 3F, 3.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c17", CubeListBuilder.create().texOffs(16, 44).addBox(-4.6F, 9.6F, -1.4F, 1.4F, 2.4F, 2.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c18", CubeListBuilder.create().texOffs(122, 44).addBox(-4.8F, 10F, -1F, 0.3F, 0.8F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 37).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(26, 44).addBox(-4F, 10.6F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 50).addBox(-0.9F, 10.5F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 37).addBox(-2F, 9.6F, -2F, 4F, 2.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(40, 37).addBox(-2F, 9.6F, -2F, 4F, 2.4F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(56, 37).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(80, 37).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(86, 44).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(100, 44).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(6, 50).addBox(-0.8F, 10.4F, -3.35F, 1.6F, 1.1F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(12, 50).addBox(-0.8F, 10.4F, -3.35F, 1.6F, 1.1F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_village_guard(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(74, 16).addBox(-6F, -8.95F, -5F, 12F, 0.6F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 16).addBox(-5F, -8.93F, -6F, 10F, 0.6F, 12F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(72, 29).addBox(-4.5F, -11.8F, -3F, 9F, 3F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(44, 16).addBox(-3F, -11.85F, -4.5F, 6F, 3.1F, 9F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 40).addBox(-3F, -12.7F, -3F, 6F, 1F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(102, 29).addBox(-0.5F, -12.6F, -4F, 1F, 1F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 29).addBox(-4.6F, -9.4F, -4.6F, 9.2F, 0.6F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(88, 0).addBox(-3.4F, -0.2F, -2.85F, 6.8F, 12.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(104, 0).addBox(-3.4F, -0.2F, 2.45F, 6.8F, 12.2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 53).addBox(-3.4F, 12F, -2.85F, 6.8F, 4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(16, 53).addBox(-3.4F, 12F, 2.45F, 6.8F, 4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(96, 40).addBox(-1.8F, 1.8F, -3F, 3.6F, 4.2F, 0.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(0, 47).addBox(-4F, 9.6F, -2F, 8F, 1.2F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(60, 53).addBox(-0.9F, 9.5F, -2.9F, 1.8F, 1.4F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(24, 47).addBox(-3F, -2F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(40, 47).addBox(-1F, -2F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(24, 40).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(56, 47).addBox(-2F, 3.6F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(72, 47).addBox(-2F, 3.6F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(40, 29).addBox(-2F, 6.2F, -2F, 4F, 5.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(56, 29).addBox(-2F, 6.2F, -2F, 4F, 5.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(48, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(72, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(32, 53).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(46, 53).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(88, 47).addBox(-2F, 6.1F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(104, 47).addBox(-2F, 6.1F, -2F, 4F, 1.1F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_evening(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(30, 30).addBox(-5F, -9.05F, -4F, 10F, 0.45F, 8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(38, 16).addBox(-4F, -9.03F, -5F, 8F, 0.45F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(74, 16).addBox(-4.4F, -8.9F, -5F, 8.8F, 0.4F, 10F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 16).addBox(-3.6F, -15.6F, -3.1F, 7.2F, 6.6F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(88, 0).addBox(-3.1F, -15.65F, -3.6F, 6.2F, 6.7F, 7.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(0, 40).addBox(-3.1F, -15.9F, -3.1F, 6.2F, 0.4F, 6.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(66, 30).addBox(-3.77F, -10.6F, -3.07F, 7.54F, 1.5F, 6.14F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 30).addBox(-3.07F, -10.57F, -3.77F, 6.14F, 1.5F, 7.54F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(96, 30).addBox(-1.8F, -0.4F, -2.7F, 3.6F, 7.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(28, 40).addBox(-2.6F, 0F, -2.75F, 1.4F, 6.8F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, -0.2443F));
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(34, 40).addBox(1.2F, 0F, -2.75F, 1.4F, 6.8F, 0.3F, new CubeDeformation(0F)), PartPose.rotation(0F, 0F, 0.2443F));
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(28, 55).addBox(-1.6F, -0.5F, -3F, 3.2F, 1.2F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(38, 55).addBox(-0.4F, -0.6F, -3.15F, 0.8F, 1.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(42, 55).addBox(-0.25F, 2.15F, -3.05F, 0.5F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(46, 55).addBox(-0.25F, 4.15F, -3.05F, 0.5F, 0.5F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(40, 40).addBox(-4.5F, 12F, 2.2F, 9F, 6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(88, 48).addBox(-3F, 9.2F, -2F, 4F, 0.8F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(104, 48).addBox(-1F, 9.2F, -2F, 4F, 0.8F, 4F, new CubeDeformation(0.6F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(50, 55).addBox(-3.8F, 2F, -2.8F, 1.4F, 1F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(60, 40).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(30, 16).addBox(1.95F, 0.2F, -0.4F, 0.4F, 11.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(34, 16).addBox(-2.35F, 0.2F, -0.4F, 0.4F, 11.6F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 48).addBox(-2F, 10F, -2F, 4F, 2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(40, 48).addBox(-2F, 10F, -2F, 4F, 2F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(84, 40).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 48).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 55).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(14, 55).addBox(-2.45F, 10.2F, -3.15F, 4.9F, 1.5F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 48).addBox(-2F, 8.6F, -2F, 4F, 1.8F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(72, 48).addBox(-2F, 8.6F, -2F, 4F, 1.8F, 4F, new CubeDeformation(0.75F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_winter(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(36, 17).addBox(-4.9F, -11.4F, -3.7F, 9.8F, 3.6F, 7.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 17).addBox(-3.7F, -11.45F, -4.9F, 7.4F, 3.7F, 9.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(16, 31).addBox(-3.7F, -12.12F, -3.7F, 7.4F, 0.82F, 7.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(72, 17).addBox(-5.3F, -7.8F, -2.6F, 0.8F, 5.6F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(86, 17).addBox(4.5F, -7.8F, -2.6F, 0.8F, 5.6F, 5.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(88, 50).addBox(-4.9F, -8.6F, -5.6F, 9.8F, 2.6F, 1.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 57).addBox(-4.9F, -7.8F, 4.6F, 9.8F, 4F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(62, 57).addBox(-2F, -11.6F, -5.5F, 4F, 2.4F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(48, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(64, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -0.2F, -2F, 8F, 12.2F, 4F, new CubeDeformation(0.72F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 31).addBox(-4.6F, -0.8F, -2.9F, 9.2F, 2.2F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(100, 17).addBox(-0.9F, 1.4F, -3.05F, 1.8F, 10.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(24, 50).addBox(-3F, 8.2F, -2F, 4F, 1.8F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(40, 50).addBox(-1F, 8.2F, -2F, 4F, 1.8F, 4F, new CubeDeformation(0.78F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(22, 57).addBox(-4.5F, 12F, 2.2F, 9F, 3.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(80, 31).addBox(-4.6F, 12F, -2.3F, 0.55F, 3.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(92, 31).addBox(4.05F, 12F, -2.3F, 0.55F, 3.6F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(42, 57).addBox(-4.5F, 12F, -2.85F, 9F, 3.6F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(0, 42).addBox(-4.7F, 15F, -3F, 9.4F, 1.2F, 6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(72, 57).addBox(-2.6F, 3F, -3.25F, 1.2F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(78, 57).addBox(1.4F, 3F, -3.25F, 1.2F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c15", CubeListBuilder.create().texOffs(84, 57).addBox(-2.6F, 6.4F, -3.25F, 1.2F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c16", CubeListBuilder.create().texOffs(90, 57).addBox(1.4F, 6.4F, -3.25F, 1.2F, 0.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(80, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(96, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(32, 42).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(106, 17).addBox(-2F, 5.4F, -2F, 4F, 6.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 31).addBox(-2F, 5.4F, -2F, 4F, 6.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(56, 42).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(80, 42).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(56, 50).addBox(-2F, 5.2F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.85F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(72, 50).addBox(-2F, 5.2F, -2F, 4F, 1.6F, 4F, new CubeDeformation(0.85F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(104, 42).addBox(-2.55F, 11.4F, -2.95F, 5.1F, 1F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 50).addBox(-2.55F, 11.4F, -2.95F, 5.1F, 1F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_desert(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-4.75F, -9.6F, -4.75F, 9.5F, 3.8F, 9.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 16).addBox(-4.85F, -8.6F, -4.85F, 9.7F, 1F, 9.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(64, 16).addBox(-4.85F, -6.9F, -4.85F, 9.7F, 1F, 9.7F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(26, 28).addBox(-3.8F, -10.4F, -3.8F, 7.6F, 1F, 7.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(68, 38).addBox(-1.4F, -6.4F, 4.6F, 2.8F, 4.6F, 0.5F, new CubeDeformation(0F)), PartPose.rotation(0.1047F, 0F, 0F));
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(104, 16).addBox(-3.5F, 6F, -2.5F, 5F, 5F, 5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 28).addBox(-1.5F, 6F, -2.5F, 5F, 5F, 5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(58, 28).addBox(-4.5F, 12F, 2.2F, 9F, 6.4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, 12F, -2.3F, 0.55F, 6.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(12, 16).addBox(4.05F, 12F, -2.3F, 0.55F, 6.4F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(78, 28).addBox(-4.5F, 12F, -2.85F, 3F, 6.4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(86, 28).addBox(1.5F, 12F, -2.85F, 3F, 6.4F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(76, 38).addBox(-4F, 9.4F, -2F, 8F, 1.6F, 4F, new CubeDeformation(0.66F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(20, 28).addBox(-0.6F, 0F, -2.7F, 1.2F, 9F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(94, 28).addBox(-4.6F, -0.6F, -2.8F, 9.2F, 1.4F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(96, 0).addBox(-2F, 0F, -2F, 4F, 9F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(112, 0).addBox(-2F, 0F, -2F, 4F, 9F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(100, 38).addBox(-2F, 9F, -2F, 4F, 2F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 45).addBox(-2F, 9F, -2F, 4F, 2F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 38).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(24, 38).addBox(-2.4F, 11.6F, -3.2F, 4.8F, 0.7F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(46, 38).addBox(-2.4F, 11.6F, -3.2F, 4.8F, 0.7F, 5.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(24, 45).addBox(-2.1F, 10.2F, -2.4F, 4.2F, 0.6F, 0.4F, new CubeDeformation(0.33F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(36, 45).addBox(-2.1F, 10.2F, -2.4F, 4.2F, 0.6F, 0.4F, new CubeDeformation(0.33F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(48, 45).addBox(-2.1F, 10.6F, 0.8F, 4.2F, 0.6F, 0.4F, new CubeDeformation(0.33F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(60, 45).addBox(-2.1F, 10.6F, 0.8F, 4.2F, 0.6F, 0.4F, new CubeDeformation(0.33F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(16, 45).addBox(-0.4F, 9.6F, -2.75F, 0.8F, 2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(20, 45).addBox(-0.4F, 9.6F, -2.75F, 0.8F, 2F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_fisherman(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(38, 29).addBox(-4.6F, -11F, -3.3F, 9.2F, 2.4F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(88, 0).addBox(-3.3F, -11.05F, -4.6F, 6.6F, 2.5F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(16, 40).addBox(-3.3F, -11.78F, -3.3F, 6.6F, 0.88F, 6.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(68, 16).addBox(-5.6F, -9.05F, -4.6F, 11.2F, 0.55F, 9.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(0, 16).addBox(-4.6F, -9.03F, -5.6F, 9.2F, 0.55F, 11.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(92, 40).addBox(-5.4F, -9F, 2.2F, 10.8F, 0.5F, 5.4F, new CubeDeformation(0F)), PartPose.rotation(-0.2443F, 0F, 0F));
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(72, 29).addBox(-4.77F, -9.6F, -4.07F, 9.54F, 0.7F, 8.14F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 29).addBox(-4.07F, -9.57F, -4.77F, 8.14F, 0.7F, 9.54F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(24, 0).addBox(-3F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(40, 0).addBox(-1F, -2F, -2F, 4F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(90, 57).addBox(-0.35F, 1.25F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(94, 57).addBox(-0.35F, 3.65F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(98, 57).addBox(-0.35F, 6.05F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(102, 57).addBox(-0.35F, 8.45F, -2.85F, 0.7F, 0.7F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(34, 57).addBox(-3.8F, 6.4F, -2.75F, 2.6F, 2.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(42, 57).addBox(1.2F, 6.4F, -2.75F, 2.6F, 2.4F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(0, 57).addBox(-3.6F, -0.4F, 2.3F, 7.2F, 3.6F, 1F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(44, 40).addBox(-3F, -1F, -2.6F, 6F, 1.2F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(50, 57).addBox(-4.5F, 12F, 2.2F, 9F, 2.8F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(68, 40).addBox(-4.6F, 12F, -2.3F, 0.55F, 2.8F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c13", CubeListBuilder.create().texOffs(80, 40).addBox(4.05F, 12F, -2.3F, 0.55F, 2.8F, 4.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c14", CubeListBuilder.create().texOffs(70, 57).addBox(-4.5F, 12F, -2.85F, 9F, 2.8F, 0.55F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(72, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.4F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(44, 16).addBox(-4F, 3F, -2F, 8F, 9F, 4F, new CubeDeformation(0.55F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(18, 57).addBox(-3.2F, -0.4F, -2.65F, 1F, 3.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(22, 57).addBox(2.2F, -0.4F, -2.65F, 1F, 3.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(26, 57).addBox(-3.2F, -0.4F, 2.35F, 1F, 3.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(30, 57).addBox(2.2F, -0.4F, 2.35F, 1F, 3.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(110, 29).addBox(-2F, 6F, -2F, 4F, 6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 40).addBox(-2F, 6F, -2F, 4F, 6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 50).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(24, 50).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(96, 50).addBox(-2F, 6F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(112, 50).addBox(-2F, 6F, -2F, 4F, 1.2F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(48, 50).addBox(-2.55F, 11.5F, -2.95F, 5.1F, 0.9F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(72, 50).addBox(-2.55F, 11.5F, -2.95F, 5.1F, 0.9F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }

    private static int o_botanist(PartDefinition[] p, int piece) {
        switch (piece) {
            case 0 -> {
                p[0].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(0, 16).addBox(-7.4F, -9.05F, -6.4F, 14.8F, 0.5F, 12.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(0, 0).addBox(-6.4F, -9.03F, -7.4F, 12.8F, 0.5F, 14.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(56, 16).addBox(-6.4F, -8.85F, -6.4F, 12.8F, 0.4F, 12.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(94, 30).addBox(-3.8F, -11.6F, -2.8F, 7.6F, 2.6F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 30).addBox(-2.8F, -11.65F, -3.8F, 5.6F, 2.7F, 7.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(60, 43).addBox(-2.8F, -12.2F, -2.8F, 5.6F, 0.7F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(30, 43).addBox(-3.97F, -10F, -3.27F, 7.94F, 0.9F, 6.54F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(0, 43).addBox(-3.27F, -9.97F, -3.97F, 6.54F, 0.9F, 7.94F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(28, 65).addBox(2.6F, -11F, -4.3F, 1.8F, 1.8F, 0.8F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(44, 65).addBox(3.2F, -10.4F, -4.5F, 0.6F, 0.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[0].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(48, 65).addBox(1.6F, -10.4F, -4.15F, 1.2F, 0.8F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 1 -> {
                p[1].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(56, 0).addBox(-4F, 0F, -2F, 8F, 12F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(108, 16).addBox(-3F, -2F, -2F, 4F, 9F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 30).addBox(-1F, -2F, -2F, 4F, 9F, 4F, new CubeDeformation(0.45F)), PartPose.ZERO);
                p[2].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(0, 59).addBox(-3F, 5.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[3].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(16, 59).addBox(-1F, 5.8F, -2F, 4F, 1.4F, 4F, new CubeDeformation(0.68F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(44, 30).addBox(-3.6F, 2F, -2.85F, 7.2F, 10F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(56, 59).addBox(-3.6F, 12F, -2.85F, 7.2F, 3.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(106, 59).addBox(-3F, 7.6F, -3.05F, 6F, 2.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c8", CubeListBuilder.create().texOffs(120, 59).addBox(1.6F, 6.4F, -3.3F, 0.6F, 2.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c9", CubeListBuilder.create().texOffs(34, 65).addBox(1.4F, 5F, -3.35F, 1F, 1.6F, 0.3F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c10", CubeListBuilder.create().texOffs(124, 59).addBox(-3.4F, -0.4F, -2.75F, 1F, 2.6F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c11", CubeListBuilder.create().texOffs(0, 65).addBox(2.4F, -0.4F, -2.75F, 1F, 2.6F, 0.35F, new CubeDeformation(0F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c12", CubeListBuilder.create().texOffs(84, 43).addBox(-4.2F, 6.6F, -2.6F, 8.4F, 0.7F, 5.2F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 2 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(80, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(96, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F, new CubeDeformation(0.32F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(0, 52).addBox(-4F, 9.2F, -2F, 8F, 2.8F, 4F, new CubeDeformation(0.36F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(32, 59).addBox(-4F, 10.6F, -2F, 8F, 1.1F, 4F, new CubeDeformation(0.62F)), PartPose.ZERO);
                p[1].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(38, 65).addBox(-0.9F, 10.5F, -2.9F, 1.8F, 1.3F, 0.5F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(4, 65).addBox(-2.2F, 3.8F, -2.6F, 4.4F, 2.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(16, 65).addBox(-2.2F, 3.8F, -2.6F, 4.4F, 2.6F, 0.4F, new CubeDeformation(0F)), PartPose.ZERO);
            }
            case 3 -> {
                p[4].addOrReplaceChild("c0", CubeListBuilder.create().texOffs(62, 30).addBox(-2F, 6.4F, -2F, 4F, 5.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c1", CubeListBuilder.create().texOffs(78, 30).addBox(-2F, 6.4F, -2F, 4F, 5.6F, 4F, new CubeDeformation(0.5F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c2", CubeListBuilder.create().texOffs(24, 52).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c3", CubeListBuilder.create().texOffs(48, 52).addBox(-2.55F, 11.55F, -2.95F, 5.1F, 0.85F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c4", CubeListBuilder.create().texOffs(72, 52).addBox(-2.55F, 11.5F, -2.95F, 5.1F, 0.9F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c5", CubeListBuilder.create().texOffs(96, 52).addBox(-2.55F, 11.5F, -2.95F, 5.1F, 0.9F, 5.6F, new CubeDeformation(0F)), PartPose.ZERO);
                p[4].addOrReplaceChild("c6", CubeListBuilder.create().texOffs(74, 59).addBox(-2F, 6.4F, -2F, 4F, 0.8F, 4F, new CubeDeformation(0.66F)), PartPose.ZERO);
                p[5].addOrReplaceChild("c7", CubeListBuilder.create().texOffs(90, 59).addBox(-2F, 6.4F, -2F, 4F, 0.8F, 4F, new CubeDeformation(0.66F)), PartPose.ZERO);
            }
            default -> {
            }
        }
        return 128;
    }
}
