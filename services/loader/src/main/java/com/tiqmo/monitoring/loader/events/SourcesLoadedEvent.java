package com.tiqmo.monitoring.loader.events;

import lombok.Getter;

import java.util.Collections;
import java.util.Set;

/** Published after the registry has (re)loaded source DB configs & pools. */
@Getter
public class SourcesLoadedEvent {
    private final Set<String> dbCodes;

    public SourcesLoadedEvent(Set<String> dbCodes) {
        this.dbCodes = (dbCodes == null) ? Collections.emptySet() : Set.copyOf(dbCodes);
    }
}
