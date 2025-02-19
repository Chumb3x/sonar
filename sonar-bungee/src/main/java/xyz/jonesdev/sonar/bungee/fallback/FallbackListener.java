/*
 * Copyright (C) 2023 Sonar Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xyz.jonesdev.sonar.bungee.fallback;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.protocol.packet.Kick;
import org.jetbrains.annotations.NotNull;
import xyz.jonesdev.sonar.api.Sonar;

@RequiredArgsConstructor
public final class FallbackListener implements Listener {

  @EventHandler
  @SuppressWarnings("deprecation")
  public void handle(final @NotNull PostLoginEvent event) {
    if (Sonar.get().getConfig().getLockdown().isEnabled()) {
      if (!event.getPlayer().hasPermission(Sonar.get().getConfig().getLockdown().getBypassPermission())) {
        final PendingConnection pendingConnection = event.getPlayer().getPendingConnection();
        // Try to close the channel with a custom serialized disconnect component
        if (pendingConnection instanceof FallbackInitialHandler) {
          final FallbackInitialHandler fallbackInitialHandler = (FallbackInitialHandler) pendingConnection;
          final Component component = Sonar.get().getConfig().getLockdown().getDisconnect();
          final String serialized = JSONComponentSerializer.json().serialize(component);
          fallbackInitialHandler.closeWith(new Kick(serialized));
        } else {
          // Fallback by disconnecting without a message
          pendingConnection.disconnect();
          Sonar.get().getLogger().warn("Fallback handler of {} is missing", event.getPlayer().getName());
          return;
        }

        if (Sonar.get().getConfig().getLockdown().isLogAttempts()) {
          Sonar.get().getLogger().info(Sonar.get().getConfig().getLockdown().getConsoleLog()
            .replace("%player%", event.getPlayer().getName())
            .replace("%ip%", Sonar.get().getConfig()
              .formatAddress(event.getPlayer().getAddress().getAddress()))
            .replace("%protocol%",
              String.valueOf(event.getPlayer().getPendingConnection().getVersion())));
        }
      } else if (Sonar.get().getConfig().getLockdown().isNotifyAdmins()) {
        event.getPlayer().sendMessage(new TextComponent(Sonar.get().getConfig().getLockdown().getNotification()));
      }
    }
  }
}
