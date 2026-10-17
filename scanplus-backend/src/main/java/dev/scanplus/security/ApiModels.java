package dev.scanplus.security;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class ApiModels {
    private ApiModels() {}
    public record EnrollmentRequest(@NotBlank @Size(max=80) String label,
                                    @NotBlank @Size(max=80) String expectedSerial) {}
    public record Enrollment(String code, Instant expiresAt) {}
    public record EnrollRequest(@NotBlank @Size(max=100) String code,
                                @NotBlank @Size(max=80) String serial,
                                @NotBlank @Size(max=80) String model,
                                @NotBlank @Size(max=40) String version) {}
    public record Identity(UUID deviceId, String deviceToken) {}
    public record ProtectionRequest(@NotNull UUID requestId, @NotNull Boolean enabled,
                                    @Min(15) @Max(300) Integer ttlSeconds) {}
    public record Status(@NotNull Boolean enabled, @NotNull Boolean ownerActive,
                         @NotNull Boolean usbSealed, @NotNull Boolean bridgeAvailable,
                         @NotBlank @Size(max=2000) String detail) {}
    public record CheckIn(@NotBlank @Size(max=40) String version, @NotNull @jakarta.validation.Valid Status status) {}
    public record Ack(@NotNull Boolean success, @NotNull @jakarta.validation.Valid Status status) {}
    public record Command(UUID id, boolean enabled, Instant expiresAt) {}
    public record CheckInReply(Instant serverTime, Command command, int pollSeconds, AppModels.Command appCommand) {}
}
