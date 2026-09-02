package fr.reborn.hud.cosmetic;

/**
 * Transform mutable d'un cosmétique 3D porté par le joueur — position, rotation,
 * échelle et point d'ancrage (os du modèle joueur). Lu CHAQUE FRAME par
 * {@link CosmeticFeatureRenderer} : muter cet objet applique l'effet en direct,
 * ce qui alimente l'éditeur visuel ({@code CosmeticEditorScreen}).
 *
 * <p>Les unités de position/échelle sont l'<b>espace bloc vanilla</b> tel que le
 * repère se présente <i>après</i> l'ancrage ({@code translateToHand} /
 * {@code ModelPart.translateAndRotate}) — Y vers le BAS. Les rotations sont en
 * degrés (appliquées dans l'ordre X→Y→Z). Les valeurs par défaut par os d'ancrage
 * ({@link #defaultFor(Anchor)}) donnent un placement raisonnable pour un modèle
 * d'item Nexo, à affiner ensuite via le menu de repositionnement.
 *
 * <p>Champs {@code public} + constructeur vide : sérialisable tel quel par Gson
 * ({@code CosmeticPresets}). L'{@link Anchor} est persisté par son nom.
 */
public final class CosmeticTransform {

    /**
     * Os du modèle joueur sur lequel le cosmétique est ancré. Chaque valeur est
     * projetée sur le {@link net.minecraft.client.model.geom.ModelPart} le plus
     * proche par {@link CosmeticFeatureRenderer} (l'offset couvre le reste :
     * NECK ≈ base de la tête, TORSO/PELVIS ≈ corps décalé, RIGHT/LEFT_ARM = bras).
     */
    public enum Anchor {
        RIGHT_HAND("Main droite"),
        LEFT_HAND("Main gauche"),
        HEAD("Tête"),
        NECK("Cou"),
        TORSO("Torse"),
        PELVIS("Pelvis"),
        RIGHT_ARM("Bras droit"),
        LEFT_ARM("Bras gauche");

        /** Libellé FR affiché dans le menu de repositionnement. */
        public final String label;

        Anchor(String label) {
            this.label = label;
        }

        /** Anchor suivant (cyclique) — pour le bouton de cycle de l'éditeur. */
        public Anchor next() {
            Anchor[] v = values();
            return v[(ordinal() + 1) % v.length];
        }

        /** Anchor précédent (cyclique). */
        public Anchor prev() {
            Anchor[] v = values();
            return v[(ordinal() - 1 + v.length) % v.length];
        }
    }

    public Anchor anchor;
    public float posX, posY, posZ;
    public float rotX, rotY, rotZ;
    public float scale;

    /** Constructeur vide requis par Gson (désérialisation). */
    public CosmeticTransform() {
        this.anchor = Anchor.RIGHT_HAND;
        this.scale = 1f;
    }

    public CosmeticTransform(Anchor anchor,
                             float posX, float posY, float posZ,
                             float rotX, float rotY, float rotZ,
                             float scale) {
        this.anchor = anchor;
        this.posX = posX; this.posY = posY; this.posZ = posZ;
        this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
        this.scale = scale;
    }

    /**
     * Placement par défaut « raisonnable » pour un os d'ancrage donné, quand aucun
     * preset n'a encore été sauvegardé pour le cosmétique. Les valeurs visent un
     * modèle d'item Nexo rendu en {@code FIXED}/{@code HEAD} (≈ 1 bloc) — l'échelle
     * est réduite pour tenir sur la partie du corps visée, la position dans l'espace
     * de l'os (Y vers le BAS après {@code translateAndRotate}). À affiner ensuite en
     * direct via le menu de repositionnement.
     */
    public static CosmeticTransform defaultFor(Anchor anchor) {
        Anchor a = anchor != null ? anchor : Anchor.HEAD;
        return switch (a) {
            case HEAD       -> new CosmeticTransform(a, 0.0f, 0.15f, -0.10f, 0f, 0f, 0f, 0.75f);
            case NECK       -> new CosmeticTransform(a, 0.0f, 0.10f, -0.05f, 0f, 0f, 0f, 0.65f);
            case TORSO      -> new CosmeticTransform(a, 0.0f, 0.00f,  0.00f, 0f, 0f, 0f, 0.90f);
            case PELVIS     -> new CosmeticTransform(a, 0.0f, 0.00f,  0.00f, 0f, 0f, 0f, 0.80f);
            case RIGHT_ARM, LEFT_ARM   -> new CosmeticTransform(a, 0.0f, 0.35f, 0f, 0f, 0f, 0f, 0.55f);
            case RIGHT_HAND, LEFT_HAND -> new CosmeticTransform(a, 0.0f, 0.45f, 0f, 0f, 0f, 0f, 0.50f);
        };
    }

    /**
     * Sérialise en chaîne compacte {@code ANCHOR,px,py,pz,rx,ry,rz,scale} — sans
     * {@code :} ni {@code "} (safe à embarquer dans le broadcast {@code reborn:cosmetics}
     * pour que les AUTRES joueurs voient le placement appliqué). {@code Float.toString}
     * est locale-indépendant (toujours un point décimal).
     */
    public String serialize() {
        return (anchor != null ? anchor.name() : "HEAD")
            + "," + posX + "," + posY + "," + posZ
            + "," + rotX + "," + rotY + "," + rotZ + "," + scale;
    }

    /** Inverse de {@link #serialize()}. Renvoie {@code null} si la chaîne est invalide. */
    public static CosmeticTransform deserialize(String s) {
        if (s == null) return null;
        String[] p = s.split(",");
        if (p.length != 8) return null;
        try {
            Anchor a;
            try { a = Anchor.valueOf(p[0].trim()); } catch (IllegalArgumentException e) { a = Anchor.HEAD; }
            return new CosmeticTransform(a,
                Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]), Float.parseFloat(p[6]),
                Float.parseFloat(p[7]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Copie profonde (les champs sont primitifs + enum immuable). */
    public CosmeticTransform copy() {
        return new CosmeticTransform(anchor, posX, posY, posZ, rotX, rotY, rotZ, scale);
    }

    /** Recopie tous les champs d'un autre transform dans celui-ci (in place). */
    public void copyFrom(CosmeticTransform o) {
        if (o == null) return;
        this.anchor = o.anchor != null ? o.anchor : Anchor.RIGHT_HAND;
        this.posX = o.posX; this.posY = o.posY; this.posZ = o.posZ;
        this.rotX = o.rotX; this.rotY = o.rotY; this.rotZ = o.rotZ;
        this.scale = o.scale;
    }

    /** Restaure les valeurs par défaut fournies (reset-to-default de l'éditeur). */
    public void reset(CosmeticTransform defaults) {
        copyFrom(defaults);
    }
}
