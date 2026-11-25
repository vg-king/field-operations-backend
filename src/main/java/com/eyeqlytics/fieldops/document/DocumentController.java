package com.eyeqlytics.fieldops.document;

import com.eyeqlytics.fieldops.common.ApiException;
import com.eyeqlytics.fieldops.security.AuthUser;
import com.eyeqlytics.fieldops.workorder.WorkOrderService;
import com.eyeqlytics.fieldops.workorder.WorkOrderStatus;
import com.eyeqlytics.fieldops.workorder.WorkOrderView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/work-orders/{id}/pdf")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','PLANNER','SUPERVISOR','TECHNICIAN')")
public class DocumentController {

    private static final Set<WorkOrderStatus> REPORTABLE =
            Set.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.VERIFIED, WorkOrderStatus.CLOSED);

    private final WorkOrderService workOrders;
    private final PdfService pdf;

    @GetMapping("/work-order")
    public ResponseEntity<byte[]> workOrder(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        WorkOrderView v = workOrders.get(id, actor);          // also enforces zone / assignment access
        return pdfResponse(pdf.workOrder(v), "work-order-" + id + ".pdf");
    }

    @GetMapping("/completion-report")
    public ResponseEntity<byte[]> completionReport(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        WorkOrderView v = workOrders.get(id, actor);
        if (!REPORTABLE.contains(v.status()))
            throw ApiException.conflict("NOT_COMPLETED",
                    "Completion report is available once work is COMPLETED (current: " + v.status() + ")");
        return pdfResponse(pdf.completionReport(v, workOrders.history(id, actor)), "completion-report-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(bytes);
    }
}
