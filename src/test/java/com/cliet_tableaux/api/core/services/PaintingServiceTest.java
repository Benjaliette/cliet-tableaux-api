package com.cliet_tableaux.api.core.services;

import com.cliet_tableaux.api.core.daos.PaintingDao;
import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.exceptions.ResourceNotFoundException;
import com.cliet_tableaux.api.core.mappers.PaintingMapper;
import com.cliet_tableaux.api.core.model.Painting;
import com.cliet_tableaux.api.utils.PaintingTestDataFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaintingServiceTest {

    @Mock
    private PaintingDao paintingDao;

    @Mock
    private PaintingMapper paintingMapper;

    @InjectMocks
    private PaintingService paintingService;

    @Test
    void findById_whenPaintingExists_shouldReturnPaintingRecord() {
        // GIVEN (Arrange)
        Painting painting = PaintingTestDataFactory.aPaintingEntity();

        when(paintingDao.findAll()).thenReturn(List.of(painting));
        when(paintingMapper.toDto(painting)).thenReturn(PaintingTestDataFactory.aPaintingDto());

        // WHEN (Act)
        List<PaintingDto> result = paintingService.findAll();

        // THEN (Assert)
        assertNotNull(result);
        assertThat(result).as("liste des peintures retournées non vide").isNotEmpty();
        verify(paintingDao).findAll();
        verifyNoMoreInteractions(paintingDao);
    }

    @Test
    void findById_whenPaintingDoesNotExist_shouldThrowResourceNotFoundException() {
        // GIVEN
        when(paintingDao.findById(1L)).thenReturn(Optional.empty());

        // WHEN & THEN
        assertThrows(ResourceNotFoundException.class, () -> {
            paintingService.findById(1L);
        });
        verify(paintingDao).findById(1L);
    }
}

