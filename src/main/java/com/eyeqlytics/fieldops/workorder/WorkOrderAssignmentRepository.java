package com.eyeqlytics.fieldops.workorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkOrderAssignmentRepository extends JpaRepository<WorkOrderAssignment, Long> {

    Optional<WorkOrderAssignment> findByWorkOrderIdAndActiveTrue(Long workOrderId);

    List<WorkOrderAssignment> findByWorkOrderIdInAndActiveTrue(Collection<Long> workOrderIds);


    @Query("select count(a) from WorkOrderAssignment a join WorkOrder w on w.id = a.workOrderId " +
           "where a.technicianId = :techId and a.active = true and w.status in :statuses")
    long countActiveWorkload(@Param("techId") Long techId,
                             @Param("statuses") Collection<WorkOrderStatus> statuses);
}
