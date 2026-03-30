package com.freightflow.gateway.contract;

import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class used by generated Spring Cloud Contract tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ContractBaseTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setupRestAssured() {
        RestAssuredMockMvc.mockMvc(mockMvc);
    }
}
