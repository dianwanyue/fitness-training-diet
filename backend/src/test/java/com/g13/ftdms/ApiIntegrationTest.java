package com.g13.ftdms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g13.ftdms.repository.BodyRecordRepository;
import com.g13.ftdms.repository.DietRecordRepository;
import com.g13.ftdms.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real controllers, JWT filters, services and committed H2 transactions; no mocked repository or service. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "logging.level.com.g13.ftdms=INFO"
})
@ActiveProfiles("h2")
@AutoConfigureMockMvc
class ApiIntegrationTest {
    private static final String API = "/api/v1";
    private static final String PASSWORD = "Testpass123";
    private static final DateTimeFormatter DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Clock clock;
    @Autowired BodyRecordRepository bodyRepository;
    @Autowired DietRecordRepository dietRepository;
    @Autowired UserAccountRepository userRepository;
    String username;
    String token;
    long userId;
    String today;

    @BeforeEach
    void registerAndLogin() throws Exception {
        username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        today = LocalDate.now(clock).toString();
        JsonNode created = ok(post(API + "/auth/register"), null,
                Map.of("username", username, "password", PASSWORD));
        userId = created.path("id").asLong();
        token = login(username, PASSWORD);
    }

    @Test
    void registrationLoginAndEnvelope() throws Exception {
        JsonNode profile = ok(get(API + "/users/me"), token, null);
        assertEquals(userId, profile.path("id").asLong());
        assertEquals("USER", profile.path("role").asText());
        assertFalse(profile.has("passwordHash"));
        JsonNode duplicate = response(post(API + "/auth/register"), null,
                Map.of("username", username, "password", PASSWORD));
        assertEquals(1006, duplicate.path("code").asInt());
        assertEquals(2001, response(post(API + "/auth/login"), null,
                Map.of("username", username, "password", "wrongpassword1")).path("code").asInt());
        assertNotEquals(PASSWORD, userRepository.findById(userId).orElseThrow().getPasswordHash());
    }

    @Test
    void rejectsAnonymousTamperedAndLoggedOutTokens() throws Exception {
        assertEquals(1002, response(get(API + "/users/me"), null, null, 401).path("code").asInt());
        assertEquals(1002, response(get(API + "/users/me"), "bad.jwt.token", null, 401).path("code").asInt());
        ok(post(API + "/auth/logout"), token, null);
        assertEquals(1002, response(get(API + "/users/me"), token, null, 401).path("code").asInt());
    }

    @Test
    void ordinaryUserCannotManageCatalog() throws Exception {
        assertEquals(1003, response(post(API + "/admin/exercises"), token,
                Map.of("name", "ShouldNotExist"), 403).path("code").asInt());
    }

    @Test
    void disabledAccountCannotContinueUsingIssuedToken() throws Exception {
        String admin = login("admin", "Admin@12345");
        ok(put(API + "/admin/users/" + userId + "/status"), admin, Map.of("status", "DISABLED"));
        assertEquals(1002, response(get(API + "/users/me"), token, null, 401).path("code").asInt());
        assertEquals(2002, response(post(API + "/auth/login"), null,
                Map.of("username", username, "password", PASSWORD)).path("code").asInt());
    }

    @Test
    void bodyUpsertCommitsAndReloadsWithoutDuplicates() throws Exception {
        JsonNode first = ok(put(API + "/body-records"), token, Map.of("recordDate", today, "weightKg", 70));
        JsonNode second = ok(put(API + "/body-records"), token, Map.of("recordDate", today, "weightKg", 71.25));
        assertEquals(first.path("id"), second.path("id"));
        assertEquals(71.25, bodyRepository.findById(first.path("id").asLong()).orElseThrow().getWeightKg().doubleValue());
        JsonNode list = ok(range(get(API + "/body-records")), token, null);
        assertEquals(1, list.path("total").asInt());
        assertEquals(1, list.path("validDays").asInt());
        assertEquals(71.25, list.path("list").get(0).path("weightKg").asDouble());
        ok(delete(API + "/body-records/" + first.path("id").asLong()), token, null);
        assertEquals(0, ok(range(get(API + "/body-records")), token, null).path("total").asInt());
    }

