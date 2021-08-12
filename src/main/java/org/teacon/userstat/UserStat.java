package org.teacon.userstat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("user_stat")
public final class UserStat {
    
    public static final class Entry {
        public long lastLogin = 0L;
        public long totalPlayTime = 0L;
    }
    
    private static final Logger LOGGER = LogManager.getLogger(UserStat.class);
    private static final Gson GSON = new Gson();
    private static final Type TYPE_TOKEN = new TypeToken<Map<UUID, Entry>>() {
    }.getType();
    
    private static Map<UUID, UserStat.Entry> statData = new HashMap<>();
    private static Map<UUID, Long> onlineUsers = new HashMap<>();
    
    public UserStat() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FMLServerStartingEvent.class, UserStat::onServerStart);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, UserStat::onLogin);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, UserStat::onLogout);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FMLServerStoppingEvent.class, UserStat::onServerStop);
    }
    
    private static void onServerStart(FMLServerStartingEvent event) {
        File f = event.getServer().getFile("user_stat.json");
        if (f.isFile()) {
            try {
                statData.putAll(GSON.fromJson(new FileReader(f), TYPE_TOKEN));
            } catch (JsonSyntaxException syntaxError) {
                // TODO Auto-generated catch block
                LOGGER.warn("Cannot read existing data from {} because it has syntax error.", f.getName());
            } catch (JsonIOException ioError) {
                LOGGER.catching(ioError);
            } catch (FileNotFoundException notFound) {
                // Impossible
                LOGGER.error("Trying to read from a file even after knowing that it does not exist?!", notFound);
            }
        }
        
    }
    
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        long loginTime = System.currentTimeMillis();
        UUID id = event.getPlayer().getGameProfile().getId();
        UserStat.Entry entry = statData.computeIfAbsent(id, uuid -> new UserStat.Entry());
        onlineUsers.put(id, entry.lastLogin = loginTime);
    }
    
    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        long logoutTime = System.currentTimeMillis();
        UUID id = event.getPlayer().getGameProfile().getId();
        UserStat.Entry entry = statData.computeIfAbsent(id, uuid -> new UserStat.Entry());
        Long login = onlineUsers.remove(id);
        if (login != null) {
            entry.totalPlayTime += logoutTime - login;
        }
    }
    
    private static void onServerStop(FMLServerStoppingEvent event) {
        File f = event.getServer().getFile("user_stat.json");
        try {
            Files.write(f.toPath(), GSON.toJson(statData).getBytes(StandardCharsets.UTF_8), 
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("Failed to save UserStat data, data may be corrupted. Backup whenever possible!", e);
        }
    }
}
