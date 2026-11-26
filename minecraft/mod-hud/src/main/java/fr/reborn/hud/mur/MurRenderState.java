package fr.reborn.hud.mur;

/**
 * Ce que l'état de rendu d'un avatar retient de la marche sur les murs ({@code AvatarRenderStateMurMixin}) : la face de la
 * paroi ({@code null} = corps ordinaire), l'avancement de la bascule (0 debout, 1 couché), le cap du corps sur la paroi
 * (radians, 0 = devant vers le haut) et le lacet de la paroi (degrés du jeu, celui de {@link MurFace#lacet}, interpolé
 * entre les deux faces pendant un virage de coin), et le lacet du corps debout que vise une sortie animée du joueur local
 * (degrés, NaN ailleurs : le rendu prend alors le lacet du corps du jeu). Écrit à l'extraction, lu par {@link MurRendu} à la
 * pose des rotations.
 */
public interface MurRenderState {

    MurFace reborn$murFace();

    float reborn$murBascule();

    float reborn$murCap();

    float reborn$murLacet();

    float reborn$murDebout();

    void reborn$mur(MurFace face, float bascule, float cap, float lacet, float debout);
}
