package dev.scanplus.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class AppModels {
    private AppModels() {}
    public static final String PACKAGE="(?:android|[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+)";
    public enum Action { REFRESH, LAUNCH, FORCE_STOP, SUSPEND, RESUME, HIDE, SHOW, BLOCK_UNINSTALL, ALLOW_UNINSTALL, GRANT_PERMISSION, DENY_PERMISSION, RESET_PERMISSION, INSTALL, UNINSTALL }
    public record Permission(@NotBlank @Size(max=200) String name, boolean granted, @Min(0) @Max(2) int policy,
        boolean operationBlocked, @Size(max=24) String enforcement) {
        public Permission(String name,boolean granted,int policy){this(name,granted,policy,false,"ANDROID");}
    }
    public record AppInfo(@NotBlank @Pattern(regexp=PACKAGE) @Size(max=180) String packageName,
        @NotBlank @Size(max=160) String label, @Size(max=100) String versionName, @Min(0) long versionCode,
        boolean systemApp, boolean critical, boolean enabled, boolean hidden, boolean suspended,
        boolean uninstallBlocked, boolean launchable, boolean stopped, @Min(0) long firstInstallTime,
        @Min(0) long lastUpdateTime, @NotNull @Size(max=128) List<@Valid Permission> permissions) {}
    public record Inventory(@NotNull @Size(max=1000) List<@Valid AppInfo> apps, boolean canForceStop) {}
    public record Request(@NotNull UUID requestId, @NotNull Action action,
        @Pattern(regexp=PACKAGE) @Size(max=180) String packageName,
        @Pattern(regexp="android\\.permission\\.[A-Z0-9_]+") @Size(max=200) String permission,
        UUID artifactId, @Min(15) @Max(600) Integer ttlSeconds) {}
    public record Command(UUID id, Action action, String packageName, String permission, UUID artifactId,
        String sha256, Long sizeBytes, Instant expiresAt) {}
    public record Ack(boolean success, @NotBlank @Size(max=2000) String detail, @Valid AppInfo observedApp) {}
}
