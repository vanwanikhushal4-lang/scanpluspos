package dev.scanplus.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class SimModels {
    private SimModels() {}
    public enum ReadState { INITIALIZING, READY, AIRPLANE_MODE, PERMISSION_REQUIRED, UNSUPPORTED, UNAVAILABLE }
    public enum SlotState { PRESENT, ABSENT, LOCKED, NOT_READY, UNKNOWN }
    public enum EventType { SIM_INSERTED, SIM_REMOVED, SIM_REPLACED, MONITORING_GAP }
    public record Slot(@Min(0) @Max(3) int slot,@NotNull SlotState state,
                       @Pattern(regexp="[a-f0-9]{64}") String fingerprint,
                       @Pattern(regexp="[0-9]{4}") String suffix,@NotNull @Size(max=64) String carrier,
                       boolean baselineKnown) {
        @JsonIgnore @AssertTrue public boolean isConsistent() {
            return state==SlotState.PRESENT?(fingerprint==null)==(suffix==null):fingerprint==null&&suffix==null;
        }
    }
    public record Snapshot(@NotNull ReadState readState,@NotBlank @Size(max=500) String detail,
                           boolean baselineEstablished,Instant baselineAt,@Min(0) @Max(128) int outboxDepth,
                           @NotNull @Size(max=4) List<@NotNull @Valid Slot> slots) {
        @JsonIgnore @AssertTrue public boolean isConsistent() {
            return baselineEstablished==(baselineAt!=null)&&slots!=null&&(readState!=ReadState.READY||!slots.isEmpty())&&slots.stream().noneMatch(Objects::isNull)&&slots.stream().map(Slot::slot).distinct().count()==slots.size();
        }
    }
    public record Event(@NotNull UUID id,@NotNull EventType type,@Min(-1) @Max(3) int slot,@NotNull Instant observedAt,
                        @Pattern(regexp="[a-f0-9]{64}") String previousFingerprint,@Pattern(regexp="[a-f0-9]{64}") String currentFingerprint,
                        @Pattern(regexp="[0-9]{4}") String previousSuffix,@Pattern(regexp="[0-9]{4}") String currentSuffix,
                        @NotNull @Size(max=64) String previousCarrier,@NotNull @Size(max=64) String currentCarrier) {
        @JsonIgnore @AssertTrue public boolean isConsistent() {
            if(type==null)return false;
            if((previousFingerprint==null)!=(previousSuffix==null)||(currentFingerprint==null)!=(currentSuffix==null))return false;
            return switch(type) {
                case MONITORING_GAP->slot==-1&&previousFingerprint==null&&currentFingerprint==null;
                case SIM_REMOVED->slot>=0&&previousFingerprint!=null&&currentFingerprint==null;
                case SIM_INSERTED->slot>=0&&currentFingerprint!=null&&(previousFingerprint==null||previousFingerprint.equals(currentFingerprint));
                case SIM_REPLACED->slot>=0&&previousFingerprint!=null&&currentFingerprint!=null&&!previousFingerprint.equals(currentFingerprint);
            };
        }
    }
    public record Report(@NotNull @Valid Snapshot snapshot,@NotNull @Size(max=8) List<@NotNull @Valid Event> events) {
        @JsonIgnore @AssertTrue public boolean isConsistent() { return events!=null&&events.stream().noneMatch(Objects::isNull)&&events.stream().map(Event::id).distinct().count()==events.size(); }
    }
    public record Receipt(List<UUID> acceptedEventIds) {}
}
