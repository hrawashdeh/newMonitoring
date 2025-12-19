package com.tiqmo.monitoring.loader.api.loader;

import com.tiqmo.monitoring.loader.dto.loader.EtlLoaderDto;
import com.tiqmo.monitoring.loader.service.loader.LoaderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/res/loaders")
@RequiredArgsConstructor
public class LoaderController {

    private final LoaderService service;

    @GetMapping("/loaders")
    public ResponseEntity<Map<String, Object>> getAll() {
        List<EtlLoaderDto> loaders = service.findAll();
        return ResponseEntity.ok(Map.of("loaders", loaders));
    }

    @GetMapping("/{loaderCode}")
    public ResponseEntity<EtlLoaderDto> getByCode(@PathVariable String loaderCode) {
        EtlLoaderDto loader = service.getByCode(loaderCode);
        if (loader == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(loader);
    }

    @PostMapping
    public ResponseEntity<EtlLoaderDto> create(@Valid @RequestBody EtlLoaderDto dto) {
        try {
            EtlLoaderDto created = service.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @PutMapping("/{loaderCode}")
    public ResponseEntity<EtlLoaderDto> update(
            @PathVariable String loaderCode,
            @Valid @RequestBody EtlLoaderDto dto) {

        // Ensure path variable matches request body
        if (!loaderCode.equals(dto.getLoaderCode())) {
            dto.setLoaderCode(loaderCode);
        }

        EtlLoaderDto updated = service.upsert(dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{loaderCode}")
    public ResponseEntity<Void> delete(@PathVariable String loaderCode) {
        try {
            service.deleteByCode(loaderCode);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
