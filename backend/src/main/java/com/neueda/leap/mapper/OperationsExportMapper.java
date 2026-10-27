package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface OperationsExportMapper {
    @Select(
            "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{key},#{actor})))"
                    + " locked")
    int lockKey(@Param("actor") int actor, @Param("key") String key);

    @Select(
            "SELECT export_id::text FROM operations_evidence_exports WHERE requester=#{actor} AND"
                    + " idempotency_key=#{key}")
    String existing(@Param("actor") int actor, @Param("key") String key);

    @Select(
            "SELECT"
                + " export_id,requester,context_id,order_id,scope_version,snapshot_at,expires_at,order_version,digest"
                + " FROM operations_evidence_exports WHERE export_id=#{id}")
    Map<String, Object> metadata(@Param("id") UUID id);

    @Select("SELECT archive FROM operations_evidence_exports WHERE export_id=#{id}")
    Map<String, Object> archive(@Param("id") UUID id);

    @Insert(
            """
INSERT INTO operations_evidence_exports(export_id,requester,context_id,order_id,idempotency_key,
  request_hash,scope_version,snapshot_at,expires_at,order_version,digest,archive)
VALUES(#{id},#{actor},#{context},#{order},#{key},#{hash},#{scope},#{snapshot},#{expires},#{version},#{digest},#{archive})
""")
    void save(Map<String, Object> values);
}
