package dev.scanplus.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WifiModels {
    private WifiModels() {}
    public enum ScanState { FRESH, CACHED, WIFI_OFF, PERMISSION_REQUIRED, LOCATION_OFF, FAILED, UNAVAILABLE }
    public enum Security { OPEN, WEP, WPA, WPA2, WPA3, ENTERPRISE, ENHANCED_OPEN, UNKNOWN }
    public enum Action { SCAN, SET_OPEN_POLICY, SET_OPEN_GUARD }
    public enum Enforcement { OFF, LEGACY_LOCKDOWN, NATIVE, DEGRADED }
    public enum GuardEnforcement { OFF, MONITORED, NATIVE, DEGRADED }
    public record OpenGuard(boolean enabled, boolean applied, @NotNull GuardEnforcement enforcement,
                            @NotBlank @Size(max=500) String detail) {
        @com.fasterxml.jackson.annotation.JsonIgnore @AssertTrue public boolean isConsistent() {
            if(!applied) return enforcement==GuardEnforcement.DEGRADED;
            return enabled?enforcement==GuardEnforcement.MONITORED||enforcement==GuardEnforcement.NATIVE:enforcement==GuardEnforcement.OFF;
        }
    }
    public record Policy(boolean blocked, boolean applied, @NotNull Enforcement enforcement,
                         boolean localSettingsLocked, @NotBlank @Size(max=500) String detail) {
        @com.fasterxml.jackson.annotation.JsonIgnore @AssertTrue public boolean isConsistent() {
            if(!applied) return enforcement==Enforcement.DEGRADED;
            return blocked ? enforcement==Enforcement.NATIVE||enforcement==Enforcement.LEGACY_LOCKDOWN&&localSettingsLocked : enforcement==Enforcement.OFF;
        }
    }
    public record Network(@NotNull @Size(max=128) String ssid,
                          @Pattern(regexp="(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}") String bssid,
                          @NotNull Security security, @Min(-127) @Max(0) int rssi,
                          @Min(0) @Max(7500) int frequencyMhz,
                          @Min(0) @Max(86400) long ageSeconds, boolean connected) {}
    public record Connection(@Size(max=128) String ssid,
                             @Pattern(regexp="(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}") String bssid,
                             @NotNull Security security, @Min(-127) @Max(0) int rssi,
                             @Min(0) @Max(7500) int frequencyMhz,
                             @Size(max=45) String ipAddress) {}
    public record Snapshot(@NotNull ScanState scanState, @NotBlank @Size(max=500) String detail,
                           @Min(0) @Max(86400) Long scanAgeSeconds,
                           @Valid Connection connection, @NotNull @Valid Policy policy,
                           @NotNull @Size(max=40) List<@NotNull @Valid Network> networks,
                           @Valid OpenGuard openGuard) {
        public Snapshot(ScanState state,String detail,Long age,Connection connection,Policy policy,List<Network> networks) {
            this(state,detail,age,connection,policy,networks,null);
        }
    }
    public record ScanRequest(@NotNull UUID requestId, @Min(15) @Max(300) Integer ttlSeconds) {}
    public record PolicyRequest(@NotNull UUID requestId, @NotNull Boolean blocked, @Min(15) @Max(300) Integer ttlSeconds) {}
    public record Command(UUID id, Action action, Boolean blocked, Instant expiresAt) {}
    public record Ack(@NotNull Boolean success, @NotNull @Valid Snapshot snapshot) {}
}
