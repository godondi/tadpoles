package com.neueda.leap.mapper;

import org.apache.ibatis.annotations.*;

import java.util.*;

@Mapper
public interface SupportCaseMapper {
    String SCOPE =
            """
            (#{staff} = false AND c.client_id=#{client} OR #{staff} = true AND EXISTS
              (SELECT 1 FROM operations_access_grants g WHERE g.staff_user_id=#{actor}
               AND g.client_id=c.client_id AND g.permission=#{permission} AND g.revoked_at IS NULL
               AND g.valid_from <= #{now} AND g.valid_until > #{now}))
            AND (c.order_id IS NULL OR EXISTS (SELECT 1 FROM client_trades t
                 WHERE t.trade_id=c.order_id AND t.client_id=c.client_id))
            """;
    String FILTER =
            """
AND (#{status}::text IS NULL OR c.status=#{status})
AND (#{search}='' OR c.reference=#{search} OR position(lower(#{search}) in lower(c.subject))>0)
""";

    @Select("SELECT client_id FROM clients WHERE client_id=#{client} FOR UPDATE")
    Integer lockOwner(int client);

    @Select("SELECT count(*)=1 FROM client_trades WHERE trade_id=#{order} AND client_id=#{client}")
    boolean ownedOrder(@Param("order") int order, @Param("client") int client);

    @Select("SELECT c.* FROM support_cases c WHERE c.client_id=#{client} AND c.request_key=#{key}")
    Map<String, Object> byRequest(Map<String, Object> p);

    @Select("SELECT c.* FROM support_cases c WHERE c.reference=#{reference} AND " + SCOPE)
    Map<String, Object> detail(Map<String, Object> p);

    @Select(
            "SELECT c.* FROM support_cases c WHERE c.reference=#{reference} AND "
                    + SCOPE
                    + " FOR UPDATE OF c")
    Map<String, Object> lock(Map<String, Object> p);

    @Select(
            "SELECT c.* FROM support_cases c WHERE "
                    + SCOPE
                    + FILTER
                    + " ORDER BY c.created_at DESC,c.case_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> list(Map<String, Object> p);

    @Select("SELECT count(*) FROM support_cases c WHERE " + SCOPE + FILTER)
    long count(Map<String, Object> p);

    @Select(
            "SELECT e.event_id AS id,e.version,e.kind,e.author_role AS"
                + " \"authorRole\",e.body,e.previous_status AS"
                + " \"previousStatus\",e.status,e.recorded_at AS \"recordedAt\" FROM"
                + " support_case_events e JOIN support_cases c USING(case_id) WHERE"
                + " c.reference=#{reference} AND "
                    + SCOPE
                    + " ORDER BY e.version LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> events(Map<String, Object> p);

    @Insert(
            """
INSERT INTO support_cases(case_id,reference,client_id,order_id,category,subject,request_key,request_hash,status,version,created_at,updated_at)
VALUES(#{id},#{reference},#{client},#{order},#{category},#{subject},#{key},#{hash},'OPEN',1,#{now},#{now})
""")
    void create(Map<String, Object> p);

    @Insert(
            """
INSERT INTO support_case_events(case_id,client_id,version,kind,actor,author_role,body,previous_status,status,recorded_at)
VALUES(#{id},#{client},#{version},#{kind},#{actor},#{role},#{body},#{previous},#{status},#{now})
""")
    void event(Map<String, Object> p);

    @Select(
            "SELECT request_hash,receipt::text FROM support_case_requests WHERE case_id=#{id} AND"
                + " actor=#{actor} AND request_key=#{key}")
    Map<String, Object> request(Map<String, Object> p);

    @Insert(
            "INSERT INTO"
                + " support_case_requests(case_id,actor,request_key,request_hash,receipt,recorded_at)"
                + " VALUES(#{id},#{actor},#{key},#{hash},#{receipt}::jsonb,#{now})")
    void saveRequest(Map<String, Object> p);

    @Update(
            "UPDATE support_cases SET version=#{version},status=#{status},updated_at=#{now} WHERE"
                + " case_id=#{id}")
    void advance(Map<String, Object> p);

    @Insert(
            "INSERT INTO support_case_notes(note_id,case_id,actor,body,recorded_at)"
                + " VALUES(#{note},#{id},#{actor},#{body},#{now})")
    void note(Map<String, Object> p);

    @Select(
            "SELECT n.note_id AS id,n.actor,n.body,n.recorded_at AS \"recordedAt\" FROM"
                + " support_case_notes n JOIN support_cases c USING(case_id) WHERE"
                + " c.reference=#{reference} AND "
                    + SCOPE
                    + " ORDER BY n.recorded_at,n.note_id LIMIT #{limit} OFFSET #{offset}")
    List<Map<String, Object>> notes(Map<String, Object> p);

    @Select(
            "SELECT count(*) FROM support_case_notes n JOIN support_cases c USING(case_id) WHERE"
                + " c.reference=#{reference} AND "
                    + SCOPE)
    long noteCount(Map<String, Object> p);

    @Select("SELECT coalesce(max(event_id),0) FROM support_case_events WHERE client_id=#{client}")
    long latest(int client);

    @Select(
            "SELECT count(*)=1 FROM support_case_events WHERE client_id=#{client} AND"
                + " event_id=#{cursor}")
    boolean ownedEvent(Map<String, Object> p);

    @Select(
            "SELECT e.event_id AS id,c.reference,c.subject,e.kind,e.status,e.recorded_at AS"
                + " \"recordedAt\" FROM support_case_events e JOIN support_cases c USING(case_id)"
                + " WHERE e.client_id=#{client} AND e.event_id>#{cursor} ORDER BY e.event_id LIMIT"
                + " 101")
    List<Map<String, Object>> updates(Map<String, Object> p);

    @Select(
            "SELECT count(*) FROM support_case_events e WHERE e.client_id=#{client} AND"
                + " e.event_id>coalesce((SELECT last_event FROM support_read_state WHERE"
                + " client_id=#{client} AND recipient=#{actor}),0)")
    long unread(Map<String, Object> p);

    @Insert(
            "INSERT INTO support_read_state(client_id,recipient,last_event)"
                + " VALUES(#{client},#{actor},#{cursor}) ON CONFLICT(client_id,recipient) DO UPDATE"
                + " SET last_event=greatest(support_read_state.last_event,excluded.last_event)")
    void markRead(Map<String, Object> p);

    @Insert(
            "INSERT INTO support_case_exports(case_id,export_id,digest,actor,recorded_at)"
                + " VALUES(#{id},#{export},#{digest},#{actor},#{now}) ON"
                + " CONFLICT(case_id,export_id) DO NOTHING")
    void associate(Map<String, Object> p);
}