    @Test
    void goalsHaveEffectiveDateHistoryAndSameDayUpsert() throws Exception {
        String yesterday = LocalDate.now(clock).minusDays(1).toString();
        JsonNode earlier = ok(post(API + "/goals"), token, goal(yesterday, 1900));
        JsonNode current = ok(post(API + "/goals"), token, goal(today, 2100));
        JsonNode updated = ok(post(API + "/goals"), token, goal(today, 2200));
        assertEquals(current.path("id"), updated.path("id"));
        assertEquals(1900, ok(get(API + "/goals/current").param("date", yesterday), token, null).path("kcal").asInt());
        assertEquals(2200, ok(get(API + "/goals/current"), token, null).path("kcal").asInt());
        JsonNode history = ok(get(API + "/goals/history").param("from", yesterday).param("to", today), token, null);
        assertEquals(2, history.size());
        assertEquals(earlier.path("id"), history.get(0).path("id"));
    }

    @Test
    void crossAccountReadsAndWritesAreIsolated() throws Exception {
        long bodyId = ok(put(API + "/body-records"), token, Map.of("recordDate", today, "weightKg", 70)).path("id").asLong();
        long planId = ok(post(API + "/plans"), token, plan()).path("id").asLong();
        long dietId = ok(post(API + "/diets"), token, diet(100)).path("id").asLong();
        long workoutId = ok(post(API + "/workouts"), token, workout(planId, performedNow())).path("id").asLong();
        String other = newAccount();
        assertEquals(0, ok(range(get(API + "/plans")), other, null).size());
        assertEquals(0, ok(range(get(API + "/workouts")), other, null).size());
        assertEquals(0, ok(get(API + "/diets").param("date", today), other, null).size());
        assertEquals(0, ok(range(get(API + "/body-records")), other, null).path("total").asInt());
        assertEquals(1004, response(get(API + "/plans/" + planId), other, null).path("code").asInt());
        assertEquals(1004, response(put(API + "/plans/" + planId), other, plan()).path("code").asInt());
        assertEquals(1004, response(post(API + "/plans/" + planId + "/status"), other, Map.of("status", "COMPLETED")).path("code").asInt());
        assertEquals(1004, response(post(API + "/workouts"), other, workout(planId, performedNow())).path("code").asInt());
        assertEquals(1004, response(delete(API + "/workouts/" + workoutId), other, null).path("code").asInt());
        assertEquals(1004, response(put(API + "/diets/" + dietId), other, Map.of("grams", 200)).path("code").asInt());
        assertEquals(1004, response(delete(API + "/diets/" + dietId), other, null).path("code").asInt());
        assertEquals(1004, response(delete(API + "/body-records/" + bodyId), other, null).path("code").asInt());
        assertTrue(bodyRepository.findById(bodyId).isPresent());
        assertTrue(dietRepository.findById(dietId).isPresent());
    }

    @Test
    void planCompletionDoesNotInventWorkoutAndWorkoutDoesNotCompletePlan() throws Exception {
        long planId = ok(post(API + "/plans"), token, plan()).path("id").asLong();
        long workoutId = ok(post(API + "/workouts"), token, workout(planId, performedNow())).path("id").asLong();
        assertEquals("PLANNED", ok(get(API + "/plans/" + planId), token, null).path("status").asText());
        ok(post(API + "/plans/" + planId + "/status"), token, Map.of("status", "COMPLETED"));
        assertEquals(1, ok(range(get(API + "/workouts")), token, null).size());
        assertEquals(1005, response(put(API + "/plans/" + planId), token, plan()).path("code").asInt());
        assertEquals(1005, response(post(API + "/plans/" + planId + "/status"), token,
                Map.of("status", "CANCELLED")).path("code").asInt());
        ok(delete(API + "/workouts/" + workoutId), token, null);
        assertEquals("COMPLETED", ok(get(API + "/plans/" + planId), token, null).path("status").asText());
        assertEquals(0, ok(range(get(API + "/workouts")), token, null).size());
        JsonNode stats = ok(range(get(API + "/stats/workouts")), token, null);
        assertEquals(1, stats.path("completedCount").asInt());
        assertEquals(1.0, stats.path("completionRate").asDouble());
    }

