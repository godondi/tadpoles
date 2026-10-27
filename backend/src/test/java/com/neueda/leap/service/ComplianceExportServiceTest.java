package com.neueda.leap.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.dto.ComplianceDtos.*;
import com.neueda.leap.mapper.ComplianceExportMapper;
import com.neueda.leap.service.ComplianceAccessService.Access;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.*;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class ComplianceExportServiceTest {
    @Mock ComplianceAccessService access;
    @Mock ComplianceEvidenceService evidence;
    @Mock ComplianceExportMapper db;

    private final Instant now = Instant.parse("2026-10-22T19:00:00Z");
    private final UUID context = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID exportId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final Access createGrant = new Access(7, context, "EXPORT_CREATE", "scope-v1", now);
    private final Access downloadGrant = new Access(7, context, "EXPORT_DOWNLOAD", "scope-v1", now);
    private ComplianceExportService service;

    @BeforeEach
    void setUp() {
        service =
                new ComplianceExportService(
                        access,
                        evidence,
                        db,
                        new ObjectMapper(),
                        Clock.fixed(now, ZoneOffset.UTC),
                        3600);
        lenient().when(access.scopeLabel()).thenReturn("Assigned accounts only");
    }

    @Test
    void actualArchiveBytesAcceptExactLimitAndRejectTheNextByte() {
        assertDoesNotThrow(() -> ComplianceExportService.requireArchiveLimit(new byte[5_000_000]));
        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () -> ComplianceExportService.requireArchiveLimit(new byte[5_000_001]));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertTrue(failure.getReason().contains("five megabyte"));
    }

    @Test
    void replayRequiresReadAndCreateOnTheSameAccount() {
        when(access.require(context, "EXPORT_CREATE")).thenReturn(createGrant);
        when(db.existing(7, "request-1")).thenReturn(exportId.toString());
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        when(access.has(createGrant, "EXPORT_CREATE", 11)).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(createGrant, exportId.toString());
        assertThrows(
                ResponseStatusException.class,
                () -> service.create(context, "request-1", new ExportRequest(42)));
        verify(db, never()).save(anyMap());
    }

    @Test
    void receiptCannotUseDownloadPermissionInsteadOfReadOnTheExportAccount() {
        Access readGrant = new Access(7, context, "EVIDENCE_READ", "scope-v1", now);
        when(access.require(context, "EVIDENCE_READ")).thenReturn(readGrant);
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        lenient().when(access.has(readGrant, "EXPORT_DOWNLOAD", 11)).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(readGrant, exportId.toString());
        assertThrows(
                ResponseStatusException.class, () -> service.receipt(context, exportId.toString()));
        verify(db, never()).archive(any());
    }

    @Test
    void anotherRequesterCannotReadReceiptEvenWithAllAccountPermissions() {
        Access readGrant = new Access(8, context, "EVIDENCE_READ", "scope-v1", now);
        when(access.require(context, "EVIDENCE_READ")).thenReturn(readGrant);
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(readGrant, exportId.toString());
        assertThrows(
                ResponseStatusException.class, () -> service.receipt(context, exportId.toString()));
        verify(access, never()).has(any(), anyString(), anyInt());
    }

    @Test
    void readOnAccountBDoesNotAuthorizeCreateReplayGrantedOnlyOnAccountA() {
        when(access.require(context, "EXPORT_CREATE")).thenReturn(createGrant);
        when(db.existing(7, "request-1")).thenReturn(exportId.toString());
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        lenient().when(access.has(createGrant, "EVIDENCE_READ", 11)).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(createGrant, exportId.toString());

        assertThrows(
                ResponseStatusException.class,
                () -> service.create(context, "request-1", new ExportRequest(42)));
        verify(db, never()).archive(any());
    }

    @Test
    void readOnAccountBDoesNotAuthorizeDownloadGrantedOnlyOnAccountA() {
        when(access.require(context, "EXPORT_DOWNLOAD")).thenReturn(downloadGrant);
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        lenient().when(access.has(downloadGrant, "EVIDENCE_READ", 11)).thenReturn(true);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(downloadGrant, exportId.toString());

        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.download(context, exportId.toString()));
        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verify(db, never()).archive(any());
    }

    @Test
    void idempotentReplayCannotBypassCurrentScope() {
        when(access.require(context, "EXPORT_CREATE")).thenReturn(createGrant);
        when(db.existing(7, "request-1")).thenReturn(exportId.toString());
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        when(access.has(createGrant, "EXPORT_CREATE", 11)).thenReturn(false);
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Record not found"))
                .when(access)
                .notFound(createGrant, exportId.toString());

        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.create(context, "request-1", new ExportRequest(42)));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verify(evidence, never()).snapshotForExport(any(), anyInt());
        verify(db, never()).save(anyMap());
    }

    @Test
    void idempotencyKeyConflictDoesNotGenerateSecondArchive() {
        when(access.require(context, "EXPORT_CREATE")).thenReturn(createGrant);
        when(db.existing(7, "request-1")).thenReturn(exportId.toString());
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));

        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.create(context, "request-1", new ExportRequest(43)));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(evidence, never()).snapshotForExport(any(), anyInt());
        verify(db, never()).save(anyMap());
    }

    @Test
    void expiredExportCannotBeDownloaded() {
        when(access.require(context, "EXPORT_DOWNLOAD")).thenReturn(downloadGrant);
        Map<String, Object> row = metadata(42, 11);
        row.put("expiresAt", now.minusSeconds(1));
        when(db.metadata(exportId)).thenReturn(row);
        when(access.has(downloadGrant, "EXPORT_DOWNLOAD", 11)).thenReturn(true);

        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.download(context, exportId.toString()));

        assertEquals(HttpStatus.GONE, failure.getStatusCode());
        verify(db, never()).archive(exportId);
    }

    @Test
    void tamperedExportArchiveFailsClosed() {
        when(access.require(context, "EXPORT_DOWNLOAD")).thenReturn(downloadGrant);
        when(db.metadata(exportId)).thenReturn(metadata(42, 11));
        when(access.has(downloadGrant, "EXPORT_DOWNLOAD", 11)).thenReturn(true);
        when(db.archive(exportId)).thenReturn(Map.of("archive", "not-bytes"));

        ResponseStatusException failure =
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.download(context, exportId.toString()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
        verify(access, never())
                .success(any(), anyString(), anyString(), any(), anyString(), anyInt(), any());
    }

    private Map<String, Object> metadata(int orderId, int clientId) {
        Map<String, Object> row = new HashMap<>();
        row.put("requestReference", exportId.toString());
        row.put("requester", 7);
        row.put("orderId", orderId);
        row.put("clientId", clientId);
        row.put("contextId", context);
        row.put("scopeVersion", "scope-v1");
        row.put("asOf", now.minusSeconds(10));
        row.put("createdAt", now.minusSeconds(5));
        row.put("expiresAt", now.plusSeconds(3600));
        row.put("state", "READY");
        row.put("digest", ComplianceAccessService.digest(new byte[] {1, 2, 3}));
        row.put("manifest", null);
        row.put("purposeReference", "CASE-12345678");
        row.put("actor", "compliance.actor");
        return row;
    }
}
