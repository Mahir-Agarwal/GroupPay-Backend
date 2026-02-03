package com.grouppay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grouppay.expense.domain.ExpenseType;
import com.grouppay.settlement.domain.Settlement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupPayApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockBean
	private KafkaTemplate<String, Object> kafkaTemplate;

	@MockBean
	private RedisTemplate<String, Object> redisTemplate;

	private String token; // In a real test we would simulate login to get token, 
                          // but for simplicity we rely on DB state or mock Auth if needed.
                          // However, I implemented full Auth, so I should probably register and login.
                          // Or I can mock the JwtUtil/Filter. 
                          // Let's go through the Controller layer which is secure.
                          // Ideally, I should register a user, get the token, and use it.

	@Test
	void contextLoads() {
	}

	@Test
	void fullGroupPayFlow() throws Exception {
		// 1. Register Users
		registerUser("alice@test.com", "password");
		registerUser("bob@test.com", "password");
		registerUser("charlie@test.com", "password");

		// 2. Login to get Token (as Alice)
		String aliceToken = loginUser("alice@test.com", "password");
		String bobToken = loginUser("bob@test.com", "password");
		String charlieToken = loginUser("charlie@test.com", "password");
        
        // We will simple fetch IDs from the response or assume IDs 1, 2, 3 as it's H2 fresh DB.
        Long aliceId = 1L;
        Long bobId = 2L;
        Long charlieId = 3L;

		// 3. Create Group (Alice creates)
		MvcResult groupResult = mockMvc.perform(post("/groups")
				.header("Authorization", "Bearer " + aliceToken)
				.param("name", "Trip")
				.param("description", "Vegas")
				.param("creatorId", aliceId.toString()))
				.andExpect(status().isOk())
				.andReturn();
        
        String groupJson = groupResult.getResponse().getContentAsString();
        // Extract ID. Simple string parsing for demo
        Long groupId = Long.parseLong(objectMapper.readTree(groupJson).get("id").toString());

		// 4. Add Members
		mockMvc.perform(post("/groups/" + groupId + "/members")
				.header("Authorization", "Bearer " + aliceToken)
				.param("userId", bobId.toString()))
				.andExpect(status().isOk());

		mockMvc.perform(post("/groups/" + groupId + "/members")
				.header("Authorization", "Bearer " + aliceToken)
				.param("userId", charlieId.toString()))
				.andExpect(status().isOk());

		// 5. Add Expense
		// Alice pays 300, Split Equal (Alice: 100, Bob: 100, Charlie: 100)
        // Net: Alice (+200), Bob (-100), Charlie (-100)
		mockMvc.perform(post("/expenses")
				.header("Authorization", "Bearer " + aliceToken)
				.param("userId", aliceId.toString())
				.param("groupId", groupId.toString())
				.param("description", "Dinner")
				.param("amount", "300.00")
				.param("type", "EQUAL"))
				.andExpect(status().isOk());

		// 6. Calculate Settlement
		MvcResult settlementResult = mockMvc.perform(get("/settlements/" + groupId)
				.header("Authorization", "Bearer " + aliceToken))
				.andExpect(status().isOk())
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
				.andReturn();

		String settlementJson = settlementResult.getResponse().getContentAsString();
		System.err.println("Settlement Result JSON: " + settlementJson);
        
        // Assertions
        assertFalse(settlementJson.isEmpty(), "Settlement response should not be empty");
        // We expect at least one transaction of 100.00
        assertTrue(settlementJson.contains("100"), "Response should contain amount '100' or '100.00'. Actual: " + settlementJson);
        assertTrue(settlementJson.contains("payer"), "Response should contain 'payer'. Actual: " + settlementJson);
	}

	private void registerUser(String email, String password) throws Exception {
		mockMvc.perform(post("/auth/register")
				.param("email", email)
				.param("password", password))
				.andExpect(status().isOk());
	}

	private String loginUser(String email, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
				.param("email", email)
				.param("password", password))
				.andExpect(status().isOk())
				.andReturn();
		// Response is basic string token in my implementation: "jwt-token" or similar, 
        // wait the LoginUserService returns just the string token.
		return result.getResponse().getContentAsString();
	}
}
