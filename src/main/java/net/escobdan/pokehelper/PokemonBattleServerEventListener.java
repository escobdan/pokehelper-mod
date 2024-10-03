package net.escobdan.pokehelper;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedPostEvent;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveSet;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.platform.events.ServerPlayerEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.world.ServerWorld;

//HTTP imports
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

public class PokemonBattleServerEventListener {
    private BattleStartedPostEvent storedBattleEvent;
    private boolean battleStarted = false;
    private boolean firstPokemonSent = false;

    public PokemonBattleServerEventListener() {
        // Subscribe to the battle started event in the constructor
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(Priority.NORMAL, event -> {
//            CompletableFuture.runAsync(() -> {
                storedBattleEvent = event;
                onBattleStartedPost();
//            });
            return null;
        });

        // To check every time a pokemon is swapped
        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.NORMAL, sentPostEvent -> {
//            CompletableFuture.runAsync(() -> {
            if(battleStarted && firstPokemonSent) {
//                CompletableFuture.runAsync(() -> {
                    System.out.println("sent pokemon: " + sentPostEvent.getPokemon().getDisplayName());
                    onBattleStartedPost();
//                });
            }
            else if(battleStarted) {
                firstPokemonSent = true;
            }
//            });
            return null;
        });
        // To reset variables when battle is over
        CobblemonEvents.BATTLE_FLED.subscribe(Priority.LOWEST, battleFaintedEvent -> {
            System.out.println("fled");
            battleStarted = false;
            firstPokemonSent = false;
            storedBattleEvent = null;
            return null;
        });

        CobblemonEvents.BATTLE_VICTORY.subscribe(Priority.LOWEST, battleFaintedEvent -> {
            System.out.println("victory");
            battleStarted = false;
            firstPokemonSent = false;
            storedBattleEvent = null;
            return null;
        });

    }

    private void onBattleStartedPost() {
        battleStarted = true;

        JsonObject dataJson = JsonCreator(storedBattleEvent);
//        System.out.println(dataJson);
//        Gson gson = new Gson();
//        String dataJsonString = gson.toJson(dataJson);
//        System.out.println(dataJsonString);
        CompletableFuture.runAsync(() -> {
            sendHttpPost(dataJson.toString());
        });
    }

    private JsonObject JsonCreator(BattleStartedPostEvent event) {
        JsonObject dataJson = new JsonObject();
        for(BattleActor battleActor : event.getBattle().getActors()) {
            JsonObject actorJson = new JsonObject();
            // Flag to see if actor is player
            // Initialize Pokemon array
            JsonArray pokemonArray = new JsonArray();
            actorJson.add("pokemon", pokemonArray);
            System.out.println("current actor pokemon list: " + battleActor.getPokemonList());
            for(BattlePokemon battlePokemon : battleActor.getPokemonList()) {
                // First Pokemon in list will always be the one in battle
                Pokemon pokemon = battlePokemon.getEffectedPokemon();
                JsonObject pokemonJson = new JsonObject();
                pokemonJson.addProperty("pokemonName", pokemon.getDisplayName().getString());
                pokemonJson.addProperty("pokedexNumber", pokemon.getSpecies().getNationalPokedexNumber());
                pokemonJson.addProperty("primaryType", pokemon.getPrimaryType().getName());
                ElementalType secondaryType = pokemon.getSecondaryType();
                if(secondaryType != null) {
                    pokemonJson.addProperty("secondaryType", secondaryType.getName());
                }
                pokemonJson.addProperty("hp", pokemon.getHp());
                pokemonJson.addProperty("currentHealth", pokemon.getCurrentHealth());
                pokemonJson.addProperty("lvl", pokemon.getLevel());
                pokemonJson.addProperty("attack", pokemon.getAttack());
                pokemonJson.addProperty("defence", pokemon.getDefence());
                pokemonJson.addProperty("specialAttack", pokemon.getSpecialAttack());
                pokemonJson.addProperty("specialDefence", pokemon.getSpecialDefence());
                pokemonJson.addProperty("speed", pokemon.getSpeed());
                JsonArray pokemonMoves = new JsonArray();
                MoveSet moveSet = pokemon.getMoveSet();
                for(Move move : moveSet.getMoves()) {
                    JsonObject moveJson = new JsonObject();
                    moveJson.addProperty("moveName", move.getName());
                    moveJson.addProperty("moveDesc", move.getDescription().getString());
                    moveJson.addProperty("moveType", move.getType().getName());
                    moveJson.addProperty("movePower", move.getPower());
                    moveJson.addProperty("moveCategory", move.getDamageCategory().getName());
                    moveJson.addProperty("moveAccuracy", move.getAccuracy());
                    moveJson.addProperty("movePp", move.getMaxPp());
                    pokemonMoves.add(moveJson);
                }

                pokemonJson.add("moves", pokemonMoves);
                pokemonArray.add(pokemonJson);
            }
//            dataJson.add(battleActor.getUuid().toString(), actorJson);
            dataJson.add(battleActor.getName().getString(), actorJson);

        }

        return dataJson;
    }

    private void sendHttpPost(String json) {
        System.out.println("Setting up connection and sending request");
        String urlString = "https://escobdan1.pythonanywhere.com/battle-update";
//        String urlString = "http://127.0.0.1:5000/battle-update";
//        String urlString = "\thttps://webhook.site/ea4b5b11-197e-4304-afda-9e78b9729fe6";
        System.out.println("sending to webhook");
        try {
            // Set URL and connection
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // Set request method and properties
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            // Send JSON
            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), "UTF-8")) {
                writer.write(json);
                writer.flush();
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
        } catch (Exception e) {
            e.printStackTrace();
        }
//            try (DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream())) {
//                dataOutputStream.writeBytes(json);
//                dataOutputStream.flush();
//            } catch (Exception e) {
//                e.printStackTrace();
//                throw new RuntimeException(e);
//            }
        // Get response
//            int responseCode = connection.getResponseCode();
//            System.out.println("Response Code: " + responseCode);
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException(e);
//        }

    }
}