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

package xyz.jonesdev.sonar.api.config;

import lombok.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import xyz.jonesdev.sonar.api.Sonar;
import xyz.jonesdev.sonar.api.command.SonarCommand;
import xyz.jonesdev.sonar.api.dependencies.Dependency;

import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static xyz.jonesdev.sonar.api.Sonar.LINE_SEPARATOR;

public final class SonarConfiguration {
  private final @NotNull File pluginFolder;
  @Getter
  private SimpleYamlConfig generalConfig, messagesConfig;

  @Getter
  private final Verbose verbose = new Verbose();
  @Getter
  private final Commands commands = new Commands();
  @Getter
  private final Queue queue = new Queue();
  @Getter
  private final Verification verification = new Verification();
  @Getter
  private final Lockdown lockdown = new Lockdown();
  @Getter
  private final Database database = new Database();

  public SonarConfiguration(final @NotNull File pluginFolder) {
    this.pluginFolder = pluginFolder;
  }

  @Getter
  private String prefix;
  private String supportUrl;
  @Getter
  private String noPermission;
  private String header, footer;
  private boolean logPlayerAddresses;
  @Getter
  private int maxOnlinePerIp;
  @Getter
  private int minPlayersForAttack;
  @Getter
  private Component tooManyOnlinePerIp;

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Verbose {
    private String actionBarLayout;
    private List<String> animation;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Queue {
    private int maxQueuePolls;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Verification {
    private boolean enabled;
    private boolean checkGravity;
    private boolean checkCollisions;
    private boolean logConnections;
    private boolean logDuringAttack;
    private boolean debugXYZPositions;
    private Pattern validNameRegex;
    private Pattern validBrandRegex;
    private Pattern validLocaleRegex;
    private String connectLog;
    private String failedLog;
    private String successLog;
    private String blacklistLog;
    private short gamemodeId;
    private int maxBrandLength;
    private int maxMovementTicks;
    private int maxIgnoredTicks;
    private int maxVerifyingPlayers;
    private int maxLoginPackets;
    private int maxPing;
    private int readTimeout;
    private int reconnectDelay;

    private Component tooManyPlayers;
    private Component tooFastReconnect;
    private Component invalidUsername;
    private Component invalidProtocol;
    private Component alreadyConnected;
    private Component verificationSuccess;
    private Component verificationFailed;
    private Component alreadyVerifying;
    private Component alreadyQueued;
    private Component blacklisted;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Commands {
    private String incorrectCommandUsage;
    private String incorrectIpAddress;
    private String illegalIpAddress;
    private String unknownIpAddress;
    private String subCommandNoPerm;
    private String playersOnly;
    private String consoleOnly;
    private String commandCoolDown;
    private String commandCoolDownLeft;

    private String blacklistEmpty;
    private String blacklistAdd;
    private String blacklistAddWarning;
    private String blacklistDuplicate;
    private String blacklistNotFound;
    private String blacklistRemove;
    private String blacklistCleared;
    private String blacklistSize;

    private String verifiedRemove;
    private String verifiedNotFound;
    private String verifiedCleared;
    private String verifiedSize;
    private String verifiedEmpty;
    private String verifiedBlocked;

    private String statisticsHeader;
    private String unknownStatisticType;
    private String generalStatistics;
    private String memoryStatistics;
    private String networkStatistics;
    private String cpuStatistics;

    private List<String> helpHeader;
    private String helpSubcommands;

    private String verboseSubscribed;
    private String verboseUnsubscribed;

    private String reloading;
    private String reloaded;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Database {

    @Getter
    @RequiredArgsConstructor
    public enum Type {
      MYSQL(new Dependency[]{Dependency.MYSQL}, "com.mysql.cj.jdbc.NonRegisteringDriver"),
      MARIADB(new Dependency[]{Dependency.MYSQL, Dependency.MARIADB}, "org.mariadb.jdbc.Driver"),
      NONE(null, null);

      private final Dependency[] dependencies;
      private final String driverClassName;
    }

    private Type type;
    private String url;
    private int port;
    private String name;
    private String username;
    private String password;
  }

  @Getter
  @NoArgsConstructor(access = AccessLevel.PRIVATE)
  public static final class Lockdown {
    @Setter
    private boolean enabled;
    private boolean notifyAdmins;
    private boolean logAttempts;
    private Component disconnect;
    private String bypassPermission;
    private String activated;
    private String deactivated;
    private String notification;
    private String consoleLog;
  }

  public void load() {
    if (generalConfig == null) {
      generalConfig = new SimpleYamlConfig(pluginFolder, "config");
    }
    try {
      generalConfig.load();
    } catch (Exception exception) {
      // https://github.com/jonesdevelopment/sonar/issues/33
      // Only save the configuration when necessary
      Sonar.get().getLogger().error("Error while loading configuration: {}", exception);
      return;
    }

    // General options
    generalConfig.getYaml().setComment("language",
      "Name of the language file Sonar should use for messages"
    );
    String language = generalConfig.getString("language", "en");

    generalConfig.getYaml().setComment("max-online-per-ip",
      "Maximum number of players online with the same IP address"
    );
    maxOnlinePerIp = clamp(generalConfig.getInt("max-online-per-ip", 3), 1, Byte.MAX_VALUE);

    generalConfig.getYaml().setComment("min-players-for-attack",
      "Minimum number of new players in order for an attack to be detected"
    );
    minPlayersForAttack = clamp(generalConfig.getInt("min-players-for-attack", 5), 2, 1024);

    generalConfig.getYaml().setComment("log-player-addresses",
      "Should Sonar log players' IP addresses in console?"
    );
    logPlayerAddresses = generalConfig.getBoolean("log-player-addresses", true);

    // Message settings
    // Only create a new messages configuration object if the preferred language changed
    // https://github.com/jonesdevelopment/sonar/issues/26
    if (messagesConfig == null || !messagesConfig.getFile().getName().equals(language + ".yml")) {
      messagesConfig = new SimpleYamlConfig(pluginFolder, "translations/" + language);
    }
    try {
      messagesConfig.load();
    } catch (Exception exception) {
      // https://github.com/jonesdevelopment/sonar/issues/33
      // Only save the configuration when necessary
      Sonar.get().getLogger().error("Error while loading configuration: {}", exception);
      return;
    }

    messagesConfig.getYaml().setComment("prefix",
      "Placeholder for every '%prefix%' in this configuration file");
    prefix = formatString(messagesConfig.getString("prefix", "<yellow><bold>Sonar<reset> <gray>» <white>"));

    messagesConfig.getYaml().setComment("support-url",
      "Placeholder for every '%support-url%' in this configuration file");
    supportUrl = messagesConfig.getString("support-url", "https://jonesdev.xyz/discord/");

    // Database
    generalConfig.getYaml().setComment("database.type",
      "Type of database Sonar uses to store verified players"
        + LINE_SEPARATOR + "Possible types: NONE, MYSQL, MARIADB (experimental)"
    );
    final String newDatabaseType = generalConfig.getString("database.type", Database.Type.NONE.name());
    database.type = Database.Type.valueOf(newDatabaseType.toUpperCase());

    generalConfig.getYaml().setComment("database",
      "You can connect Sonar to a database to keep verified players even after restarting your server"
        + LINE_SEPARATOR + "Note: IP addresses are saved in plain text. You are responsible for keeping your database" +
        " safe!"
        + LINE_SEPARATOR + "However, IP addresses cannot be traced back to players as Sonar uses UUIDs instead of " +
        "usernames");
    generalConfig.getYaml().setComment("database.url",
      "URL for authenticating with the SQL database");
    database.url = generalConfig.getString("database.url", "localhost");

    generalConfig.getYaml().setComment("database.port",
      "Port for authenticating with the SQL database");
    database.port = generalConfig.getInt("database.port", 3306);

    generalConfig.getYaml().setComment("database.name",
      "Name of the SQL database");
    database.name = generalConfig.getString("database.name", "sonar");

    generalConfig.getYaml().setComment("database.username",
      "Username for authenticating with the SQL database");
    database.username = generalConfig.getString("database.username", "");

    generalConfig.getYaml().setComment("database.password",
      "Password for authenticating with the SQL database");
    database.password = generalConfig.getString("database.password", "");

    // Lockdown
    generalConfig.getYaml().setComment("lockdown",
      "You can lock your server down using '/sonar lockdown' if, for example,"
        + LINE_SEPARATOR + "bots are bypassing the verification or any other reason");
    generalConfig.getYaml().setComment("lockdown.enabled",
      "Should Sonar prevent all players from joining the server?");
    lockdown.enabled = generalConfig.getBoolean("lockdown.enabled", false);

    generalConfig.getYaml().setComment("lockdown.log-attempts",
      "Should Sonar log new login attempts during lockdown?");
    lockdown.logAttempts = generalConfig.getBoolean("lockdown.log-attempts", true);

    generalConfig.getYaml().setComment("lockdown.notify-admins",
      "Should Sonar notify admins when they join the server during lockdown?");
    lockdown.notifyAdmins = generalConfig.getBoolean("lockdown.notify-admins", true);

    generalConfig.getYaml().setComment("lockdown.bypass-permission",
      "Which permission does a player need in order to bypass the lockdown mode?"
        + LINE_SEPARATOR + "Players with this permission will also receive admin notifications when joining");
    lockdown.bypassPermission = generalConfig.getString("lockdown.bypass-permission", "sonar.lockdown.bypass");

    // Queue
    generalConfig.getYaml().setComment("queue",
      "Every new login request will be queued to avoid spam join attacks"
        + LINE_SEPARATOR + "The queue is updated every 500 milliseconds (10 ticks)");
    generalConfig.getYaml().setComment("queue.max-polls",
      "Maximum number of concurrent queue polls per 500 milliseconds");
    queue.maxQueuePolls = clamp(generalConfig.getInt("queue.max-polls", 30), 1, 1000);

    // Verification
    generalConfig.getYaml().setComment("verification",
      "Every new player that joins for the first time will be sent to"
        + LINE_SEPARATOR + "a lightweight limbo server where advanced bot checks are performed");
    generalConfig.getYaml().setComment("verification.enabled",
      "Should Sonar verify new players? (Recommended)");
    verification.enabled = generalConfig.getBoolean("verification.enabled", true);

    generalConfig.getYaml().setComment("verification.checks.gravity",
      "Checks if the players' falling motion is following Minecraft's gravity formula"
        + LINE_SEPARATOR + "All predicted motions are precalculated in order to save performance");
    generalConfig.getYaml().setComment("verification.checks.gravity.enabled",
      "Should Sonar check for valid client gravity? (Recommended)");
    verification.checkGravity = generalConfig.getBoolean("verification.checks.gravity.enabled", true);

    generalConfig.getYaml().setComment("verification.checks.gravity.max-movement-ticks",
      "Maximum number of ticks the player has to fall in order to be allowed to hit the platform");
    verification.maxMovementTicks = clamp(generalConfig.getInt("verification.checks.gravity.max-movement-ticks", 8),
      2, 100);

    generalConfig.getYaml().setComment("verification.checks.gravity.max-ignored-ticks",
      "Maximum number of ignored Y movement changes before a player fails verification");
    verification.maxIgnoredTicks = clamp(generalConfig.getInt("verification.checks.gravity.max-ignored-ticks", 5), 1,
      128);

    generalConfig.getYaml().setComment("verification.checks.collisions",
      "Checks if the players collides with barrier blocks spawned below the player"
        + LINE_SEPARATOR + "Note: The collision check will be skipped if the gravity check is disabled");
    generalConfig.getYaml().setComment("verification.checks.collisions.enabled",
      "Should Sonar check for valid client collisions? (Recommended)");
    verification.checkCollisions = generalConfig.getBoolean("verification.checks.collisions.enabled", true);

    generalConfig.getYaml().setComment("verification.gamemode",
      "The gamemode of the player during verification (0, 1, 2, or 3)");
    verification.gamemodeId = (short) clamp(generalConfig.getInt("verification.gamemode", 3), 0, 3);

    generalConfig.getYaml().setComment("verification.log-connections",
      "Should Sonar log new verification attempts?");
    verification.logConnections = generalConfig.getBoolean("verification.log-connections", true);

    generalConfig.getYaml().setComment("verification.log-during-attack",
      "Should Sonar log new verification attempts during attacks?");
    verification.logDuringAttack = generalConfig.getBoolean("verification.log-during-attack", false);

    generalConfig.getYaml().setComment("verification.debug-xyz-positions",
      "Should Sonar log every single movement/position change during verification?"
        + LINE_SEPARATOR + "This is not recommended for production servers but can be helpful for spotting errors.");
    verification.debugXYZPositions = generalConfig.getBoolean("verification.debug-xyz-positions", false);

    generalConfig.getYaml().setComment("verification.valid-name-regex",
      "Regex for validating usernames during verification");
    verification.validNameRegex = Pattern.compile(generalConfig.getString(
      "verification.valid-name-regex", "^[a-zA-Z0-9_.*!]+$"));

    generalConfig.getYaml().setComment("verification.valid-brand-regex",
      "Regex for validating client brands during verification");
    verification.validBrandRegex = Pattern.compile(generalConfig.getString(
      "verification.valid-brand-regex", "^[!-~ ]+$"));

    generalConfig.getYaml().setComment("verification.valid-locale-regex",
      "Regex for validating client locale during verification");
    verification.validLocaleRegex = Pattern.compile(generalConfig.getString(
      "verification.valid-locale-regex", "^[a-zA-Z_]+$"));

    generalConfig.getYaml().setComment("verification.max-brand-length",
      "Maximum client brand length during verification");
    verification.maxBrandLength = generalConfig.getInt("verification.max-brand-length", 64);

    generalConfig.getYaml().setComment("verification.max-ping",
      "Ping (in milliseconds) a player has to have in order to timeout");
    verification.maxPing = clamp(generalConfig.getInt("verification.max-ping", 10000), 500, 30000);

    generalConfig.getYaml().setComment("verification.read-timeout",
      "Amount of time that has to pass before a player times out");
    verification.readTimeout = clamp(generalConfig.getInt("verification.read-timeout", 3500), 500, 30000);

    generalConfig.getYaml().setComment("verification.max-login-packets",
      "Maximum number of login packets the player has to send in order to be kicked");
    verification.maxLoginPackets = clamp(generalConfig.getInt("verification.max-login-packets", 256), 128, 8192);

    generalConfig.getYaml().setComment("verification.max-players",
      "Maximum number of players verifying at the same time");
    verification.maxVerifyingPlayers = clamp(generalConfig.getInt("verification.max-players", 1024), 1,
      Short.MAX_VALUE);

    generalConfig.getYaml().setComment("verification.rejoin-delay",
      "Minimum number of rejoin delay during verification");
    verification.reconnectDelay = clamp(generalConfig.getInt("verification.rejoin-delay", 8000), 0, 100000);

    // load this here otherwise it could cause issues
    messagesConfig.getYaml().setComment("header",
      "Placeholder for every '%header%' in this configuration file");
    header = fromList(messagesConfig.getStringList("header",
      Arrays.asList(
        "<yellow><bold>Sonar<reset>",
        "<reset>"
      )));

    messagesConfig.getYaml().setComment("footer",
      "Placeholder for every '%footer%' in this configuration file");
    footer = fromList(messagesConfig.getStringList("footer",
      Arrays.asList("<gray>If you believe that this is an error, contact an administrator.")));

    messagesConfig.getYaml().setComment("too-many-online-per-ip",
      "Disconnect message that is shown when someone joins but there are too many online players with their IP " +
        "address");
    tooManyOnlinePerIp = deserialize(fromList(messagesConfig.getStringList("too-many-online-per-ip",
      Arrays.asList(
        "%header%",
        "<red>There are too many players online with your IP address.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("commands.no-permission",
      "Message that is shown when a player tries running /sonar without permission");
    noPermission = formatString(messagesConfig.getString("commands.no-permission",
      "%prefix%<red>You do not have permission to execute this command."));

    messagesConfig.getYaml().setComment("commands.incorrect-usage",
      "Message that is shown when someone uses a command incorrectly");
    commands.incorrectCommandUsage = formatString(messagesConfig.getString("commands.incorrect-usage",
      "%prefix%<red>Usage: /sonar %usage%"));

    messagesConfig.getYaml().setComment("commands.invalid-ip-address",
      "Message that is shown when someone provides an invalid IP address (Invalid characters)");
    commands.incorrectIpAddress = formatString(messagesConfig.getString("commands.invalid-ip-address",
      "%prefix%The IP address you provided seems to be invalid."));

    messagesConfig.getYaml().setComment("commands.illegal-ip-address",
      "Message that is shown when someone provides an illegal IP address (Local IP)");
    commands.illegalIpAddress = formatString(messagesConfig.getString("commands.illegal-ip-address",
      "%prefix%The IP address you provided seems to be either a local or loopback IP."));

    messagesConfig.getYaml().setComment("commands.unknown-ip-address",
      "Message that is shown when someone provides an unknown IP address (Unknown Host)");
    commands.unknownIpAddress = formatString(messagesConfig.getString("commands.unknown-ip-address",
      "%prefix%The IP address you provided seems to be unknown."));

    messagesConfig.getYaml().setComment("commands.player-only",
      "Message that is shown when the console runs a command that is player-only");
    commands.playersOnly = formatString(messagesConfig.getString("commands.player-only",
      "%prefix%<red>You can only execute this command as a player."));

    messagesConfig.getYaml().setComment("commands.console-only",
      "Message that is shown when a player runs a command that is console-only");
    commands.consoleOnly = formatString(messagesConfig.getString("commands.console-only",
      "%prefix%<red>For security reasons, you can only execute this command through console."));

    messagesConfig.getYaml().setComment("commands.cool-down",
      "Message that is shown when a player executes Sonar commands too quickly");
    commands.commandCoolDown = formatString(messagesConfig.getString("commands.cool-down",
      "%prefix%<red>You can only execute this command every 0.5 seconds."));
    commands.commandCoolDownLeft = formatString(messagesConfig.getString("commands.cool-down-left",
      "%prefix%<red>Please wait another <bold>%time-left%s<reset><red>."));

    messagesConfig.getYaml().setComment("commands.subcommand-no-permission",
      "Message that is shown when a player does not have permission to execute a certain subcommand");
    commands.subCommandNoPerm = formatString(messagesConfig.getString("commands.subcommand-no-permission",
      "%prefix%<red>You do not have permission to execute this subcommand. <gray>(%permission%)"));

    messagesConfig.getYaml().setComment("lockdown",
      "Translations for '/sonar lockdown'");
    messagesConfig.getYaml().setComment("lockdown.notification",
      "Message that is shown when an admin joins the server during lockdown");
    lockdown.notification = formatString(messagesConfig.getString("lockdown.notification",
      "%prefix%<green>Hey, the server is currently in lockdown mode. If you want to disable the lockdown mode,"
        + " type <white>/sonar lockdown<green>."
    ));

    messagesConfig.getYaml().setComment("lockdown.console-log",
      "Message that is shown to console when a normal player tries joining the server during lockdown");
    lockdown.consoleLog = messagesConfig.getString("lockdown.console-log",
      "%player% (%ip%, %protocol%) tried to join during lockdown mode.");

    messagesConfig.getYaml().setComment("lockdown.disconnect-message",
      "Message that is shown to a normal player when they try joining the server during lockdown");
    lockdown.disconnect = deserialize(fromList(messagesConfig.getStringList("lockdown.disconnect-message",
      Arrays.asList(
        "%header%",
        "<red>The server is currently locked down, please try again later.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("commands.main",
      "Translations for '/sonar'");
    messagesConfig.getYaml().setComment("commands.main.header",
      "Informational message that is shown above everything when running the main command");
    commands.helpHeader = messagesConfig.getStringList("commands.main.header",
      Arrays.asList(
        "<yellow>Running Sonar %version% on %platform%.",
        "<yellow>(C) %copyright_year% Jones Development and Sonar Contributors",
        "<green><click:open_url:'https://github.com/jonesdevelopment/sonar'>https://github.com/jonesdevelopment/sonar",
        "",
        "<yellow>Need help or have any questions?",
        "<yellow><click:open_url:'https://jonesdev.xyz/discord/'><hover:show_text:'(Click to open Discord)'>Open a " +
          "ticket on the Discord </hover></click></yellow><yellow><click:open_url:'https://github" +
          ".com/jonesdevelopment/sonar/issues'><hover:show_text:'(Click to open GitHub)'>or open a new issue on " +
          "GitHub.",
        ""
      ));
    messagesConfig.getYaml().setComment("commands.main.subcommands",
      "Formatting of the list of subcommands shown when running the main command");
    commands.helpSubcommands = formatString(messagesConfig.getString("commands.main.subcommands",
      "<click:suggest_command:'/sonar %subcommand% '><hover:show_text:'<gray>Only players: " +
        "</gray>%only_players%<br><gray>Require console: </gray>%require_console%<br><gray>Permission: " +
        "</gray><white>%permission%<br><gray>Aliases: </gray>%aliases%'><gray> ▪ </gray><green>/sonar " +
        "%subcommand%</green><gray> - </gray><white>%description%"));

    SonarCommand.prepareCachedMessages();

    messagesConfig.getYaml().setComment("commands.reload",
      "Translations for '/sonar reload'");
    messagesConfig.getYaml().setComment("commands.reload.start",
      "Message that is shown when someone starts reloading Sonar");
    commands.reloading = formatString(messagesConfig.getString("commands.reload.start",
      "%prefix%Reloading Sonar..."));

    messagesConfig.getYaml().setComment("commands.reload.finish",
      "Message that is shown when Sonar has finished reloading");
    commands.reloaded = formatString(messagesConfig.getString("commands.reload.finish",
      "%prefix%<green>Successfully reloaded <gray>(%taken%ms)"));

    messagesConfig.getYaml().setComment("commands.lockdown",
      "Translations for '/sonar lockdown'");
    messagesConfig.getYaml().setComment("commands.lockdown.enabled",
      "Message that is shown when a player enables server lockdown");
    lockdown.activated = formatString(messagesConfig.getString("commands.lockdown.enabled",
      "%prefix%The server is now in lockdown mode."));

    messagesConfig.getYaml().setComment("commands.lockdown.disabled",
      "Message that is shown when a player disables server lockdown");
    lockdown.deactivated = formatString(messagesConfig.getString("commands.lockdown.disabled",
      "%prefix%The server is no longer in lockdown mode."));

    messagesConfig.getYaml().setComment("commands.verbose",
      "Translations for '/sonar verbose'");
    messagesConfig.getYaml().setComment("commands.verbose.subscribed",
      "Message that is shown when a player subscribes to Sonar verbose");
    commands.verboseSubscribed = formatString(messagesConfig.getString("commands.verbose.subscribed",
      "%prefix%You are now viewing Sonar verbose."));

    messagesConfig.getYaml().setComment("commands.verbose.unsubscribed",
      "Message that is shown when a player unsubscribes from Sonar verbose");
    commands.verboseUnsubscribed = formatString(messagesConfig.getString("commands.verbose.unsubscribed",
      "%prefix%You are no longer viewing Sonar verbose."));

    messagesConfig.getYaml().setComment("commands.blacklist",
      "Translations for '/sonar blacklist'");
    messagesConfig.getYaml().setComment("commands.blacklist.empty",
      "Message that is shown when someone tries clearing the blacklist but is is empty");
    commands.blacklistEmpty = formatString(messagesConfig.getString("commands.blacklist.empty",
      "%prefix%The blacklist is currently empty. Therefore, no IP addresses were removed from the blacklist."));

    messagesConfig.getYaml().setComment("commands.blacklist.cleared",
      "Message that is shown when someone clears the blacklist");
    commands.blacklistCleared = formatString(messagesConfig.getString("commands.blacklist.cleared",
      "%prefix%You successfully removed a total of %removed% IP address(es) from the blacklist."));

    messagesConfig.getYaml().setComment("commands.blacklist.size",
      "Message that is shown when someone checks the size of the blacklist");
    commands.blacklistSize = formatString(messagesConfig.getString("commands.blacklist.size",
      "%prefix%The blacklist currently contains %amount% IP address(es)."));

    messagesConfig.getYaml().setComment("commands.blacklist.added",
      "Message that is shown when someone adds an IP address to the blacklist");
    commands.blacklistAdd = formatString(messagesConfig.getString("commands.blacklist.added",
      "%prefix%Successfully added %ip% to the blacklist."));

    messagesConfig.getYaml().setComment("commands.blacklist.added-warning",
      "Message that is shown when someone adds an IP address to the blacklist that is verified");
    commands.blacklistAddWarning = formatString(messagesConfig.getString("commands.blacklist.added-warning",
      "%prefix%<red>Warning: <white>%ip% is currently whitelisted. " +
        "Consider removing the IP address from the list of verified players to avoid potential issues."));

    messagesConfig.getYaml().setComment("commands.blacklist.removed",
      "Message that is shown when someone removes an IP address from the blacklist");
    commands.blacklistRemove = formatString(messagesConfig.getString("commands.blacklist.removed",
      "%prefix%Successfully removed %ip% from the blacklist."));

    messagesConfig.getYaml().setComment("commands.blacklist.duplicate-ip",
      "Message that is shown when someone adds an IP address to the blacklist but it is already blacklisted");
    commands.blacklistDuplicate = formatString(messagesConfig.getString("commands.blacklist.duplicate-ip",
      "%prefix%The IP address you provided is already blacklisted."));

    messagesConfig.getYaml().setComment("commands.blacklist.ip-not-found",
      "Message that is shown when someone removes an IP address from the blacklist but it is not blacklisted");
    commands.blacklistNotFound = formatString(messagesConfig.getString("commands.blacklist.ip-not-found",
      "%prefix%The IP address you provided is not blacklisted."));

    messagesConfig.getYaml().setComment("commands.verified",
      "Translations for '/sonar verified'");
    messagesConfig.getYaml().setComment("commands.verified.empty",
      "Message that is shown when someone tries clearing the list of verified players but is is empty");
    commands.verifiedEmpty = formatString(messagesConfig.getString("commands.verified.empty",
      "%prefix%The list of verified players is currently empty. Therefore, no players were unverified."));

    messagesConfig.getYaml().setComment("commands.verified.cleared",
      "Message that is shown when someone clears the list of verified players");
    commands.verifiedCleared = formatString(messagesConfig.getString("commands.verified.cleared",
      "%prefix%You successfully unverified a total of %removed% unique player(s)."));

    messagesConfig.getYaml().setComment("commands.verified.size",
      "Message that is shown when someone checks the size of the list of verified players");
    commands.verifiedSize = formatString(messagesConfig.getString("commands.verified.size",
      "%prefix%There are currently %amount% unique player(s) verified."));

    messagesConfig.getYaml().setComment("commands.verified.removed",
      "Message that is shown when someone un-verifies an IP address");
    commands.verifiedRemove = formatString(messagesConfig.getString("commands.verified.removed",
      "%prefix%Successfully unverified %ip%."));

    messagesConfig.getYaml().setComment("commands.verified.ip-not-found",
      "Message that is shown when someone un-verifies an IP address but it is not verified");
    commands.verifiedNotFound = formatString(messagesConfig.getString("commands.verified.ip-not-found",
      "%prefix%The IP address you provided is not verified."));

    messagesConfig.getYaml().setComment("commands.verified.blocked",
      "Message that is shown when someone tries un-verifying the same IP address twice (double operation)");
    commands.verifiedBlocked = formatString(messagesConfig.getString("commands.verified.blocked",
      "%prefix%Please wait for the current operation to finish."));

    messagesConfig.getYaml().setComment("commands.statistics",
      "Translations for '/sonar statistics'");
    messagesConfig.getYaml().setComment("commands.statistics.header",
      "Informational message that is shown above everything when viewing the statistics");
    commands.statisticsHeader = formatString(messagesConfig.getString("commands.statistics.header",
      "%prefix%<yellow>Showing %type% statistics for this session:"));

    messagesConfig.getYaml().setComment("commands.statistics.unknown-type",
      "Message that is shown when a player tries viewing an unknown statistic");
    commands.unknownStatisticType = formatString(messagesConfig.getString("commands.statistics.unknown-type",
      "%prefix%<red>Unknown statistics type! Available statistics: <gray>%statistics%"));

    messagesConfig.getYaml().setComment("commands.statistics.general",
      "Format of the general statistics message");
    commands.generalStatistics = formatString(fromList(messagesConfig.getStringList("commands.statistics.general",
      Arrays.asList(
        " <gray>▪ <green>Verified IP addresses: <white>%verified%",
        " <gray>▪ <green>Verifying IP addresses: <white>%verifying%",
        " <gray>▪ <green>Blacklisted IP addresses: <white>%blacklisted%",
        " <gray>▪ <green>Currently queued logins: <white>%queued%",
        " <gray>▪ <green>Total non-unique joins: <white>%total_joins%",
        " <gray>▪ <green>Total verification attempts: <white>%total_attempts%",
        " <gray>▪ <green>Total failed verifications: <white>%total_failed%"
      ))));

    messagesConfig.getYaml().setComment("commands.statistics.cpu",
      "Format of the CPU statistics message");
    commands.cpuStatistics = formatString(fromList(messagesConfig.getStringList("commands.statistics.cpu",
      Arrays.asList(
        " <gray>▪ <green>Process CPU usage right now: <white>%process_cpu%%",
        " <gray>▪ <green>System CPU usage right now: <white>%system_cpu%%",
        " <gray>▪ <green>Per-core process CPU usage: <white>%average_process_cpu%%",
        " <gray>▪ <green>Per-core system CPU usage: <white>%average_system_cpu%%",
        " <gray>▪ <green>General system load average: <white>%load_average%%",
        " <gray>▪ <green>Total amount of virtual cpus: <white>%virtual_cores%"
      ))));

    messagesConfig.getYaml().setComment("commands.statistics.memory",
      "Format of the memory statistics message");
    commands.memoryStatistics = formatString(fromList(messagesConfig.getStringList("commands.statistics.memory",
      Arrays.asList(
        " <gray>▪ <green>Total free memory: <white>%free_memory%",
        " <gray>▪ <green>Total used memory: <white>%used_memory%",
        " <gray>▪ <green>Total maximum memory: <white>%max_memory%",
        " <gray>▪ <green>Total allocated memory: <white>%total_memory%"
      ))));

    messagesConfig.getYaml().setComment("commands.statistics.network",
      "Format of the network statistics message");
    commands.networkStatistics = formatString(fromList(messagesConfig.getStringList("commands.statistics.network",
      Arrays.asList(
        " <gray>▪ <green>Current incoming used bandwidth: <white>%incoming%",
        " <gray>▪ <green>Current outgoing used bandwidth: <white>%outgoing%",
        " <gray>▪ <green>Total incoming used bandwidth: <white>%ttl_incoming%",
        " <gray>▪ <green>Total outgoing used bandwidth: <white>%ttl_outgoing%"
      ))));

    messagesConfig.getYaml().setComment("verification",
      "Translations for all messages during the verification process");
    messagesConfig.getYaml().setComment("verification.logs.connection",
      "Message that is logged to console whenever a new player joins the server");
    verification.connectLog = formatString(messagesConfig.getString("verification.logs.connection",
      "%name%%ip% (%protocol%) has connected."));

    messagesConfig.getYaml().setComment("verification.logs",
      "Translations for all debug messages during the verification");
    messagesConfig.getYaml().setComment("verification.logs.failed",
      "Message that is logged to console whenever a player fails verification");
    verification.failedLog = formatString(messagesConfig.getString("verification.logs.failed",
      "%ip% (%protocol%) has failed the bot check for: %reason%"));

    messagesConfig.getYaml().setComment("verification.logs.blacklisted",
      "Message that is logged to console whenever a player is blacklisted");
    verification.blacklistLog = formatString(messagesConfig.getString("verification.logs.blacklisted",
      "%ip% (%protocol%) was blacklisted for too many failed attempts"));

    messagesConfig.getYaml().setComment("verification.logs.successful",
      "Message that is logged to console whenever a player is verified");
    verification.successLog = formatString(messagesConfig.getString("verification.logs.successful",
      "%name% has been verified successfully (%time%s!)."));

    messagesConfig.getYaml().setComment("verification.too-many-players",
      "Disconnect message that is shown when too many players are verifying at the same time");
    verification.tooManyPlayers = deserialize(fromList(messagesConfig.getStringList("verification.too-many-players",
      Arrays.asList(
        "%header%",
        "<gold>Too many players are currently trying to log in, try again later.",
        "<gray>Please wait a few seconds before trying to join again.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.too-fast-reconnect",
      "Disconnect message that is shown when someone rejoins too fast during verification");
    verification.tooFastReconnect = deserialize(fromList(messagesConfig.getStringList("verification.too-fast-reconnect",
      Arrays.asList(
        "%header%",
        "<gold>You reconnected too fast, try again later.",
        "<gray>Please wait a few seconds before trying to verify again.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.already-verifying",
      "Disconnect message that is shown when someone joins but is already verifying");
    verification.alreadyVerifying = deserialize(fromList(messagesConfig.getStringList("verification.already-verifying",
      Arrays.asList(
        "%header%",
        "<red>Your IP address is currently being verified.",
        "<red>Please wait a few seconds before trying to verify again.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.already-queued",
      "Disconnect message that is shown when someone joins but is already queued for verification");
    verification.alreadyQueued = deserialize(fromList(messagesConfig.getStringList("verification.already-queued",
      Arrays.asList(
        "%header%",
        "<red>Your IP address is currently queued for verification.",
        "<red>Please wait a few minutes before trying to verify again.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.blacklisted",
      "Disconnect message that is shown when someone joins but is temporarily blacklisted");
    verification.blacklisted = deserialize(fromList(messagesConfig.getStringList("verification.blacklisted",
      Arrays.asList(
        "%header%",
        "<red>You are currently denied from entering the server.",
        "<red>Please wait a few minutes to be able to join the server again.",
        "<gold>False positive? <gray>%support-url%",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.invalid-username",
      "Disconnect message that is shown when someone joins with an invalid username");
    verification.invalidUsername = deserialize(fromList(messagesConfig.getStringList("verification.invalid-username",
      Arrays.asList(
        "%header%",
        "<red>Your username contains invalid characters.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.invalid-protocol",
      "Disconnect message that is shown when someone joins with a too new or too old version");
    verification.invalidProtocol = deserialize(fromList(messagesConfig.getStringList("verification.invalid-protocol",
      Arrays.asList(
        "%header%",
        "<red>Your protocol version is currently unsupported.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.already-online",
      "Disconnect message that is shown when someone tries verifying with an account that is online");
    verification.alreadyConnected = deserialize(fromList(messagesConfig.getStringList("verification.already-online",
      Arrays.asList(
        "%header%",
        "<red>There is someone already online with your account.",
        "<gray>Please wait a few seconds before trying to verify again.",
        "<gray>If this keeps occurring, try restarting your game or contact support.",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verification.success",
      "Disconnect message that is shown when someone verifies successfully");
    verification.verificationSuccess = deserialize(fromList(messagesConfig.getStringList("verification.success",
      Arrays.asList(
        "%header%",
        "<green>You have successfully passed the verification.",
        "<white>You are now able to play on the server when you reconnect."
      ))));

    messagesConfig.getYaml().setComment("verification.failed",
      "Disconnect message that is shown when someone fails verification");
    verification.verificationFailed = deserialize(fromList(messagesConfig.getStringList("verification.failed",
      Arrays.asList(
        "%header%",
        "<red>You have failed the verification.",
        "<gray>Please wait a few seconds before trying to verify again.",
        "<gold>Need help? <gray>%support-url%",
        "%footer%"
      ))));

    messagesConfig.getYaml().setComment("verbose",
      "Translations for all messages regarding Sonar's verbose output");
    messagesConfig.getYaml().setComment("verbose.layout",
      "General layout for the verbose action-bar" +
        LINE_SEPARATOR + "Placeholders and their descriptions:" +
        LINE_SEPARATOR + "- %queued% Number of queued connections" +
        LINE_SEPARATOR + "- %verifying% Number of verifying connections" +
        LINE_SEPARATOR + "- %blacklisted% Number of blacklisted IP addresses" +
        LINE_SEPARATOR + "- %total-joins% Number of total joins (not unique!)" +
        LINE_SEPARATOR + "- %per-second-joins% Number of joins per second" +
        LINE_SEPARATOR + "- %verify-total% Number of total verification attempts" +
        LINE_SEPARATOR + "- %verify-success% Number of verified IP addresses" +
        LINE_SEPARATOR + "- %verify-failed% Number of failed verifications" +
        LINE_SEPARATOR + "- %incoming-traffic% Incoming bandwidth usage per second" +
        LINE_SEPARATOR + "- %outgoing-traffic% Outgoing bandwidth usage per second" +
        LINE_SEPARATOR + "- %incoming-traffic-ttl% Total incoming bandwidth usage" +
        LINE_SEPARATOR + "- %outgoing-traffic-ttl% Total outgoing bandwidth usage" +
        LINE_SEPARATOR + "- %used-memory% Amount of used memory (JVM process)" +
        LINE_SEPARATOR + "- %total-memory% Amount of total memory (JVM process)" +
        LINE_SEPARATOR + "- %max-memory% Amount of max memory (JVM process)" +
        LINE_SEPARATOR + "- %free-memory% Amount of free memory (JVM process)" +
        LINE_SEPARATOR + "- %animation% Animated spinning circle (by default)"
    );
    verbose.actionBarLayout = formatString(messagesConfig.getString("verbose.layout",
      String.join(" <dark_aqua>╺ ", Arrays.asList(
        "%prefix%<gray>Queued <white>%queued%",
        "<gray>Verifying <white>%verifying%",
        "<gray>Blacklisted <white>%blacklisted%" +
          " <dark_aqua>| <green>⬆ <white>%outgoing-traffic%/s <red>⬇ <white>%incoming-traffic%/s" +
          "  <green><bold>%animation%<reset>"
      ))));
    messagesConfig.getYaml().setComment("verbose.animation",
      "Alternative symbols:"
        + LINE_SEPARATOR + "- ▙"
        + LINE_SEPARATOR + "- ▛"
        + LINE_SEPARATOR + "- ▜"
        + LINE_SEPARATOR + "- ▟");
    verbose.animation = Collections.unmodifiableList(messagesConfig.getStringList("verbose.animation",
      Arrays.asList("◜", "◝", "◞", "◟")
    ));

    generalConfig.save();
    messagesConfig.save();
  }

  private static int clamp(final int v, final int max, final int min) {
    return Math.max(Math.min(v, min), max);
  }

  public String formatAddress(final InetAddress inetAddress) {
    if (logPlayerAddresses) {
      return inetAddress.toString();
    }
    return "/<ip address withheld>";
  }

  private @NotNull String fromList(final @NotNull Collection<String> list) {
    return formatString(String.join("<newline>", list));
  }

  private static @NotNull Component deserialize(final String legacy) {
    return MiniMessage.miniMessage().deserialize(legacy);
  }

  private @NotNull String formatString(final @NotNull String str) {
    return str
      .replace("%prefix%", prefix == null ? "" : prefix)
      .replace("%support-url%", supportUrl == null ? "" : supportUrl)
      .replace("%header%", header == null ? "" : header)
      .replace("%footer%", footer == null ? "" : footer);
  }
}
