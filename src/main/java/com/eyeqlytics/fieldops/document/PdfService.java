package com.eyeqlytics.fieldops.document;

import com.eyeqlytics.fieldops.audit.ActivityLog;
import com.eyeqlytics.fieldops.identity.User;
import com.eyeqlytics.fieldops.identity.UserRepository;
import com.eyeqlytics.fieldops.identity.Zone;
import com.eyeqlytics.fieldops.identity.ZoneRepository;
import com.eyeqlytics.fieldops.workorder.WorkOrderView;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(IST);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.GRAY);
    private static final Color LABEL_BG = new Color(238, 238, 238);
    private static final Color HEAD_BG = new Color(210, 220, 235);

    private final UserRepository users;
    private final ZoneRepository zones;

    private interface Body { void write(Document doc) throws DocumentException; }

    // ------------------------------------------------------------------ Work order (planning document)

    public byte[] workOrder(WorkOrderView v) {
        return render("WORK ORDER #" + v.id(), "Issued for field execution", doc -> {
            doc.add(section("Job details"));
            doc.add(kv(new String[][]{
                    {"Work order no.", "WO-" + v.id()},
                    {"Linked service request", "SR-" + v.requestId()},
                    {"Title", v.title()},
                    {"Description", v.description()},
                    {"Priority", String.valueOf(v.priority())},
                    {"Status", String.valueOf(v.status())},
                    {"Zone", zoneName(v.zoneId())},
                    {"Created", fmt(v.createdAt())},
                    {"SLA deadline", fmt(v.slaDueAt())},
            }));
            doc.add(section("Assignment"));
            doc.add(kv(new String[][]{
                    {"Planned by", userName(v.plannedBy())},
                    {"Assigned technician", v.assignedTechnicianId() == null ? "Not yet assigned" : userName(v.assignedTechnicianId())},
            }));
            doc.add(section("Sign-off (to be completed on site)"));
            doc.add(kv(new String[][]{
                    {"Work performed", "\n\n\n"},
                    {"Technician signature / date", "\n\n"},
                    {"Customer signature / date", "\n\n"},
            }));
        });
    }

    // ------------------------------------------------------------------ Completion report

    public byte[] completionReport(WorkOrderView v, List<ActivityLog> history) {
        return render("COMPLETION REPORT - WO-" + v.id(), "Work order summary with verification and audit trail", doc -> {
            ActivityLog completed = last(history, "STATUS_COMPLETED");
            ActivityLog verified = last(history, "STATUS_VERIFIED");
            String sla = v.completedAt() == null ? "-"
                    : (v.completedAt().isAfter(v.slaDueAt()) ? "SLA BREACHED" : "Completed within SLA");

            doc.add(section("Job details"));
            doc.add(kv(new String[][]{
                    {"Work order no.", "WO-" + v.id() + " (request SR-" + v.requestId() + ")"},
                    {"Title", v.title()},
                    {"Description", v.description()},
                    {"Priority", String.valueOf(v.priority())},
                    {"Zone", zoneName(v.zoneId())},
                    {"Planned by", userName(v.plannedBy())},
                    {"Technician", v.assignedTechnicianId() == null ? "-" : userName(v.assignedTechnicianId())},
            }));

            doc.add(section("Outcome"));
            doc.add(kv(new String[][]{
                    {"Current status", String.valueOf(v.status())},
                    {"Created", fmt(v.createdAt())},
                    {"SLA deadline", fmt(v.slaDueAt())},
                    {"Completed at", fmt(v.completedAt())},
                    {"SLA result", sla},
                    {"Completion notes", completed == null ? "-" : completed.getRemarks()},
                    {"Verified by", verified == null ? "Not yet verified" : userName(verified.getActorId())},
                    {"Verification remarks", verified == null ? "-" : verified.getRemarks()},
            }));

            doc.add(section("Audit trail"));
            PdfPTable t = new PdfPTable(4);
            t.setWidthPercentage(100);
            t.setWidths(new float[]{22, 26, 18, 34});
            for (String h : new String[]{"Time", "Action", "By", "Remarks"}) t.addCell(cell(h, LABEL, HEAD_BG));
            for (ActivityLog l : history) {
                String action = l.getFromStatus() == null ? l.getAction()
                        : l.getAction() + " (" + l.getFromStatus() + " -> " + l.getToStatus() + ")";
                t.addCell(cell(fmt(l.getCreatedAt()), BODY, null));
                t.addCell(cell(action, BODY, null));
                t.addCell(cell(l.getActorId() == null ? "System" : userName(l.getActorId()), BODY, null));
                t.addCell(cell(l.getRemarks(), BODY, null));
            }
            doc.add(t);
        });
    }

    // ------------------------------------------------------------------ helpers

    private byte[] render(String heading, String subheading, Body body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph(heading, TITLE));
            doc.add(new Paragraph(subheading, SMALL));
            doc.add(Chunk.NEWLINE);
            body.write(doc);
            doc.add(Chunk.NEWLINE);
            doc.add(new Paragraph("Generated " + fmt(Instant.now()) + " IST | Field Operations Management System", SMALL));
        } catch (DocumentException e) {
            throw new IllegalStateException("PDF generation failed", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }

    private Paragraph section(String text) {
        Paragraph p = new Paragraph(text, H2);
        p.setSpacingBefore(12);
        p.setSpacingAfter(4);
        return p;
    }

    private PdfPTable kv(String[][] rows) throws DocumentException {
        PdfPTable t = new PdfPTable(2);
        t.setWidthPercentage(100);
        t.setWidths(new float[]{30, 70});
        for (String[] r : rows) {
            t.addCell(cell(r[0], LABEL, LABEL_BG));
            t.addCell(cell(r[1], BODY, null));
        }
        return t;
    }

    private PdfPCell cell(String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text == null || text.isBlank() ? "-" : text, font));
        c.setPadding(5);
        if (bg != null) c.setBackgroundColor(bg);
        return c;
    }

    private static ActivityLog last(List<ActivityLog> history, String action) {
        ActivityLog found = null;
        for (ActivityLog l : history) if (action.equals(l.getAction())) found = l;
        return found;
    }

    private static String fmt(Instant t) { return t == null ? "-" : FMT.format(t); }

    private String userName(Long id) {
        if (id == null) return "-";
        return users.findById(id).map(User::getFullName).orElse("User #" + id);
    }

    private String zoneName(Long id) {
        return zones.findById(id).map(Zone::getName).orElse("Zone #" + id);
    }
}
