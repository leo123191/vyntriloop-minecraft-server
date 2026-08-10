package com.vyntriloop.minecraft.passwordauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.EaglercraftAuthCheckRequiredEvent;
import net.lax1dude.eaglercraft.backend.server.api.bukkit.event.EaglercraftAuthPasswordEvent;
import net.lax1dude.eaglercraft.backend.server.api.event.IEaglercraftAuthCheckRequiredEvent.EnumAuthResponse;
import net.lax1dude.eaglercraft.backend.server.api.event.IEaglercraftAuthCheckRequiredEvent.EnumAuthType;

public final class EaglerPasswordAuthPlugin extends JavaPlugin implements Listener {

    private static final String PASSWORD_ENV = "EAGLER_SERVER_PASSWORD";
    private static final String PASSWORD_PROMPT = "Enter the server password";
    private static final String WRONG_PASSWORD_MESSAGE = "Incorrect server password.";
    private static final String CONFIG_ERROR_MESSAGE = "Server password authentication is unavailable.";

    private byte[] expectedPassword;

    @Override
    public void onEnable() {
        String configuredPassword = System.getenv(PASSWORD_ENV);
        if (configuredPassword == null || configuredPassword.isEmpty()) {
            expectedPassword = null;
            getLogger().severe(PASSWORD_ENV + " is not configured. Eaglercraft logins will be denied.");
        } else {
            expectedPassword = configuredPassword.getBytes(StandardCharsets.UTF_8);
            getLogger().info("Eaglercraft pre-login password authentication is enabled.");
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (expectedPassword != null) {
            for (int i = 0; i < expectedPassword.length; ++i) {
                expectedPassword[i] = 0;
            }
            expectedPassword = null;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthCheck(EaglercraftAuthCheckRequiredEvent event) {
        if (expectedPassword == null) {
            event.kickUser(CONFIG_ERROR_MESSAGE);
            return;
        }

        if (!event.isClientSolicitingPassword()) {
            event.kickUser("This server requires a password-capable Eaglercraft client.");
            return;
        }

        event.setUseAuthType(EnumAuthType.PLAINTEXT);
        event.setAuthMessage(PASSWORD_PROMPT);
        event.setEnableCookieAuth(false);
        event.setAuthRequired(EnumAuthResponse.REQUIRE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAuthPassword(EaglercraftAuthPasswordEvent event) {
        if (expectedPassword == null) {
            event.setLoginDenied(CONFIG_ERROR_MESSAGE);
            return;
        }

        if (event.getAuthType() != EnumAuthType.PLAINTEXT) {
            event.setLoginDenied("Unsupported authentication method.");
            return;
        }

        byte[] providedPassword = event.getAuthPasswordDataResponse();
        if (providedPassword != null && MessageDigest.isEqual(expectedPassword, providedPassword)) {
            event.setLoginAllowed();
        } else {
            event.setLoginDenied(WRONG_PASSWORD_MESSAGE);
        }
    }
}
