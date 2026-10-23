package com.neueda.leap.compliance;

import com.neueda.leap.mapper.*;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Own database only: never resets a supplied or retained database. */
abstract class ComplianceDatabaseFixture {
    static final Instant NOW = Instant.parse("2026-10-23T09:00:00Z");
    static final SqlSessionFactory FACTORY;
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;
    private static final PostgreSQLContainer<?> CONTAINER;

    static {
        try {
            String supplied = System.getenv("COMPLIANCE_W1_JDBC_URL");
            String admin;
            if (supplied == null) {
                CONTAINER =
                        new PostgreSQLContainer<>("postgres:15-alpine")
                                .withDatabaseName("compliance_w1_admin");
                CONTAINER.start();
                admin = CONTAINER.getJdbcUrl();
                USER = CONTAINER.getUsername();
                PASSWORD = CONTAINER.getPassword();
            } else {
                CONTAINER = null;
                if (!supplied.matches("jdbc:postgresql://[^/]+/compliance_w1_[A-Za-z0-9_]+"))
                    throw new IllegalArgumentException(
                            "External URL must identify a dedicated compliance_w1_ database");
                admin = supplied;
                USER = Objects.requireNonNull(System.getenv("COMPLIANCE_W1_DB_USER"));
                PASSWORD = Objects.requireNonNull(System.getenv("COMPLIANCE_W1_DB_PASSWORD"));
            }
            String database = "compliance_w1_" + UUID.randomUUID().toString().replace("-", "");
            try (Connection c = DriverManager.getConnection(admin, USER, PASSWORD);
                    Statement s = c.createStatement()) {
                s.execute("CREATE DATABASE " + database);
            }
            URL = admin.substring(0, admin.lastIndexOf('/') + 1) + database;
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        try (Connection c =
                                                        DriverManager.getConnection(
                                                                admin, USER, PASSWORD);
                                                Statement s = c.createStatement()) {
                                            s.execute(
                                                    "DROP DATABASE " + database + " WITH (FORCE)");
                                        } catch (SQLException failure) {
                                            System.err.println(
                                                    "Unable to remove owned Compliance test"
                                                            + " database: "
                                                            + database);
                                        }
                                        if (CONTAINER != null) CONTAINER.stop();
                                    }));
            try (Connection c = connection();
                    Statement s = c.createStatement()) {
                s.execute(Files.readString(Path.of("../database/fresh/001_schema.sql")));
                s.execute(
                        """
INSERT INTO users(user_id,username,password_hash,display_name)
VALUES (1,'compliance.actor','hash','Actor'), (2,'approver','hash','Approver');
INSERT INTO clients(client_id,user_id,client_name)
VALUES (1,1,'Account A'),(2,2,'Account B');
INSERT INTO instruments(instrument_id,instrument_name,ticker,currency)
VALUES(1,'Fixture instrument','TEST','USD');
SET session_replication_role=replica;
INSERT INTO client_trades(
  trade_id,client_id,instrument_id,trade_type,quantity,trade_date,status,record_kind,
  submitted_by_user_id,idempotency_key,request_hash,submitted_at,accepted_at,updated_at,order_type)
SELECT id,CASE WHEN id=3 THEN 2 ELSE 1 END,1,'BUY',1,CURRENT_DATE,
  CASE id WHEN 4 THEN 'ACCEPTED' WHEN 5 THEN 'EXECUTING' WHEN 6 THEN 'REJECTED' ELSE 'FILLED' END,
  'SELF_DIRECTED',1,'fixture-'||id,repeat('a',64),
  '2026-10-23T08:00Z','2026-10-23T08:00Z','2026-10-23T08:00Z','MARKET'
FROM generate_series(1,6) id;
INSERT INTO client_trades(
  trade_id,client_id,instrument_id,trade_type,quantity,price,trade_date,status,record_kind)
SELECT id,1,1,'BUY',1,10,CURRENT_DATE,
  CASE id WHEN 7 THEN 'PENDING' WHEN 8 THEN 'APPROVED' ELSE 'EXECUTED' END,'LEGACY'
FROM generate_series(7,9) id;
INSERT INTO order_events(event_id,order_id,client_id,cursor,sequence,to_state,reason,actor,occurred_at)
SELECT md5('event-'||n)::uuid,1,1,n,n,'FILLED','fixture','engine','2026-10-23T08:01Z'
FROM generate_series(1,5) n;
INSERT INTO order_quote_observations(quote_id,order_id,stage,facts,recorded_at)
SELECT md5('quote-'||n)::uuid,1,'EXECUTION','{"price":"10","private":"redacted"}','2026-10-23T08:01Z'
FROM generate_series(1,5) n;
INSERT INTO order_rule_decisions(decision_id,order_id,stage,policy_version,passed,code,explanation,recorded_at)
SELECT md5('decision-'||n)::uuid,1,'ACCEPTANCE','test',true,'PASS','Fixture','2026-10-23T08:01Z'
FROM generate_series(1,5) n;
INSERT INTO order_decision_contexts(decision_id,schema_version,facts,recorded_at)
SELECT md5('decision-'||n)::uuid,1,'{"schemaVersion":1,"private":"redacted"}','2026-10-23T08:01Z'
FROM generate_series(1,5) n;
INSERT INTO order_execution_attempts(attempt_id,order_id,attempt,outcome,reason,recorded_at)
SELECT md5('attempt-'||n)::uuid,1,1,'RETRY','Fixture','2026-10-23T08:01Z'
FROM generate_series(1,5) n;
INSERT INTO order_reservations(order_id,resource,amount,state,created_at)
VALUES(1,'CASH',10,'ACTIVE','2026-10-23T08:01Z');
INSERT INTO order_fills(fill_id,order_id,execution_reference,quote_id,quantity,price,gross,
  fee,currency,posting_group,executed_at,booked_at)
VALUES(md5('fill')::uuid,1,'fixture-fill',md5('quote-1')::uuid,1,10,10,0,'USD',
  md5('posting-group')::uuid,'2026-10-23T08:01Z','2026-10-23T08:01Z');
INSERT INTO cash_journal(posting_id,client_id,currency,delta,fill_id,posting_group,actor,recorded_at)
VALUES(md5('cash')::uuid,1,'USD',-10,md5('fill')::uuid,md5('posting-group')::uuid,'engine','2026-10-23T08:01Z');
INSERT INTO position_journal(posting_id,client_id,instrument_id,delta,fill_id,posting_group,actor,recorded_at)
VALUES(md5('position')::uuid,1,1,1,md5('fill')::uuid,md5('posting-group')::uuid,'engine','2026-10-23T08:01Z');
INSERT INTO outbox_events(event_id,order_id,version,event_type,payload,created_at)
SELECT md5('event-'||n)::uuid,1,n,'ORDER_STATE','{}','2026-10-23T08:01Z'
FROM generate_series(1,5) n;
SET session_replication_role=origin;
INSERT INTO compliance_access_grants(grant_id,staff_user_id,permission,client_id,account_reference,
  valid_from,valid_until,approved_by,approval_reference)
SELECT md5(permission)::uuid,1,permission,1,'ACCOUNT-A','2026-10-23T08:00Z','2026-10-24T08:00Z',
  2,'APPROVAL-W1-12345678'
FROM unnest(ARRAY['EVIDENCE_READ','EXPORT_CREATE','EXPORT_DOWNLOAD']) permission;
""");
            }
            org.apache.ibatis.session.Configuration config =
                    new org.apache.ibatis.session.Configuration(
                            new Environment(
                                    "w1",
                                    new SpringManagedTransactionFactory(),
                                    new UnpooledDataSource(
                                            "org.postgresql.Driver", URL, USER, PASSWORD)));
            config.addMapper(ComplianceEvidenceMapper.class);
            config.addMapper(ComplianceExportMapper.class);
            config.addMapper(ComplianceAccessMapper.class);
            FACTORY = new SqlSessionFactoryBuilder().build(config);
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    static Map<String, Object> section(String name, int order, int page, int size, boolean count) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("section", name);
        parameters.put("id", order);
        parameters.put("actor", 1);
        parameters.put("permission", "EVIDENCE_READ");
        parameters.put("now", NOW);
        parameters.put("limit", size);
        parameters.put("offset", page * size);
        parameters.put("count", count);
        return parameters;
    }
}
