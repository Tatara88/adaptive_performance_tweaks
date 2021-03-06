/**
 * Copyright 2021 Markus Bordihn
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package de.markusbordihn.adaptiveperformancetweaks.player;

import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import de.markusbordihn.adaptiveperformancetweaks.Constants;
import de.markusbordihn.adaptiveperformancetweaks.Manager;

@Mod.EventBusSubscriber
public class PlayerManager extends Manager {
  private static Map<String, ServerPlayerEntity> usernamePlayerMap = new HashMap<>();
  private static Set<ServerPlayerEntity> playerList = new HashSet<>();
  private static Set<PlayerValidation> playerValidationList = new HashSet<>();
  private static boolean hasPlayers = false;
  private static int playerCount = 0;
  private static short ticks = 0;
  private static int validationTimeout = 90;
  private static boolean optimizePlayerLogin = COMMON.optimizePlayerLogin.get();

  @SubscribeEvent
  public static void onServerAboutToStartEvent(FMLServerAboutToStartEvent event) {
    optimizePlayerLogin = COMMON.optimizePlayerLogin.get();
  }

  @SubscribeEvent
  public static void handleServerStartingEvent(FMLServerStartingEvent event) {
    for (ServerPlayerEntity player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
      playerValidationList.add(new PlayerValidation(player));
    }
  }

  @SubscribeEvent
  public static void handlePlayerLoggedInEvent(PlayerEvent.PlayerLoggedInEvent event) {
    String username = event.getPlayer().getName().getString();
    if (!username.isEmpty()) {
      log.debug("Player {} {} logged in.", username, event.getEntity());
      ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByUsername(username);

      // Add protection during login process and give 1 heal
      if (optimizePlayerLogin) {
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.heal(1);
      }
      playerValidationList.add(new PlayerValidation(player));
    }
  }

  @SubscribeEvent
  public static void handlePlayerLoggedOutEvent(PlayerEvent.PlayerLoggedOutEvent event) {
    String username = event.getPlayer().getName().getString();
    if (!username.isEmpty()) {
      log.debug("Player {} logged out.", event.getEntity());
      removePlayer(username);
    }
  }

  @SubscribeEvent
  public static void handleServerTickEvent(TickEvent.ServerTickEvent event) {
    if (event.phase == TickEvent.Phase.END) {
      ticks++;
      return;
    }

    if (ticks == 75) {
      for (PlayerValidation playerValidation : playerValidationList) {
        String username = playerValidation.getUsername();
        boolean foundPlayer = false;
        if (playerValidation.hasPlayerMoved()) {
          log.debug("User {} was successful validated ...", username);
          addPlayer(username);
          foundPlayer = true;
        } else if (playerValidation.getValidationTimeElapsed() / 1000 >= validationTimeout) {
          log.warn("User validation for {} timed out after {} sec", username, validationTimeout);
          addPlayer(username);
          foundPlayer = true;
        }
        if (foundPlayer) {
          break;
        }
      }
      ticks = 0;
    }
  }

  private static void addPlayer(String username) {
    ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByUsername(username);
    usernamePlayerMap.put(username, player);
    try {
      for (PlayerValidation playerValidation : playerValidationList) {
        if (username.equals(playerValidation.getUsername())) {
          log.debug("Found player {} with player validation {}", player, playerValidation);
          // Remove protection from login process
          if (optimizePlayerLogin) {
            if (player.isInvisible()) {
              player.setInvisible(false);
            }
            if (player.isInvulnerable()) {
              player.setInvulnerable(false);
            }
          }
          playerValidationList.remove(playerValidation);
          break;
        }
      }
    } catch (ConcurrentModificationException error) {
      log.error("Unexpected error during adding player. Please report the following error under {} .\n{}", Constants.ISSUE_REPORT, error);
    }
    playerList.add(player);
    playerCount = ServerLifecycleHooks.getCurrentServer().getCurrentPlayerCount();
    hasPlayers = true;
    log.debug("Added {} to PlayerMap: {}", username, usernamePlayerMap);
  }

  private static void removePlayer(String username) {
    usernamePlayerMap.remove(username);
    try {
      for (PlayerValidation playerValidation : playerValidationList) {
        if (username.equals(playerValidation.getUsername())) {
          playerValidationList.remove(playerValidation);
          break;
        }
      }
    } catch (ConcurrentModificationException error) {
      log.error("Unexpected error during removing player. Please report the following error under {} .\n{}", Constants.ISSUE_REPORT, error);
    }
    ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByUsername(username);
    if (playerList.contains(player)) {
      playerList.remove(player);
    }
    playerCount--;
    hasPlayers = playerCount > 0;
    log.debug("Remove {} from PlayerMap: {}", username, usernamePlayerMap);
  }

  public static Set<ServerPlayerEntity> getPlayers() {
    return playerList;
  }

  public static Set<PlayerValidation> getPlayerValidationList() {
    return playerValidationList;
  }

  public static String getUserName(ServerPlayerEntity player) {
    return player.getName().getString();
  }

  public static boolean hasPlayers() {
    return hasPlayers;
  }

  public static int getNumberOfPlayers() {
    return playerCount;
  }

}
