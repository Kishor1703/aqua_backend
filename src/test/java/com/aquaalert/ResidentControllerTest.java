package com.aquaalert;

import com.aquaalert.entity.Area;
import com.aquaalert.entity.Role;
import com.aquaalert.entity.User;
import com.aquaalert.repository.AreaRepository;
import com.aquaalert.repository.ResidentRepository;
import com.aquaalert.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResidentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired AreaRepository areaRepository;
    @Autowired ResidentRepository residentRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    Area area;

    @BeforeEach
    void setUp() {
        residentRepository.deleteAll();
        areaRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(User.builder()
                .fullName("Operator One")
                .email("operator1@aquaalert.test")
                .passwordHash(passwordEncoder.encode("SecurePass123"))
                .role(Role.OPERATOR)
                .active(true)
                .build());

        area = areaRepository.save(Area.builder()
                .name("Gandhi Nagar").ward("Ward 5").zone("North Zone").active(true).build());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_withValidPayload_returns201WithGeneratedCode() throws Exception {
        String body = """
                {"houseNumber":"12A","ownerName":"Ramesh Kumar","phoneNumber":"9876543210",
                 "areaId":%d,"notificationPreference":"SMS"}
                """.formatted(area.getId());

        mockMvc.perform(post("/api/v1/residents").contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.residentCode").value(org.hamcrest.Matchers.startsWith("RES-")))
                .andExpect(jsonPath("$.areaName").value("Gandhi Nagar"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_duplicatePhoneInSameArea_returns409() throws Exception {
        String body = """
                {"houseNumber":"12A","ownerName":"Ramesh Kumar","phoneNumber":"9876543210",
                 "areaId":%d,"notificationPreference":"SMS"}
                """.formatted(area.getId());

        mockMvc.perform(post("/api/v1/residents").contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/residents").contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void create_invalidPhoneNumber_returns400() throws Exception {
        String body = """
                {"houseNumber":"12A","ownerName":"Ramesh Kumar","phoneNumber":"abc",
                 "areaId":%d,"notificationPreference":"SMS"}
                """.formatted(area.getId());

        mockMvc.perform(post("/api/v1/residents").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("phoneNumber"));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void importCsv_withValidAndInvalidRows_reportsPartialSuccess() throws Exception {
        String csv = """
                houseNumber,ownerName,phoneNumber,alternatePhoneNumber,areaName,ward,notificationPreference,preferredLanguage
                14B,Suresh Babu,9876500001,,Gandhi Nagar,Ward 5,SMS,en
                15C,Lakshmi Devi,invalidphone,,Gandhi Nagar,Ward 5,SMS,en
                16D,Priya Raman,9876500003,,Unknown Area,Ward 9,SMS,en
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "residents.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/residents/import").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(2));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void exportCsv_returnsCsvContentType() throws Exception {
        mockMvc.perform(get("/api/v1/residents/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")));
    }

    @Test
    @WithMockUser(username = "operator1@aquaalert.test")
    void search_filtersByArea() throws Exception {
        String body = """
                {"houseNumber":"12A","ownerName":"Ramesh Kumar","phoneNumber":"9876543210",
                 "areaId":%d,"notificationPreference":"SMS"}
                """.formatted(area.getId());
        mockMvc.perform(post("/api/v1/residents").contentType("application/json").content(body));

        mockMvc.perform(get("/api/v1/residents").param("areaId", area.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
