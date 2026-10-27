package com.neueda.leap.compliance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.neueda.leap.config.SecurityConfig;
import com.neueda.leap.controller.ComplianceController;
import com.neueda.leap.domain.AppUser;
import com.neueda.leap.exception.GlobalExceptionHandler;
import com.neueda.leap.mapper.*;
import com.neueda.leap.service.*;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.*;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.ByteArrayInputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.ZipInputStream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

@SpringBootTest(
        classes = ComplianceHttpJourneyTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "compliance.access-enabled=true",
            "compliance.allowed-purposes=ORDER_REVIEW",
            "compliance.policy-version=w1",
            "compliance.context-seconds=3600",
            "compliance.export-seconds=3600",
            "jwt.secret=w1-test-secret-only-12345678901234567890"
        })
class ComplianceHttpJourneyTest extends ComplianceDatabaseFixture {
    @Configuration
    @EnableAutoConfiguration(
            exclude = {DataSourceAutoConfiguration.class, MybatisAutoConfiguration.class})
    @EnableTransactionManagement
    @Import({
        SecurityConfig.class,
        ComplianceController.class,
        GlobalExceptionHandler.class,
        ComplianceAccessService.class,
        ComplianceEvidenceService.class,
        ComplianceExportService.class,
        ComplianceAuditService.class
    })
    static class Application {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        @Bean
        DataSource dataSource() {
            return FACTORY.getConfiguration().getEnvironment().getDataSource();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory() {
            return FACTORY;
        }

        @Bean
        SqlSessionTemplate session() {
            return new SqlSessionTemplate(FACTORY);
        }

        @Bean
        DataSourceTransactionManager transactions(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        ComplianceAccessMapper accessMapper(SqlSessionTemplate session) {
            return session.getMapper(ComplianceAccessMapper.class);
        }

        @Bean
        ComplianceEvidenceMapper evidenceMapper(SqlSessionTemplate session) {
            return session.getMapper(ComplianceEvidenceMapper.class);
        }

        @Bean
        ComplianceExportMapper exportMapper(SqlSessionTemplate session) {
            return session.getMapper(ComplianceExportMapper.class);
        }

        @Bean
        AppUserService users() {
            AppUserService users = mock(AppUserService.class);
            AppUser actor = new AppUser();
            actor.setUserId(1);
            actor.setUsername("compliance.actor");
            actor.setEnabled(true);
            actor.setRoles(List.of("COMPLIANCE"));
            when(users.getUser(1)).thenReturn(actor);
            when(users.getUserByUsername("compliance.actor")).thenReturn(actor);
            return users;
        }

        @Bean
        SessionService sessions() {
            SessionService sessions = mock(SessionService.class);
            when(sessions.valid(any())).thenReturn(true);
            return sessions;
        }
    }

    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void realHttpSearchDetailPaginationExportRecoveryReceiptAndExactArchive() throws Exception {
        HttpResponse<byte[]> created =
                call(
                        "POST",
                        "/access-contexts",
                        null,
                        null,
                        "{\"purposeCode\":\"ORDER_REVIEW\",\"caseReference\":\"CASE-W1-12345678\"}");
        assertEquals(201, created.statusCode(), text(created));
        String context = tree(created).path("contextId").asText();
        for (String state :
                List.of(
                        "ACCEPTED",
                        "EXECUTING",
                        "FILLED",
                        "REJECTED",
                        "PENDING",
                        "APPROVED",
                        "EXECUTED")) {
            HttpResponse<byte[]> search =
                    call(
                            "GET",
                            "/investigations?page=0&pageSize=25&status=" + state,
                            context,
                            null,
                            null);
            assertEquals(200, search.statusCode(), text(search));
            assertTrue(tree(search).path("total").asInt() > 0, state);
            for (JsonNode item : tree(search).path("items"))
                assertEquals(state, item.path("status").asText());
        }
        for (String state : List.of("SUBMITTED", "CANCELLED")) {
            assertEquals(
                    400,
                    call(
                                    "GET",
                                    "/investigations?page=0&pageSize=25&status=" + state,
                                    context,
                                    null,
                                    null)
                            .statusCode());
        }
        HttpResponse<byte[]> detail = call("GET", "/orders/1/evidence", context, null, null);
        assertEquals(200, detail.statusCode(), text(detail));
        assertEquals("no-store", detail.headers().firstValue("Cache-Control").orElse(""));
        assertEquals(8, tree(detail).path("sections").size());
        assertFalse(text(detail).contains("private"));
        for (String name : ComplianceEvidenceMapper.SECTIONS) {
            HttpResponse<byte[]> page =
                    call(
                            "GET",
                            "/orders/1/evidence/" + name + "?page=0&pageSize=1",
                            context,
                            null,
                            null);
            assertEquals(200, page.statusCode(), text(page));
            assertEquals(1, tree(page).path("items").size(), name);
            assertEquals(
                    200,
                    call(
                                    "GET",
                                    "/orders/1/evidence/" + name + "?page=1&pageSize=1",
                                    context,
                                    null,
                                    null)
                            .statusCode());
            HttpResponse<byte[]> empty =
                    call("GET", "/orders/2/evidence/" + name, context, null, null);
            assertEquals(200, empty.statusCode(), text(empty));
            assertEquals(0, tree(empty).path("total").asInt(), name);
        }
        assertEquals(404, call("GET", "/orders/3/evidence", context, null, null).statusCode());
        // Treat the first completed POST as a lost response: recovery sends exactly the original
        // key/body.
        HttpResponse<byte[]> first =
                call("POST", "/evidence/exports", context, "original-w1-key", "{\"orderId\":1}");
        assertEquals(201, first.statusCode(), text(first));
        HttpResponse<byte[]> replay =
                call("POST", "/evidence/exports", context, "original-w1-key", "{\"orderId\":1}");
        assertEquals(201, replay.statusCode(), text(replay));
        String reference = tree(replay).path("requestReference").asText();
        assertEquals(tree(first).path("requestReference").asText(), reference);
        HttpResponse<byte[]> receipt =
                call("GET", "/evidence/exports/" + reference, context, null, null);
        assertEquals(200, receipt.statusCode(), text(receipt));
        assertEquals(8, tree(receipt).path("manifest").path("sectionCounts").size());
        assertTrue(tree(receipt).path("authorizedActions").toString().contains("DOWNLOAD"));
        HttpResponse<byte[]> download =
                call("GET", "/evidence/exports/" + reference + "/download", context, null, null);
        assertEquals(200, download.statusCode(), text(download));
        try (var session = FACTORY.openSession(true)) {
            byte[] stored =
                    (byte[])
                            session.getMapper(ComplianceExportMapper.class)
                                    .archive(UUID.fromString(reference))
                                    .get("archive");
            assertArrayEquals(stored, download.body());
        }
        Set<String> entries = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(download.body()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
                entries.add(entry.getName());
        }
        assertEquals(Set.of("evidence.json", "manifest.json", "limitations.txt"), entries);
        HttpResponse<byte[]> conflict =
                call("POST", "/evidence/exports", context, "original-w1-key", "{\"orderId\":2}");
        assertEquals(409, conflict.statusCode());
        assertEquals("IDEMPOTENCY_CONFLICT", tree(conflict).path("code").asText());
        assertEquals(200, call("GET", "/orders/1/evidence", context, null, null).statusCode());

        try (var c = connection();
                var s = c.createStatement()) {
            s.execute(
                    """
INSERT INTO compliance_access_grants(grant_id,staff_user_id,permission,client_id,account_reference,
    valid_from,valid_until,approved_by,approval_reference)
VALUES(md5('read-account-b')::uuid,1,'EVIDENCE_READ',2,'ACCOUNT-B',
    '2026-10-23T08:00Z','2026-10-24T08:00Z',2,'APPROVAL-W1-B-12345678');
INSERT INTO compliance_evidence_exports(
    export_id,requester,context_id,order_id,idempotency_key,request_hash,scope_version,
    snapshot_at,created_at,expires_at,state,digest,manifest,archive)
SELECT md5('historical-export-b')::uuid,requester,context_id,3,'historical-b-key',request_hash,scope_version,
    snapshot_at,created_at,expires_at,state,digest,manifest,archive
FROM compliance_evidence_exports WHERE idempotency_key='original-w1-key';
""");
        }
        HttpResponse<byte[]> revokedScope = call("GET", "/orders/1/evidence", context, null, null);
        assertEquals(403, revokedScope.statusCode());
        assertEquals("CONTEXT_INVALID", tree(revokedScope).path("code").asText());
        String mixedContext =
                tree(call(
                                "POST",
                                "/access-contexts",
                                null,
                                null,
                                "{\"purposeCode\":\"ORDER_REVIEW\",\"caseReference\":\"CASE-W1-MIXED-12345678\"}"))
                        .path("contextId")
                        .asText();
        assertEquals(200, call("GET", "/orders/3/evidence", mixedContext, null, null).statusCode());
        assertEquals(
                404,
                call(
                                "POST",
                                "/evidence/exports",
                                mixedContext,
                                "new-account-b-key",
                                "{\"orderId\":3}")
                        .statusCode());
        assertEquals(
                404,
                call(
                                "POST",
                                "/evidence/exports",
                                mixedContext,
                                "historical-b-key",
                                "{\"orderId\":3}")
                        .statusCode());
        String accountBExport;
        try (var c = connection();
                var s = c.createStatement();
                var row = s.executeQuery("SELECT md5('historical-export-b')::uuid::text")) {
            assertTrue(row.next());
            accountBExport = row.getString(1);
        }
        HttpResponse<byte[]> accountBReceipt =
                call("GET", "/evidence/exports/" + accountBExport, mixedContext, null, null);
        assertEquals(200, accountBReceipt.statusCode(), text(accountBReceipt));
        assertFalse(
                tree(accountBReceipt).path("authorizedActions").toString().contains("DOWNLOAD"));
        assertEquals(
                404,
                call(
                                "GET",
                                "/evidence/exports/" + accountBExport + "/download",
                                mixedContext,
                                null,
                                null)
                        .statusCode());
    }

    private HttpResponse<byte[]> call(
            String method, String path, String context, String key, String body) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(
                                URI.create("http://localhost:" + port + "/api/compliance" + path))
                        .header("Authorization", "Bearer " + token())
                        .header("Content-Type", "application/json");
        if (context != null) request.header("X-Compliance-Context", context);
        if (key != null) request.header("Idempotency-Key", key);
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String token() throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header =
                encoder.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload =
                encoder.encodeToString(
                        json.writeValueAsBytes(
                                Map.of(
                                        "sub",
                                        "compliance.actor",
                                        "userId",
                                        1,
                                        "roles",
                                        List.of("COMPLIANCE"),
                                        "iat",
                                        NOW.minusSeconds(60).getEpochSecond(),
                                        "exp",
                                        NOW.plusSeconds(7200).getEpochSecond())));
        String signed = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(
                new SecretKeySpec(
                        "w1-test-secret-only-12345678901234567890".getBytes(StandardCharsets.UTF_8),
                        "HmacSHA256"));
        return signed
                + "."
                + encoder.encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.US_ASCII)));
    }

    private JsonNode tree(HttpResponse<byte[]> response) throws Exception {
        return json.readTree(response.body());
    }

    private static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
