package com.medical.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.appointment.domain.entity.AppointmentEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentEventOutboxMapper extends BaseMapper<AppointmentEventOutbox> {

    @Select({
            "<script>",
            "SELECT * FROM appointment_event_outbox",
            "WHERE deleted = 0",
            "  AND (publish_status = #{pendingStatus}",
            "       OR (publish_status = #{publishingStatus} AND update_time <![CDATA[<=]]> #{reclaimBefore}))",
            "ORDER BY id ASC",
            "LIMIT #{batchSize}",
            "</script>"
    })
    List<AppointmentEventOutbox> selectPublishCandidates(
            @Param("pendingStatus") Integer pendingStatus,
            @Param("publishingStatus") Integer publishingStatus,
            @Param("reclaimBefore") LocalDateTime reclaimBefore,
            @Param("batchSize") Integer batchSize);

    @Update({
            "<script>",
            "UPDATE appointment_event_outbox",
            "SET publish_status = #{publishingStatus},",
            "    last_error = NULL,",
            "    published_at = NULL",
            "WHERE id = #{id}",
            "  AND deleted = 0",
            "  AND (publish_status = #{pendingStatus}",
            "       OR (publish_status = #{publishingStatus} AND update_time <![CDATA[<=]]> #{reclaimBefore}))",
            "</script>"
    })
    int claimForPublish(
            @Param("id") Long id,
            @Param("pendingStatus") Integer pendingStatus,
            @Param("publishingStatus") Integer publishingStatus,
            @Param("reclaimBefore") LocalDateTime reclaimBefore);

    @Update("""
            UPDATE appointment_event_outbox
            SET publish_status = #{publishedStatus},
                published_at = #{publishedAt},
                last_error = NULL
            WHERE id = #{id}
              AND deleted = 0
              AND publish_status = #{publishingStatus}
            """)
    int markPublished(
            @Param("id") Long id,
            @Param("publishingStatus") Integer publishingStatus,
            @Param("publishedStatus") Integer publishedStatus,
            @Param("publishedAt") LocalDateTime publishedAt);

    @Update("""
            UPDATE appointment_event_outbox
            SET publish_status = #{pendingStatus},
                retry_count = #{retryCount},
                last_error = #{lastError},
                published_at = NULL
            WHERE id = #{id}
              AND deleted = 0
              AND publish_status = #{publishingStatus}
            """)
    int markPendingForRetry(
            @Param("id") Long id,
            @Param("publishingStatus") Integer publishingStatus,
            @Param("retryCount") Integer retryCount,
            @Param("lastError") String lastError,
            @Param("pendingStatus") Integer pendingStatus);
}