    @Test
    void cancellationIsExcludedFromDenominatorAndTemporaryWorkoutsCountSeparately() throws Exception {
        long planId = ok(post(API + "/plans"), token, plan()).path("id").asLong();
        ok(post(API + "/plans/" + planId + "/status"), token, Map.of("status", "CANCELLED"));
        ok(post(API + "/workouts"), token, workout(null, performedNow()));
        JsonNode stats = ok(range(get(API + "/stats/workouts")), token, null);
        assertEquals(1, stats.path("plannedCount").asInt());
        assertEquals(1, stats.path("cancelledCount").asInt());
        assertTrue(stats.path("completionRate").isNull());
        assertEquals(1, stats.path("extraWorkoutCount").asInt());
    }

    @Test
    void dietEditsPreserveOriginalNutritionSnapshotAfterCatalogChange() throws Exception {
        // Unique food avoids changing shared seed data for other tests.
        String admin = login("admin", "Admin@12345");
        long foodId = ok(post(API + "/admin/foods"), admin, Map.of("name", "SnapshotFood", "state", "COOKED",
                "source", "OriginalSource", "kcalPer100g", 120, "proteinPer100g", 8,
                "carbPer100g", 12, "fatPer100g", 3)).path("id").asLong();
        long id = ok(post(API + "/diets"), token, Map.of("recordDate", today, "meal", "LUNCH", "foodId", foodId, "grams", 150))
                .path("id").asLong();
        ok(put(API + "/admin/foods/" + foodId), admin, Map.of("kcalPer100g", 900, "source", "ChangedSource", "status", "DISABLED"));
        JsonNode changed = ok(put(API + "/diets/" + id), token, Map.of("grams", 200));
        assertEquals(240, changed.path("snapshotKcal").asInt());
        assertEquals(16, changed.path("snapshotProteinG").asInt());
        assertEquals("OriginalSource", dietRepository.findById(id).orElseThrow().getSnapshotSource());
        JsonNode summary = ok(get(API + "/diets/summary").param("date", today), token, null);
        assertEquals(240, summary.path("kcal").asInt());
        assertEquals(1, summary.path("meals").path("LUNCH").size());
        assertEquals(0, summary.path("meals").path("BREAKFAST").size());
    }

    @Test
    void statisticsKeepMissingDaysNullAndUseOnlyEffectiveRecords() throws Exception {
        String yesterday = LocalDate.now(clock).minusDays(1).toString();
        ok(post(API + "/diets"), token, diet(100));
        ok(put(API + "/body-records"), token, Map.of("recordDate", today, "weightKg", 70));
        JsonNode intake = ok(get(API + "/stats/intake").param("from", yesterday).param("to", today), token, null);
        assertEquals(2, intake.path("days").size());
        assertFalse(intake.path("days").get(0).path("recorded").asBoolean());
        assertTrue(intake.path("days").get(0).has("kcal"), "Missing kcal is explicitly null, not an omitted property");
        assertTrue(intake.path("days").get(0).path("kcal").isNull());
        assertEquals(1, intake.path("validDays").asInt());
        assertEquals(intake.path("days").get(1).path("kcal").asDouble(), intake.path("avg").path("kcal").asDouble());
        JsonNode weight = ok(get(API + "/stats/weight").param("from", yesterday).param("to", today), token, null);
        assertEquals(1, weight.path("points").size());
        assertEquals(1, weight.path("validDays").asInt());
        assertEquals(70, weight.path("sevenDayAvg").asInt());
        assertEquals(0, weight.path("change").asInt());
    }

    @Test
    void validatesDatesBoundsAndMaximumInclusiveRange() throws Exception {
        assertEquals(1001, response(get(API + "/plans").param("from", today), token, null).path("code").asInt());
        assertEquals(1001, response(get(API + "/plans").param("from", today).param("to", "2026-02-30"), token, null).path("code").asInt());
        assertEquals(1001, response(get(API + "/stats/intake").param("from", LocalDate.now(clock).minusDays(366).toString()).param("to", today), token, null).path("code").asInt());
        assertEquals(1001, response(put(API + "/body-records"), token,
                Map.of("recordDate", LocalDate.now(clock).plusDays(8).toString(), "weightKg", 70)).path("code").asInt());
        assertEquals(1001, response(post(API + "/workouts"), token,
                workout(null, LocalDateTime.now(clock).plusMinutes(6).format(DATETIME))).path("code").asInt());
        assertEquals(1001, response(post(API + "/workouts"), token,
                workout(null, LocalDateTime.now(clock).minusDays(366).format(DATETIME))).path("code").asInt());
    }

