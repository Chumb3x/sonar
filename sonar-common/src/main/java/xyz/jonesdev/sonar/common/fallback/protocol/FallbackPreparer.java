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

package xyz.jonesdev.sonar.common.fallback.protocol;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.jonesdev.sonar.api.Sonar;
import xyz.jonesdev.sonar.api.fallback.protocol.ProtocolVersion;
import xyz.jonesdev.sonar.common.fallback.protocol.block.BlockPosition;
import xyz.jonesdev.sonar.common.fallback.protocol.block.BlockType;
import xyz.jonesdev.sonar.common.fallback.protocol.block.ChangedBlock;
import xyz.jonesdev.sonar.common.fallback.protocol.dimension.DimensionInfo;
import xyz.jonesdev.sonar.common.fallback.protocol.packets.config.FinishConfiguration;
import xyz.jonesdev.sonar.common.fallback.protocol.packets.config.RegistrySync;
import xyz.jonesdev.sonar.common.fallback.protocol.packets.play.Abilities;
import xyz.jonesdev.sonar.common.fallback.protocol.packets.play.EmptyChunkData;
import xyz.jonesdev.sonar.common.fallback.protocol.packets.play.JoinGame;
import xyz.jonesdev.sonar.common.fallback.protocol.packets.play.UpdateSectionBlocks;

import java.io.InputStream;
import java.util.Collections;
import java.util.Objects;

import static xyz.jonesdev.sonar.api.fallback.protocol.ProtocolVersion.*;

@UtilityClass
public class FallbackPreparer {

  // Mappings
  private final CompoundBinaryTag DAMAGE_TYPE_1194;
  private final CompoundBinaryTag DAMAGE_TYPE_120;

  static {
    DAMAGE_TYPE_1194 = getMapping("damage_type_1194.nbt");
    DAMAGE_TYPE_120 = getMapping("damage_type_120.nbt");
  }

  // Abilities
  public final FallbackPacket DEFAULT_ABILITIES = new Abilities((byte) 0, 0f, 0f);
  // Chunks
  public final FallbackPacket EMPTY_CHUNK_DATA = new EmptyChunkData(0, 0);
  // Finish Configuration
  public final FallbackPacket FINISH_CONFIGURATION = new FinishConfiguration();
  // Registry Sync
  public FallbackPacket synchronizeRegistry;

  // JoinGame
  private FallbackPacket legacyJoinGame;
  private FallbackPacket joinGame116;
  private FallbackPacket joinGame1162;
  private FallbackPacket joinGame1182;
  private FallbackPacket joinGame1191;
  private FallbackPacket joinGame1194;
  private JoinGame joinGame120;

  // Dimension
  private final DimensionInfo DIMENSION = new DimensionInfo(
    "minecraft:overworld", "sonar", false, false);

  // Collisions
  public final int BLOCKS_PER_ROW = 8; // 8 * 8 = 64 (max)
  private final int SPAWN_BUFFER = 5; // player spawns at 255 + 5 (5 blocks above the platform)
  public final int DEFAULT_Y_COLLIDE_POSITION = 255; // 255 is the maximum Y position allowed

  public UpdateSectionBlocks UPDATE_SECTION_BLOCKS;
  private final ChangedBlock[] CHANGED_BLOCKS = new ChangedBlock[BLOCKS_PER_ROW * BLOCKS_PER_ROW];

  public int maxMovementTick, maxPredictionTick, dynamicSpawnYPosition;
  public double[] preparedCachedYMotions;
  public double maxFallDistance;
  public final int SPAWN_X_POSITION = BLOCKS_PER_ROW; // middle of the chunk
  public final int SPAWN_Z_POSITION = BLOCKS_PER_ROW;

