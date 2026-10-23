package com.neueda.leap.compliance;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import java.sql.*;

class CompliancePostgresTest extends ComplianceDatabaseFixture {

    private static final String GRANT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String CONTEXT_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String EXPORT_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc";

    @Test
    void freshSchemaContainsComplianceMigrationAndImmutableGuards() throws Exception {
        assertTrue(Integer.parseInt(scalar("SELECT max(version) FROM schema_migrations")) >= 14);

        sql(
                """
                INSERT INTO compliance_access_grants(
                    grant_id,staff_user_id,permission,client_id,account_reference,
                    valid_from,valid_until,approved_by,approval_reference)
                VALUES (?::uuid,1,'EVIDENCE_READ',1,'TEST-ACCOUNT',now(),now()+interval '1 hour',
                        2,'APPROVAL-12345678')
                """,
                GRANT_ID);
        assertEquals(
                "1",
                scalar(
                        "SELECT count(*) FROM compliance_grant_events WHERE grant_id='"
                                + GRANT_ID
                                + "'"));
        assertSqlFails(
                "UPDATE compliance_access_grants SET account_reference='OTHER' WHERE grant_id='"
                        + GRANT_ID
                        + "'");

        sql(
                """
                INSERT INTO compliance_access_contexts(
                    context_id,actor,purpose_code,case_reference,created_at,expires_at,
                    policy_version,scope_version)
                VALUES (?::uuid,1,'ORDER_REVIEW','CASE-12345678',now(),now()+interval '1 hour',
                        'test-v1',?)
                """,
                CONTEXT_ID,
                "a".repeat(64));
        assertSqlFails(
                "UPDATE compliance_access_contexts SET purpose_code='OTHER' WHERE context_id='"
                        + CONTEXT_ID
                        + "'");

        sql(
                """
                INSERT INTO compliance_evidence_exports(
                    export_id,requester,context_id,order_id,idempotency_key,request_hash,
                    scope_version,snapshot_at,created_at,expires_at,state,digest,manifest,archive)
                VALUES (?::uuid,1,?::uuid,1,'request-1',?, ?,now(),now(),now()+interval '1 hour',
                        'READY',?,'{}'::jsonb,decode('0102','hex'))
                """,
                EXPORT_ID,
                CONTEXT_ID,
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64));
        assertSqlFails(
                "UPDATE compliance_evidence_exports SET state='FAILED' WHERE export_id='"
                        + EXPORT_ID
                        + "'");
    }

    private static String scalar(String query) throws SQLException {
        try (Connection connection = connection();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(query)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void sql(String query, String... values) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            for (int index = 0; index < values.length; index++) {
                statement.setString(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }

    private static void assertSqlFails(String query) throws SQLException {
        assertThrows(SQLException.class, () -> sql(query));
    }
}
