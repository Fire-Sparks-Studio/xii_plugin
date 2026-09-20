package com.mceteams.xii.enums;

/**
 * Types de structures .nbt fournies par le développeur (spec §36).
 * Le plugin ne génère JAMAIS l'architecture lui-même : il charge et
 * place uniquement les structures fournies dans resources/structures.
 */
public enum StructureType {
    /** resources/structures/waiting/waiting_lobby.nbt */
    WAITING_LOBBY,
    /** resources/structures/bases/bases.nbt (template unique des 4 équipes) */
    BASE,
    /** resources/structures/dungeons/dungeon_<1..4>.nbt */
    DUNGEON
}
