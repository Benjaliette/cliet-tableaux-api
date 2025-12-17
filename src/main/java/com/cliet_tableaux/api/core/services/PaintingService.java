package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.mappers.PaintingMapper;
import com.cliet_tableaux.api.core.model.Painting;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaintingService {
    private final PaintingDao paintingDao;
    private final PaintingMapper paintingMapper;

    public PaintingService(final PaintingDao paintingDao, final PaintingMapper paintingMapper) {
        this.paintingDao = paintingDao;
        this.paintingMapper = paintingMapper;
    }

    public List<PaintingDto> findAll() {
        return paintingDao.findAll().stream().map(paintingMapper::toDto).toList();
    }

    public PaintingDto findById(Long id) {
        return paintingDao.findById(id)
                .map(paintingMapper::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Painting not found with id: " + id));
    }

    public PaintingDto save(PaintingDto paintingDto) {
        Painting painting = paintingMapper.toEntity(paintingDto);
        Painting savedPainting = paintingDao.save(painting);
        return paintingMapper.toDto(savedPainting);
    }

    public void deleteById(Long id) {
        if (!paintingDao.existsById(id)) {
            throw new ResourceNotFoundException("Painting not found with id: " + id);
        }
        paintingDao.deleteById(id);
    }
}
