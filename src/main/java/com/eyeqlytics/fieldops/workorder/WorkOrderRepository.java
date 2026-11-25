package com.eyeqlytics.fieldops.workorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long>, JpaSpecificationExecutor<WorkOrder> {

    @Query("select w from WorkOrder w where w.id in " +
           "(select a.workOrderId from WorkOrderAssignment a where a.technicianId = :techId and a.active = true)")
    Page<WorkOrder> findAssignedTo(@Param("techId") Long techId, Pageable pageable);

    /** Work orders past their SLA deadline that are not yet done (used by the SLA monitor). */
    @Query("select w from WorkOrder w where w.slaDueAt < :now and w.status not in :doneStates")
    List<WorkOrder> findOverdueOpen(@Param("now") Instant now,
                                    @Param("doneStates") Collection<WorkOrderStatus> doneStates);
}