    @Test
    void catalogUsesOneBasedClampedPaginationAndDetailedNutrients() throws Exception {
        JsonNode exercises = ok(get(API + "/exercises").param("page", "0").param("size", "999"), token, null);
        assertEquals(1, exercises.path("page").asInt());
        assertEquals(100, exercises.path("size").asInt());
        assertTrue(exercises.path("total").asLong() > 0);
        JsonNode foods = ok(get(API + "/foods").param("size", "1"), token, null);
        assertEquals(1, foods.path("list").size());
        JsonNode food = ok(get(API + "/foods/" + foods.path("list").get(0).path("id").asLong()), token, null);
        assertTrue(food.has("proteinPer100g"));
        assertTrue(food.has("source"));
    }

    @Test
    void profileCanClearOptionalTextAsDocumented() throws Exception {
        ok(put(API + "/users/me"), token, Map.of("nickname", "TestName", "birthDate", "2000-01-01"));
        ok(put(API + "/users/me"), token, Map.of("nickname", "", "birthDate", ""));
        JsonNode profile = ok(get(API + "/users/me"), token, null);
        assertTrue(profile.path("nickname").isMissingNode() || profile.path("nickname").isNull());
        assertTrue(profile.path("birthDate").isMissingNode() || profile.path("birthDate").isNull());
    }

    @Test
    void unknownApiRouteIsNotAnInternalServerError() throws Exception {
        assertEquals(1004, response(get(API + "/not-a-real-route"), token, null).path("code").asInt());
    }

    private MockHttpServletRequestBuilder range(MockHttpServletRequestBuilder request) {
        return request.param("from", today).param("to", today);
    }

    private Map<String, Object> goal(String date, int kcal) {
        return Map.of("effectiveDate", date, "kcal", kcal, "proteinG", 120, "carbG", 250, "fatG", 60);
    }

    private Map<String, Object> plan() {
        return Map.of("plannedDate", today, "title", "IntegrationPlan", "items", List.of(
                Map.of("exerciseId", 1, "targetSets", 3, "targetReps", 10, "targetWeightKg", 20)));
    }

    private Map<String, Object> diet(int grams) {
        return Map.of("recordDate", today, "meal", "LUNCH", "foodId", 1, "grams", grams);
    }

    private Map<String, Object> workout(Long planId, String performedAt) {
        Map<String, Object> result = new java.util.HashMap<>();
        if (planId != null) result.put("planId", planId);
        result.put("performedAt", performedAt);
        result.put("sets", List.of(Map.of("exerciseId", 1, "setNo", 1, "weightKg", 20, "reps", 10)));
        return result;
    }

    private String performedNow() {
        return LocalDateTime.now(clock).minusSeconds(1).format(DATETIME);
    }

    private String newAccount() throws Exception {
        String name = "v" + UUID.randomUUID().toString().replace("-", "").substring(0, 15);
        ok(post(API + "/auth/register"), null, Map.of("username", name, "password", PASSWORD));
        return login(name, PASSWORD);
    }

    private String login(String name, String password) throws Exception {
        return ok(post(API + "/auth/login"), null, Map.of("username", name, "password", password)).path("token").asText();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, String bearer, Object body) throws Exception {
        JsonNode envelope = response(request, bearer, body);
        assertEquals(0, envelope.path("code").asInt(), envelope.toPrettyString());
        return envelope.path("data");
    }

    private JsonNode response(MockHttpServletRequestBuilder request, String bearer, Object body) throws Exception {
        return response(request, bearer, body, 200);
    }

    private JsonNode response(MockHttpServletRequestBuilder request, String bearer, Object body, int status) throws Exception {
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode envelope = mapper.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(envelope.has("code"));
        assertTrue(envelope.has("data"));
        assertTrue(envelope.path("timestamp").asLong() > 0);
        assertFalse(envelope.path("traceId").asText().isBlank());
        return envelope;
    }
}
