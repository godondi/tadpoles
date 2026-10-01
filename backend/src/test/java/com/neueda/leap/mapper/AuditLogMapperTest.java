package com.neueda.leap.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.neueda.leap.domain.AuditLog;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

@MybatisTest
@Sql(scripts = {"classpath:mapper/revised_schema.sql", "classpath:mapper/revised_data.sql"})
class AuditLogMapperTest {
    @Autowired
    private AuditLogMapper auditLogMapper;

    @Test
    void listAuditLogsReturnsRowsOrderedNewestFirst() {
        List<AuditLog> auditLogs = auditLogMapper.listAuditLogs();

        assertEquals(2, auditLogs.size());
        assertEquals(32, auditLogs.get(0).getAuditLogId());
    }

    @Test
    void getAuditLogReturnsRow() {
        AuditLog auditLog = auditLogMapper.getAuditLog(31);

        assertNotNull(auditLog);
        assertEquals("client_subscriptions", auditLog.getEntityName());
        assertEquals("CREATE", auditLog.getActionType());
    }
}
