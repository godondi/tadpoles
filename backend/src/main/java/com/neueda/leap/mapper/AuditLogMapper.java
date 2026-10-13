package com.neueda.leap.mapper;

import com.neueda.leap.domain.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper {
    @Select("""
            SELECT audit_log_id AS auditLogId,
                   user_id AS userId,
                   entity_name AS entityName,
                   entity_id AS entityId,
                   action_type AS actionType,
                   old_values AS oldValues,
                   new_values AS newValues,
                   created_at AS createdAt
            FROM audit_logs
            ORDER BY created_at DESC, audit_log_id DESC
            """)
    List<AuditLog> listAuditLogs();

    @Select("""
            SELECT audit_log_id AS auditLogId,
                   user_id AS userId,
                   entity_name AS entityName,
                   entity_id AS entityId,
                   action_type AS actionType,
                   old_values AS oldValues,
                   new_values AS newValues,
                   created_at AS createdAt
            FROM audit_logs
            WHERE audit_log_id = #{id}
            """)
    AuditLog getAuditLog(@Param("id") Integer id);
}
