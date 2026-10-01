package com.empresa.offboarding.service;

import java.util.List;

public final class AssetSystemNames {
    private AssetSystemNames() {}
    private static final List<String> NAMES=List.of(
        "Equipo de Computo","Equipo de c\u00f3mputo",
        "Equipo de c\u00c3\u00b3mputo",
        "Telefonia","Telefon\u00eda","Telefon\u00c3\u00ada",
        "Activos asignados");

    public static boolean isAsset(String name) {
        return name!=null&&NAMES.stream().anyMatch(v->v.equalsIgnoreCase(name.strip()));
    }
}