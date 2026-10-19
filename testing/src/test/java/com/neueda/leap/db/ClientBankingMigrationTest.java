package com.neueda.leap.db;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClientBankingMigrationTest extends AbstractPostgresIntegrationTest {
    @Test
    void bankingSchemaRetainsOwnedReferencesAndImmutableExactMovementEvidence() throws Exception {
        executeSqlScript(Path.of("../database/migrations/004_client_simulation.sql"));
        executeSqlScript(Path.of("../database/migrations/005_client_banking.sql"));
        try (var c = newConnection();
                var s = c.createStatement()) {
            var r =
                    s.executeQuery(
                            "SELECT to_regclass('bank_references') IS NOT NULL AND"
                                    + " to_regclass('cash_movements') IS NOT NULL");
            r.next();
            assertTrue(
                    r.getBoolean(1), "Owned bank references and durable cash movements must exist");
            s.execute("INSERT INTO clients(client_name) VALUES('Client A'),('Client B')");
            s.execute(
                    "INSERT INTO"
                        + " bank_references(bank_id,client_id,nickname,encrypted_details,last_four,is_default)"
                        + " VALUES('00000000-0000-0000-0000-000000000001',1,'Test"
                        + " bank','encrypted-test','0001',true)");
            assertThrows(
                    java.sql.SQLException.class,
                    () ->
                            s.execute(
                                    "INSERT INTO"
                                        + " bank_references(bank_id,client_id,nickname,encrypted_details,last_four)"
                                        + " VALUES(gen_random_uuid(),1,'test"
                                        + " BANK','encrypted-test','0002')"));
            assertThrows(
                    java.sql.SQLException.class,
                    () ->
                            s.execute(
                                    "INSERT INTO"
                                        + " cash_movements(movement_id,client_id,bank_id,request_key,request_hash,direction,amount,status,bank_snapshot,posting_group,actor)"
                                        + " VALUES(gen_random_uuid(),2,'00000000-0000-0000-0000-000000000001','request-key-0001','hash','DEPOSIT',10,'SETTLED','masked',gen_random_uuid(),'test')"));
            s.execute(
                    "INSERT INTO"
                        + " cash_movements(movement_id,client_id,bank_id,request_key,request_hash,direction,amount,status,bank_snapshot,posting_group,actor)"
                        + " VALUES('00000000-0000-0000-0000-000000000002',1,'00000000-0000-0000-0000-000000000001','request-key-0001','hash','DEPOSIT',10,'SETTLED','masked',gen_random_uuid(),'test')");
            assertThrows(
                    java.sql.SQLException.class,
                    () -> s.execute("UPDATE cash_movements SET amount=20"));
            assertThrows(
                    java.sql.SQLException.class, () -> s.execute("DELETE FROM cash_movements"));
            assertThrows(
                    java.sql.SQLException.class, () -> s.execute("DELETE FROM bank_references"));
        }
    }
}