  public void prepare() {
    legacyJoinGame = createJoinGamePacket(MINECRAFT_1_8);
    joinGame116 = createJoinGamePacket(MINECRAFT_1_16);
    joinGame1162 = createJoinGamePacket(MINECRAFT_1_16_2);
    joinGame1182 = createJoinGamePacket(MINECRAFT_1_18_2);
    joinGame1191 = createJoinGamePacket(MINECRAFT_1_19_1);
    joinGame1194 = createJoinGamePacket(MINECRAFT_1_19_4);
    joinGame120 = createJoinGamePacket(MINECRAFT_1_20);
    synchronizeRegistry = new RegistrySync(joinGame120.getRegistry());

    maxMovementTick = Sonar.get().getConfig().getVerification().getMaxMovementTicks();
    maxPredictionTick = maxMovementTick + 10;
    preparedCachedYMotions = new double[maxPredictionTick + 1];

    for (int i = 0; i < maxPredictionTick + 1; i++) {
      preparedCachedYMotions[i] = -((Math.pow(0.98, i) - 1) * 3.92);
    }

    // Adjust block and collide Y position based on max fall distance
    maxFallDistance = 0;
    for (int i = 0; i < maxMovementTick; i++) {
      maxFallDistance += preparedCachedYMotions[i];
    }

    // Set the dynamic spawn buffer
    final int DYNAMIC_SPAWN_BUFFER = (int) (SPAWN_BUFFER + maxFallDistance);
    // Set the dynamic block and collide Y position based on the maximum fall distance
    dynamicSpawnYPosition = DEFAULT_Y_COLLIDE_POSITION + DYNAMIC_SPAWN_BUFFER;

    // Prepare collision platform positions
    int index = 0;
    for (int x = 0; x < BLOCKS_PER_ROW; x++) {
      for (int z = 0; z < BLOCKS_PER_ROW; z++) {
        final BlockPosition position = new BlockPosition(
          x + (BLOCKS_PER_ROW / 2),
          DEFAULT_Y_COLLIDE_POSITION,
          z + (BLOCKS_PER_ROW / 2),
          0, 0
        );
        CHANGED_BLOCKS[index++] = new ChangedBlock(position, BlockType.BARRIER);
      }
    }

    // Prepare UpdateSectionBlocks packet
    UPDATE_SECTION_BLOCKS = new UpdateSectionBlocks(0, 0, CHANGED_BLOCKS);
  }

  private @Nullable CompoundBinaryTag getMapping(final @NotNull String fileName) {
    try (final InputStream inputStream = Sonar.class.getResourceAsStream("/mappings/" + fileName)) {
      return BinaryTagIO.reader().read(Objects.requireNonNull(inputStream), BinaryTagIO.Compression.GZIP);
    } catch (Throwable throwable) {
      Sonar.get().getLogger().error("Could not load mappings for {}: {}", fileName, throwable);
      return null;
    }
  }

  public static FallbackPacket getJoinPacketForVersion(final @NotNull ProtocolVersion protocolVersion) {
    if (protocolVersion.compareTo(MINECRAFT_1_15_2) <= 0) {
      return legacyJoinGame; // 1.7.2-1.15.2
    }
    if (protocolVersion.compareTo(MINECRAFT_1_16_1) <= 0) {
      return joinGame116; // 1.16-1.16.1
    }
    if (protocolVersion.compareTo(MINECRAFT_1_18) <= 0) {
      return joinGame1162; // 1.16.2-1.18
    }
    if (protocolVersion.compareTo(MINECRAFT_1_19) <= 0) {
      return joinGame1182; // 1.18.1-1.19
    }
    if (protocolVersion.compareTo(MINECRAFT_1_19_3) <= 0) {
      return joinGame1191; // 1.19.1-1.19.3
    }
    if (protocolVersion.compareTo(MINECRAFT_1_19_4) == 0) {
      return joinGame1194; // 1.19.4
    }
    return joinGame120; // 1.20-latest
  }

