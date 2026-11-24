package fr.reborn.hud.mur;

/**
 * Ce que l'état de rendu d'un avatar retient de la marche sur les murs ({@code AvatarRenderStateMurMixin}) : la face de la
 * paroi ({@code null} = corps ordinaire), l'avancement de la bascule (0 debout, 1 couché) et le cap du corps sur la paroi
 * (radians, 0 = devant vers le haut). Écrit à l'extraction, lu par {@link MurRendu} à la pose des rotations.
 */
public interface MurRenderState {

    MurFace reborn$murFace();

    float reborn$murBascule();

    float reborn$murCap();

    void reborn$mur(MurFace face, float bascule, float cap);
}
