package com.cliet_tableaux.api.core.controllers;

import com.cliet_tableaux.api.core.dtos.PaintingDto;
import com.cliet_tableaux.api.core.services.PaintingService;
import com.cliet_tableaux.api.utils.PaintingTestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaintingControllerTest {
    @InjectMocks
    private PaintingController paintingController;

    private MockMvc mockMvc;

    @Mock
    private PaintingService paintingService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paintingController)
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    void getPaintingById_shouldReturnPainting() throws Exception {
        // GIVEN
        PaintingDto painting = PaintingTestDataFactory.aPaintingDto();
        when(paintingService.findById(1L)).thenReturn(painting);

        // WHEN & THEN
        mockMvc.perform(get("/api/v1/paintings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Le Murmure du Zéphyr sur la Toile Oubliée"));
    }

    @Test
    void createPainting_shouldReturnCreatedPainting() throws Exception {
        // GIVEN
        PaintingDto paintingToCreate = PaintingTestDataFactory.aPaintingDtoToCreate();
        PaintingDto createdPainting = PaintingTestDataFactory.aPaintingDto();
        when(paintingService.save(any(PaintingDto.class))).thenReturn(createdPainting);

        // WHEN & THEN
        mockMvc.perform(post("/api/v1/paintings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paintingToCreate)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.title").value("Le Murmure du Zéphyr sur la Toile Oubliée"));
    }
}