  // Partially taken from
  // https://github.com/Elytrium/LimboAPI/blob/master/plugin/src/main/java/net/elytrium/limboapi/server/LimboImpl.java#L607
  private @NotNull JoinGame createJoinGamePacket(final @NotNull ProtocolVersion protocolVersion) {
    final JoinGame joinGame = new JoinGame();

    joinGame.setLevelType("flat");
    joinGame.setGamemode(Sonar.get().getConfig().getVerification().getGamemodeId());
    joinGame.setDimension(0);
    joinGame.setReducedDebugInfo(true);

    // 1.7.2-1.8.9 don't need any dimension information
    if (protocolVersion.compareTo(MINECRAFT_1_8) <= 0) {
      return joinGame;
    }

    joinGame.setDimensionInfo(DIMENSION);

    final CompoundBinaryTag.Builder registryContainer = CompoundBinaryTag.builder();
    final ListBinaryTag encodedDimensionRegistry = ListBinaryTag.builder(BinaryTagTypes.COMPOUND)
      .add(createDimensionData(protocolVersion))
      .build();

    if (protocolVersion.compareTo(MINECRAFT_1_16_2) >= 0) {
      final CompoundBinaryTag.Builder dimensionRegistryEntry = CompoundBinaryTag.builder();

      dimensionRegistryEntry.putString("type", "minecraft:dimension_type");
      dimensionRegistryEntry.put("value", encodedDimensionRegistry);

      registryContainer.put("minecraft:dimension_type", dimensionRegistryEntry.build());

      final CompoundBinaryTag.Builder effectsTagBuilder = CompoundBinaryTag.builder()
        .putInt("sky_color", 7907327)
        .putInt("fog_color", 12638463)
        .putInt("water_color", 0)
        .putInt("water_fog_color", 0);

      final CompoundBinaryTag.Builder elementTagBuilder = CompoundBinaryTag.builder()
        .putFloat("depth", 0.125f)
        .putFloat("temperature", 0.8f)
        .putFloat("scale", 0.05f)
        .putFloat("downfall", 0.4f)
        .putString("category", "plains")
        .put("effects", effectsTagBuilder.build());

      if (protocolVersion.compareTo(MINECRAFT_1_19_4) >= 0) {
        elementTagBuilder.putBoolean("has_precipitation", false);
      } else {
        elementTagBuilder.putString("precipitation", "rain");
      }

      registryContainer.put("minecraft:worldgen/biome", CompoundBinaryTag.builder()
        .putString("type", "minecraft:worldgen/biome")
        .put("value", ListBinaryTag.from(Collections.singletonList(
          CompoundBinaryTag.builder()
            .putString("name", "minecraft:plains")
            .putInt("id", 1)
            .put("element", elementTagBuilder.build())
            .build()
        ))).build()
      );

      if (protocolVersion.compareTo(MINECRAFT_1_19_4) == 0) {
        registryContainer.put("minecraft:damage_type", DAMAGE_TYPE_1194);
      } else if (protocolVersion.compareTo(MINECRAFT_1_20) >= 0) {
        registryContainer.put("minecraft:damage_type", DAMAGE_TYPE_120);
      }
    } else {
      registryContainer.put("dimension", encodedDimensionRegistry);
    }

    CompoundBinaryTag currentDimensionData = encodedDimensionRegistry.getCompound(0);

    if (protocolVersion.compareTo(MINECRAFT_1_16_2) >= 0) {
      currentDimensionData = currentDimensionData.getCompound("element");
    }

    joinGame.setCurrentDimensionData(currentDimensionData);
    joinGame.setLevelNames(new String[]{"minecraft:overworld"});
    joinGame.setRegistry(registryContainer.build());
    return joinGame;
  }

  public @NotNull CompoundBinaryTag createDimensionData(final @NotNull ProtocolVersion version) {
    final CompoundBinaryTag details = CompoundBinaryTag.builder()
      .putBoolean("natural", false)
      .putFloat("ambient_light", 0f)
      .putBoolean("shrunk", false)
      .putBoolean("ultrawarm", false)
      .putBoolean("has_ceiling", false)
      .putBoolean("has_skylight", true)
      .putBoolean("piglin_safe", false)
      .putBoolean("bed_works", false)
      .putBoolean("respawn_anchor_works", false)
      .putBoolean("has_raids", false)
      .putInt("logical_height", 256)
      .putString("infiniburn", version.compareTo(MINECRAFT_1_18_2) >= 0 ? "#minecraft" +
        ":infiniburn_nether" : "minecraft:infiniburn_nether")
      .putDouble("coordinate_scale", 1.0)
      .putString("effects", "minecraft:overworld")
      .putInt("min_y", 0)
      .putInt("height", 256)
      .putInt("monster_spawn_block_light_limit", 0)
      .putInt("monster_spawn_light_level", 0)
      .build();

    if (version.compareTo(MINECRAFT_1_16_2) >= 0) {
      return CompoundBinaryTag.builder()
        .putString("name", "minecraft:overworld")
        .putInt("id", 0)
        .put("element", details)
        .build();
    }

    return details.putString("name", "minecraft:overworld");
  }
}
