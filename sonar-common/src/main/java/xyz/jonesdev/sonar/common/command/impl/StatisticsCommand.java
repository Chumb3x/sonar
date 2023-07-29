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

package xyz.jonesdev.sonar.common.command.impl;

import org.jetbrains.annotations.NotNull;
import xyz.jonesdev.sonar.api.command.CommandInvocation;
import xyz.jonesdev.sonar.api.command.subcommand.Subcommand;
import xyz.jonesdev.sonar.api.command.subcommand.SubcommandInfo;

import static xyz.jonesdev.sonar.api.Sonar.DECIMAL_FORMAT;

@SubcommandInfo(
  name = "statistics",
  aliases = {"stats"},
  description = "Show session statistics of this server"
)
public final class StatisticsCommand extends Subcommand {

  @Override
  public void execute(final @NotNull CommandInvocation invocation) {
    final long total = SONAR.getStatistics().get("total", 0);
    final long queued = SONAR.getFallback().getQueue().getQueuedPlayers().size();
    final long verifying = SONAR.getFallback().getConnected().size();
    final long verified = SONAR.getFallback().getVerified().size();
    final int blacklisted = SONAR.getFallback().getBlacklisted().estimatedSize();

    invocation.getSender().sendMessage();
    invocation.getSender().sendMessage(" §eStatistics (this session)");
    invocation.getSender().sendMessage();
    invocation.getSender().sendMessage(" §a▪ §7Verified IP addresses: §f" + DECIMAL_FORMAT.format(verified));
    invocation.getSender().sendMessage(" §a▪ §7Verifying IP addresses: §f" + DECIMAL_FORMAT.format(verifying));
    invocation.getSender().sendMessage(" §a▪ §7Blacklisted IP addresses: §f" + DECIMAL_FORMAT.format(blacklisted));
    invocation.getSender().sendMessage(" §a▪ §7Queued connections: §f" + DECIMAL_FORMAT.format(queued));
    invocation.getSender().sendMessage(" §a▪ §7Total connections: §f" + DECIMAL_FORMAT.format(total));
    invocation.getSender().sendMessage();
  }
}