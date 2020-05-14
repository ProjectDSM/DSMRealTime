package org.projectdsm.dsmrealtime.dsmrealtime;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.projectdsm.dsmrealtime.dsmrealtime.commands.ToggleCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public final class DSMRealTime extends JavaPlugin {

    private static final long interval = 1200; // Update every minute
    private static final String formattedMessage = ChatColor.GRAY + "[" + ChatColor.RED + "DSMRealTime" + ChatColor.GRAY + "]" + ChatColor.WHITE;
    private static World world;
    private static String timezone, apiKey, location;
    private static boolean isTimeEnabled, isWeatherEnabled;
    private static long weatherOffset = -interval; // Negated to start positive in Scheduler

    /**
     * Check if the config is set up properly and is returning values
     *
     * @return true if all values have been properly set in config.yml, false otherwise
     */
    public static boolean checkTimeValues() {
        boolean success = true;

        /* Check for missing values */
        if (timezone.equals("")) {
            setTimeEnabled(false);
            String message = formattedMessage + " ERROR: Missing config values for Sync Time, disabling. " +
                    "To fix, verify the config.yml has been setup and reload";
            Bukkit.broadcastMessage(message);
            System.out.println(message);
            success = false;
        }

        return success;
    }

    public static boolean checkWeatherValues() {
        boolean success = true;
        if (apiKey.equals("") || apiKey.equalsIgnoreCase("API_KEY") || location.equals("")) {
            setWeatherEnabled(false);
            String message = formattedMessage + " ERROR: Missing config values for Sync Weather, disabling. " +
                    "To fix, verify the config.yml has been setup and reload";
            Bukkit.broadcastMessage(message);
            System.out.println(message);
            success = false;
        }
        return success;
    }

    /**
     * Get the standard formatted message prefix for all plugin chat output
     *
     * @return the formatted message prefix
     */
    public static String getFormattedMessage() {
        return formattedMessage;
    }

    /**
     * Get isTimeEnabled
     *
     * @return isTimeEnabled true or false
     */
    public static String getTimeEnabled() {
        if (isTimeEnabled) {
            return "enabled";
        } else {
            return "disabled";
        }
    }

    /**
     * Set isTimeEnabled to true or false
     *
     * @param enabled - if true, enable the time feature, if false, disable it.
     */
    public static void setTimeEnabled(boolean enabled) {
        isTimeEnabled = enabled;
    }

    /**
     * Get isWeatherEnabled
     *
     * @return isWeatherEnabled true or false
     */
    public static String getWeatherEnabled() {
        if (isWeatherEnabled) {
            return "enabled";
        } else {
            return "disabled";
        }
    }

    /**
     * Set isWeatherEnabled to true or false
     *
     * @param enabled - if true, enable the time feature, if false, disable it.
     */
    public static void setWeatherEnabled(boolean enabled) {
        isWeatherEnabled = enabled;
    }

    /**
     * Converts JSON data from OpenWeatherMap into Java object
     *
     * @param str - the specified JSON string
     * @return the data as a Java Map
     */
    public static Map<String, Object> jsonToMap(String str) {
        return new Gson().fromJson(
                str, new TypeToken<Map<String, Object>>() {
                }.getType()
        );
    }

    /**
     * Set the time of the world
     */
    private static void setTime(String timezone) {
        // Ticks = (Hours * 1000) - 6000
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timezone)); // Get the time instance
        long time = (1000 * cal.get(Calendar.HOUR_OF_DAY)) + (16 * cal.get(Calendar.MINUTE)) - 6000;
        world.setTime(time);
        System.out.println("[DSMRealTime] Time Updated!");
    }

    /**
     * Get Current Weather JSON String from api.openweathermap.org
     *
     * @return a Java Map of the current weather condition data
     * Possible weather condition data include "clear", "rain", "snow", etc.
     */
    private static Map<String, Object> getWeatherData(String location, String apiKey) {
        final String weather = "http://api.openweathermap.org/data/2.5/weather?q=" + location + "&appid=" + apiKey + "&units=imperial";

        /* Get Data from OpenWeatherMap page */
        try {
            StringBuilder result = new StringBuilder();
            URL url = new URL(weather);
            URLConnection connection = url.openConnection();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String line;
            while ((line = rd.readLine()) != null) { // Read in each JSON line
                result.append(line);
            }
            rd.close();
            System.out.println("[DSMRealTime] Received Weather Data...");

            System.out.println(result.toString());

            return jsonToMap(result.toString()); // Convert the String to a map

        } catch (IOException e) {
            System.out.println("[DSMRealTime] ERROR: Weather Data Failed to Update");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Set the weather of the world given updated API weather data
     *
     * @param weatherData - the specified weather data Java Map
     */
    private static void setWeather(Map<String, Object> weatherData) {
        Map<String, Object> weather = jsonToMap(weatherData.get("weather").toString()); // Return the JSON "weather" section
        String data = weather.get("main").toString(); // Get the line "main" from the given "weather" Map

        world.setStorm(!data.contains("clear") || !data.contains("clouds")); // Set weather to clear if the data says clear, storm if not clear
        System.out.println("[DSMRealTime] Weather Updated!");
    }

    private void setConfig() {
        getConfig().set("SyncTime", isTimeEnabled);
        getConfig().set("SyncWeather", isWeatherEnabled);
    }

    @Override
    public void onEnable() {
        /* Setup config.yml */

        getConfig().options().copyDefaults(true);
        saveDefaultConfig();

        /* Get Data from Config */
        world = getServer().getWorld(Objects.requireNonNull(getConfig().getString("World")));
        timezone = getConfig().getString("Timezone");
        apiKey = getConfig().getString("APIKey");
        location = getConfig().getString("Location");

        isTimeEnabled = getConfig().getBoolean("SyncTime");
        isWeatherEnabled = getConfig().getBoolean("SyncWeather");

        System.out.println("[DSMRealTime]: Starting...");

        /* Set Command Executors */
        Objects.requireNonNull(getCommand("synctime")).setExecutor(new ToggleCommand());
        Objects.requireNonNull(getCommand("syncweather")).setExecutor(new ToggleCommand());

        /* Schedule an update time and weather task */
        BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, () -> {
            weatherOffset = -weatherOffset; // Negate every time, run setWeather() when negative

            setConfig(); // Write current enabled/disabled values to config

            if (isTimeEnabled && checkTimeValues()) {
                System.out.println("[DSMRealTime] Updating Time...");
                setTime(timezone);
            }

            if (isWeatherEnabled && checkWeatherValues() && weatherOffset < 0) { // Every two minutes (when set to negative)
                System.out.println("[DSMRealTime] Updating Weather...");
                Map<String, Object> currentWeather = getWeatherData(location, apiKey);
                if (currentWeather != null) {
                    setWeather(currentWeather);
                } else {
                    System.out.println("[DSMRealTime] Weather Data is NULL. Is your API key valid?");
                }
            }
        }, 0L, interval);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        setConfig();
        System.out.println("[DSMRealTime]: Stopping...");
    }
}