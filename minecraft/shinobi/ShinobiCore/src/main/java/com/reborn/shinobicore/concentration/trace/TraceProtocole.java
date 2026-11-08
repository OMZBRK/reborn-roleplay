package com.reborn.shinobicore.concentration.trace;

import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Le message {@code traces} du canal {@code reborn:concentration} (PLUGINS_REFERENCE §4 undecies) :
 * <pre>{"v":1,"t":"traces","plein":true,"o":[ox,oy,oz],"a":[…],"r":[…]}</pre>
 * {@code plein} : la liste remplace tout (à l'entrée, au changement de monde). {@code o} : origine (bloc). {@code a} : les
 * pas nouveaux, à plat, {@value #CHAMPS} entiers par pas :
 * {@code id, clé de piste, dx, dy, dz (1/32 de bloc depuis o), angle, drapeaux, échelle %, âge (s), vie (s)}.
 * {@code r} : les id à oublier. De 30 à 53 octets par pas (âges et vies à 5 chiffres) ; jamais de nom ni d'UUID.
 */
final class TraceProtocole {

    static final int CHAMPS = 10;
    static final double PRECISION = 32.0;

    private TraceProtocole() {}

    static String message(int version, boolean plein, int ox, int oy, int oz, List<Trace> ajouts, Collection<Integer> retraits,
                          long now, ToIntFunction<Trace> cle) {
        StringBuilder s = new StringBuilder(64 + ajouts.size() * 36 + retraits.size() * 8);
        s.append("{\"v\":").append(version).append(",\"t\":\"traces\"");
        if (plein) s.append(",\"plein\":true");
        s.append(",\"o\":[").append(ox).append(',').append(oy).append(',').append(oz).append("],\"a\":[");
        boolean premier = true;
        for (Trace t : ajouts) {
            if (!premier) s.append(',');
            premier = false;
            s.append(t.id).append(',').append(cle.applyAsInt(t)).append(',')
                .append(Math.round((t.x - ox) * PRECISION)).append(',')
                .append(Math.round((t.y - oy) * PRECISION)).append(',')
                .append(Math.round((t.z - oz) * PRECISION)).append(',')
                .append(t.angle).append(',').append(t.drapeaux).append(',').append(t.k).append(',')
                .append(t.ageSecondes(now)).append(',').append(t.vie);
        }
        s.append("],\"r\":[");
        premier = true;
        for (int id : retraits) {
            if (!premier) s.append(',');
            premier = false;
            s.append(id);
        }
        return s.append("]}").toString();
    }
}
