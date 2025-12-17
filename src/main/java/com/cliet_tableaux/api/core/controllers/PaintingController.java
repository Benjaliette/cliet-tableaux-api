package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.services.PaintingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/paintings")
public class PaintingController {
    private final PaintingService paintingService;

    public PaintingController(PaintingService paintingService) {
        this.paintingService = paintingService;
    }

    @GetMapping
    public ResponseEntity<List<PaintingDto>> getAllPaintings() {
        return ResponseEntity.ok(paintingService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaintingDto> getPaintingById(@PathVariable Long id) {
        return ResponseEntity.ok(paintingService.findById(id));
    }

    @PostMapping
    public ResponseEntity<PaintingDto> createPainting(@RequestBody PaintingDto paintingDto) {
        PaintingDto savedPainting = paintingService.save(paintingDto);
        return new ResponseEntity<>(savedPainting, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PaintingDto> updatePainting(@PathVariable Long id, @RequestBody PaintingDto paintingDto) {
        paintingService.findById(id);
        PaintingDto updatedPainting = paintingService.save(paintingDto);
        return ResponseEntity.ok(updatedPainting);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePainting(@PathVariable Long id) {
        paintingService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
