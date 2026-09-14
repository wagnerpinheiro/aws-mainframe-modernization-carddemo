package com.carddemo.web.report;

/** Request body for POST /reports/trigger — maps CORPT00C BMS screen fields to REST params. */
public record ReportTriggerRequest(String reportType, String startDate, String endDate) {}
