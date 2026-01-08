package com.shop.ecommerceengine.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ecommerceengine.common.dto.ApiError;
import com.shop.ecommerceengine.common.dto.ApiResponse;
import com.shop.ecommerceengine.common.exception.BaseCustomException;
import com.shop.ecommerceengine.common.exception.GlobalExceptionHandler;
import com.shop.ecommerceengine.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("Should return ApiResponse with ApiError when BaseCustomException is thrown")
    void shouldHandleBaseCustomException() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/base-exception"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);

        assertThat(response.isStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Base custom exception occurred");
        assertThat(response.getData()).isNotNull();
    }

    @Test
    @DisplayName("Should return ApiResponse with ApiError when ResourceNotFoundException is thrown")
    void shouldHandleResourceNotFoundException() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);

        assertThat(response.isStatus()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Resource not found: Product with id 123");
    }

    @Test
    @DisplayName("Should return ApiResponse with ApiError for generic exceptions")
    void shouldHandleGenericException() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/generic-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ApiResponse<?> response = objectMapper.readValue(responseBody, ApiResponse.class);

        assertThat(response.isStatus()).isFalse();
        assertThat(response.getMessage()).contains("Internal server error");
    }

    @Test
    @DisplayName("Should include error code in ApiError response")
    void shouldIncludeErrorCodeInResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Verify the response contains expected error code
        assertThat(responseBody).contains("404");
    }

    @Test
    @DisplayName("Should include timestamp in ApiError response")
    void shouldIncludeTimestampInResponse() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/base-exception"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Verify the response contains a timestamp field
        assertThat(responseBody).contains("timestamp");
    }

    // Test controller for triggering exceptions
    @RestController
    static class TestController {

        @GetMapping("/test/base-exception")
        public String triggerBaseException() {
            throw new BaseCustomException("Base custom exception occurred", HttpStatus.BAD_REQUEST);
        }

        @GetMapping("/test/not-found")
        public String triggerNotFoundException() {
            throw new ResourceNotFoundException("Product", "id", "123");
        }

        @GetMapping("/test/generic-exception")
        public String triggerGenericException() {
            throw new RuntimeException("Something went wrong");
        }
    }
}
