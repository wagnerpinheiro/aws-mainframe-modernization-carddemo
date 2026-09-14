package com.carddemo.web.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Replicates CORPT00C — report trigger screen.
 *
 * Q10 architectural substitution (established premise): no JCL reconstruction.
 * Accepts POST /reports/trigger with {reportType, startDate, endDate} and
 * returns HTTP 202 Accepted with a jobExecutionId.
 *
 * For the PoC, this fires an async log entry (not a real Spring Batch job) — the
 * important proof is that the REST endpoint exists, accepts the parameters mapped
 * from CORPT00C's BMS screen fields, and returns 202 with a tracking ID.
 * Production wiring: inject JobLauncher + transactionReportJob bean.
 *
 * TODO(prod): inject JobLauncher and run transactionReportJob asynchronously.
 */
@RestController
@RequestMapping("/reports")
public class ReportTriggerController {

    private static final Logger log = LoggerFactory.getLogger(ReportTriggerController.class);

    /**
     * Triggers report generation asynchronously.
     *
     * CORPT00C BMS fields → REST params:
     *   RPTTYP → reportType
     *   RPTSTART → startDate  (YYYY-MM-DD)
     *   RPTEND   → endDate    (YYYY-MM-DD)
     *
     * Returns HTTP 202 Accepted with jobExecutionId.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@RequestBody ReportTriggerRequest req) {
        String jobExecutionId = UUID.randomUUID().toString();
        log.info("Report trigger accepted: type={}, start={}, end={}, jobExecutionId={}",
            req.reportType(), req.startDate(), req.endDate(), jobExecutionId);
        // TODO(prod): jobLauncher.run(transactionReportJob, jobParams);
        return ResponseEntity.accepted().body(Map.of(
            "jobExecutionId", jobExecutionId,
            "reportType", req.reportType() != null ? req.reportType() : "",
            "startDate",   req.startDate() != null ? req.startDate() : "",
            "endDate",     req.endDate()   != null ? req.endDate()   : "",
            "status",      "STARTING",
            "triggeredAt", LocalDateTime.now().toString()
        ));
    }
}
