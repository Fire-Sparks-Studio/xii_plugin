package com.mceteams.xii.scoreboard.tab;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.world.level.GameType;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Envoi des "fausses entrées" du TAB via le paquet réseau joueur_info
 * (ClientboundPlayerInfoUpdatePacket) SANS ProtocolLib : accès NMS
 * Mojang-mapped fourni par le dev bundle paperweight.
 *
 * Contrats client vérifiés (1.21.11) :
 * - une entrée n'est listée QUE si l'action UPDATE_LISTED passe listed=true ;
 * - l'action ADD_PLAYER crée l'entrée (profil obligatoire) ;
 * - UPDATE_DISPLAY_NAME / UPDATE_LIST_ORDER rafraîchissent ligne et tri.
 */
public final class PlayerInfoPackets {

    /** Actions envoyées à la CRÉATION d'une entrée. */
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> ADD_ACTIONS =
            EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);

    /** Actions envoyées lors d'un simple CHANGEMENT de texte / d'ordre. */
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> UPDATE_ACTIONS =
            EnumSet.of(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME,
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LIST_ORDER);

    private PlayerInfoPackets() {
    }

    public static void add(Player viewer, Collection<FakeTabEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        send(viewer, new ClientboundPlayerInfoUpdatePacket(
                ADD_ACTIONS, toEntries(entries)));
    }

    public static void update(Player viewer, Collection<FakeTabEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        send(viewer, new ClientboundPlayerInfoUpdatePacket(
                UPDATE_ACTIONS, toEntries(entries)));
    }

    public static void remove(Player viewer, Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return;
        }
        send(viewer, new ClientboundPlayerInfoRemovePacket(
                new ArrayList<>(uuids)));
    }

    /**
     * Re-publie un JOUEUR RÉEL dans le TAB d'un spectateur après un
     * hidePlayer : le hide (Bukkit) retire aussi l'entrée du TAB, on la
     * remet au niveau paquet (profil réel avec peau, game mode, ping,
     * ordre) pour qu'il reste affiché dans la liste tout en étant
     * toujours invisible dans le monde.
     */
    public static void readd(Player viewer, Player target,
                             Component displayName, int listOrder) {
        if (viewer == null || target == null
                || viewer.equals(target)
                || !viewer.isOnline() || !target.isOnline()) {
            return;
        }
        GameProfile profile = ((CraftPlayer) target).getProfile();
        var entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                target.getUniqueId(),
                profile,
                true,                              // listed : visible dans le TAB
                target.getPing(),
                GameType.byId(target.getGameMode().getValue()),
                displayName,
                false,
                listOrder,
                null);
        send(viewer, new ClientboundPlayerInfoUpdatePacket(
                ADD_ACTIONS, List.of(entry)));
    }

    private static List<ClientboundPlayerInfoUpdatePacket.Entry> toEntries(
            Collection<FakeTabEntry> entries) {
        List<ClientboundPlayerInfoUpdatePacket.Entry> list =
                new ArrayList<>(entries.size());
        for (FakeTabEntry entry : entries) {
            // Profil "tête" fourni par l'entrée (ex : bloc gris des lignes
            // vides) ou repli : profil vide au nom déterministe.
            GameProfile profile = entry.profile() != null
                    ? entry.profile()
                    : new GameProfile(entry.uuid(), entry.profileName());
            Component display = CraftChatMessage.fromStringOrNull(entry.displayName());
            list.add(new ClientboundPlayerInfoUpdatePacket.Entry(
                    entry.uuid(),
                    profile,
                    true,                              // listed : visible dans le TAB
                    0,                                 // latence
                    GameType.SURVIVAL,
                    display,
                    false,                             // pas de chapeau
                    entry.listOrder(),
                    null));                            // pas de session de chat
        }
        return list;
    }

    private static void send(Player viewer, Packet<?> packet) {
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        try {
            var connection = ((CraftPlayer) viewer).getHandle().connection;
            if (connection != null) {
                connection.send(packet);
            }
        } catch (Throwable ignored) {
            // Joueur déconnecté en plein envoi : entrée ignorée.
        }
    }
}