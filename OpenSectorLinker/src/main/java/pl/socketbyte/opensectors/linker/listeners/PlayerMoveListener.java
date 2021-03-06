package pl.socketbyte.opensectors.linker.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import pl.socketbyte.opensectors.linker.OpenSectorLinker;
import pl.socketbyte.opensectors.linker.json.controllers.ServerController;
import pl.socketbyte.opensectors.linker.packet.PacketPlayerInfo;
import pl.socketbyte.opensectors.linker.packet.PacketPlayerTransfer;
import pl.socketbyte.opensectors.linker.sector.Sector;
import pl.socketbyte.opensectors.linker.sector.SectorManager;
import pl.socketbyte.opensectors.linker.util.*;
import pl.socketbyte.opensectors.linker.util.reflection.ProtocolManager;

import java.text.DecimalFormat;

public class PlayerMoveListener implements Listener {

    private final DecimalFormat df = new DecimalFormat("#.#");

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL))
            return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        Sector fromSector = SectorManager.INSTANCE.getAt(from);
        Sector toSector = SectorManager.INSTANCE.getAt(to);

        if (fromSector == null || toSector == null) {
            event.setCancelled(true);
            return;
        }

        if (!fromSector.getServerController().name.equals(toSector.getServerController().name))
            event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(OpenSectorLinker.getInstance(), () -> {
            Location from = event.getFrom();
            Location to = event.getTo();

            Sector sector = SectorManager.INSTANCE.getNear(to);
            Sector in = SectorManager.INSTANCE.getAt(to);
            if (sector == null || in == null)
                return;

            double howClose = sector.howClose(to);

            if (SectorManager.INSTANCE.isNear(to)) {
                ProtocolManager.getActionBar().send(player, OpenSectorLinker.getInstance().getConfig().getString("sector-border-close")
                        .replace("%distance%", df.format(howClose).replace(",", "."))
                        .replace("%sector%", sector.getServerController().name));
            }

            if (in.getServerController().id == OpenSectorLinker.getServerId())
                return;

            if (PlayerTransferHolder.getTransfering().contains(player.getUniqueId()))
                return;

            int x = player.getLocation().getBlockX(), z = player.getLocation().getBlockZ();
            ServerController current = SectorManager.INSTANCE.getSectorMap()
                    .get(OpenSectorLinker.getServerId())
                    .getServerController();
            int[] destination = Util.getDestinationWithOffset(current,
                    in.getServerController(), x, z);

            PlayerTransferHolder.getTransfering().add(player.getUniqueId());

            PacketPlayerTransfer packet = new PacketPlayerTransfer();
            packet.setPlayerUniqueId(player.getUniqueId().toString());
            packet.setServerId(in.getServerController().id);

            PacketPlayerInfo packetPlayerInfo = new PacketPlayerInfo(player, destination[0], destination[1]);

            packet.setPlayerInfo(packetPlayerInfo);

            NetworkManager.sendTCP(packet);

            Bukkit.getScheduler().runTaskLater(OpenSectorLinker.getInstance(),
                    () -> PlayerTransferHolder.getTransfering().remove(player.getUniqueId()), 10);
        });
    }
}
