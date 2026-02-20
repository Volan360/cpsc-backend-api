package com.cpsc.backend.controller;

import com.cpsc.backend.api.AnalyticsApi;
import com.cpsc.backend.model.AnalyticsDateRange;
import com.cpsc.backend.model.AnalyticsOptions;
import com.cpsc.backend.model.AnalyticsRequest;
import com.cpsc.backend.model.AnalyticsResponse;
import com.cpsc.backend.model.HealthScoreResponse;
import com.cpsc.backend.model.ReportRequest;
import com.cpsc.backend.model.ReportResponse;
import com.cpsc.backend.service.LambdaInvokerService;
import com.cpsc.backend.service.LambdaInvokerService.LambdaInvocationException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for financial analytics endpoints.
 *
 * <p>Delegates computation to AWS Lambda functions. For local development,
 * set {@code LAMBDA_ENDPOINT_URL=http://localhost:9001} and run
 * {@code python cpsc-analytics-scripts/local_lambda_server.py}.</p>
 */
@RestController
public class AnalyticsController implements AnalyticsApi {

    private static final Logger logger = LoggerFactory.getLogger(AnalyticsController.class);

    private final LambdaInvokerService lambdaInvokerService;
    private final ObjectMapper objectMapper;

    @Value("${lambda.analytics.function-name}")
    private String analyticsFunctionName;

    @Value("${lambda.report.function-name}")
    private String reportFunctionName;

    public AnalyticsController(LambdaInvokerService lambdaInvokerService) {
        this.lambdaInvokerService = lambdaInvokerService;
        this.objectMapper = new ObjectMapper();
    }

    // -------------------------------------------------------------------------
    // POST /api/analytics/generate
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<AnalyticsResponse> generateAnalytics(AnalyticsRequest request) {
        String userId = getAuthenticatedUserId();
        logger.info("Generating analytics type='{}' for userId='{}'",
                request.getAnalyticsType(), userId);

        try {
            Map<String, Object> event = buildLambdaEvent(userId, buildAnalyticsBody(request));
            Map<String, Object> proxyResponse = lambdaInvokerService.invoke(analyticsFunctionName, event);
            Map<String, Object> body = lambdaInvokerService.parseBody(proxyResponse);

            AnalyticsResponse response = mapAnalyticsResponse(body, request.getDateRange());
            return ResponseEntity.ok(response);

        } catch (LambdaInvocationException e) {
            logger.error("Lambda invocation failed for generateAnalytics: {}", e.getMessage());
            return ResponseEntity.status(e.getHttpStatus()).build();
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/analytics/report
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<ReportResponse> generateReport(ReportRequest request) {
        String userId = getAuthenticatedUserId();
        logger.info("Generating report type='{}' for userId='{}'",
                request.getReportType(), userId);

        try {
            Map<String, Object> event = buildLambdaEvent(userId, buildReportBody(request));
            Map<String, Object> proxyResponse = lambdaInvokerService.invoke(reportFunctionName, event);
            Map<String, Object> body = lambdaInvokerService.parseBody(proxyResponse);

            ReportResponse response = mapReportResponse(body, request.getDateRange());
            return ResponseEntity.ok(response);

        } catch (LambdaInvocationException e) {
            logger.error("Lambda invocation failed for generateReport: {}", e.getMessage());
            return ResponseEntity.status(e.getHttpStatus()).build();
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/analytics/health-score
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<HealthScoreResponse> getHealthScore(LocalDate startDate, LocalDate endDate) {
        String userId = getAuthenticatedUserId();

        // Default to last 30 days if not specified
        String resolvedStart = (startDate != null)
                ? startDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                : LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String resolvedEnd = (endDate != null)
                ? endDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                : LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        logger.info("Computing health score for userId='{}' from {} to {}", userId, resolvedStart, resolvedEnd);

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("analyticsType", "health");
            requestBody.put("dateRange", Map.of("start", resolvedStart, "end", resolvedEnd));
            requestBody.put("options", Map.of("includeRecommendations", true));

            Map<String, Object> event = buildLambdaEvent(userId, requestBody);
            Map<String, Object> proxyResponse = lambdaInvokerService.invoke(analyticsFunctionName, event);
            Map<String, Object> body = lambdaInvokerService.parseBody(proxyResponse);

            HealthScoreResponse response = mapHealthScoreResponse(body, userId);
            return ResponseEntity.ok(response);

        } catch (LambdaInvocationException e) {
            logger.error("Lambda invocation failed for getHealthScore: {}", e.getMessage());
            return ResponseEntity.status(e.getHttpStatus()).build();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Build the Lambda proxy event envelope expected by the Python handlers. */
    private Map<String, Object> buildLambdaEvent(String userId, Map<String, Object> requestBody) {
        try {
            String bodyJson = objectMapper.writeValueAsString(requestBody);
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", userId);

            Map<String, Object> authorizer = new LinkedHashMap<>();
            authorizer.put("claims", claims);

            Map<String, Object> requestContext = new LinkedHashMap<>();
            requestContext.put("authorizer", authorizer);

            Map<String, Object> event = new LinkedHashMap<>();
            event.put("requestContext", requestContext);
            event.put("body", bodyJson);

            return event;
        } catch (Exception e) {
            throw new LambdaInvocationException("Failed to serialize Lambda event body: " + e.getMessage(), 500);
        }
    }

    /** Build the body map for /api/analytics/generate requests. */
    private Map<String, Object> buildAnalyticsBody(AnalyticsRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("analyticsType", request.getAnalyticsType().getValue());

        AnalyticsDateRange dr = request.getDateRange();
        if (dr != null) {
            body.put("dateRange", Map.of("start", dr.getStart(), "end", dr.getEnd()));
        }

        AnalyticsOptions opts = request.getOptions();
        if (opts != null) {
            Map<String, Object> optMap = new LinkedHashMap<>();
            if (opts.getGroupBy() != null) {
                optMap.put("groupBy", opts.getGroupBy().getValue());
            }
            if (opts.getIncludeRecommendations() != null) {
                optMap.put("includeRecommendations", opts.getIncludeRecommendations());
            }
            if (opts.getUserName() != null) {
                optMap.put("userName", opts.getUserName());
            }
            body.put("options", optMap);
        }
        return body;
    }

    /** Build the body map for /api/analytics/report requests. */
    private Map<String, Object> buildReportBody(ReportRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reportType", request.getReportType().getValue());

        AnalyticsDateRange dr = request.getDateRange();
        if (dr != null) {
            body.put("dateRange", Map.of("start", dr.getStart(), "end", dr.getEnd()));
        }

        AnalyticsOptions opts = request.getOptions();
        if (opts != null) {
            Map<String, Object> optMap = new LinkedHashMap<>();
            if (opts.getGroupBy() != null) {
                optMap.put("groupBy", opts.getGroupBy().getValue());
            }
            if (opts.getIncludeRecommendations() != null) {
                optMap.put("includeRecommendations", opts.getIncludeRecommendations());
            }
            if (opts.getUserName() != null) {
                optMap.put("userName", opts.getUserName());
            }
            body.put("options", optMap);
        }
        return body;
    }

    /** Map the Lambda body response to an {@link AnalyticsResponse} model. */
    private AnalyticsResponse mapAnalyticsResponse(Map<String, Object> body, AnalyticsDateRange requestDateRange) {
        AnalyticsResponse response = new AnalyticsResponse();
        response.setAnalyticsType((String) body.get("analyticsType"));
        response.setUserId((String) body.get("userId"));
        response.setGeneratedAt((String) body.get("generatedAt"));

        // Prefer date range from Lambda response; fall back to request date range
        Object drObj = body.get("dateRange");
        if (drObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> drMap = (Map<String, Object>) drObj;
            AnalyticsDateRange dr = new AnalyticsDateRange();
            dr.setStart((String) drMap.get("start"));
            dr.setEnd((String) drMap.get("end"));
            response.setDateRange(dr);
        } else if (requestDateRange != null) {
            response.setDateRange(requestDateRange);
        }

        Object dataObj = body.get("data");
        if (dataObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            response.setData(dataMap);
        }

        return response;
    }

    /** Map the Lambda body response to a {@link ReportResponse} model. */
    private ReportResponse mapReportResponse(Map<String, Object> body, AnalyticsDateRange requestDateRange) {
        ReportResponse response = new ReportResponse();
        response.setReportType((String) body.get("reportType"));
        response.setUserId((String) body.get("userId"));
        response.setGeneratedAt((String) body.get("generatedAt"));
        response.setReportUrl((String) body.get("reportUrl"));
        response.setS3Key((String) body.get("s3Key"));
        response.setBucket((String) body.get("bucket"));

        Object drObj = body.get("dateRange");
        if (drObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> drMap = (Map<String, Object>) drObj;
            AnalyticsDateRange dr = new AnalyticsDateRange();
            dr.setStart((String) drMap.get("start"));
            dr.setEnd((String) drMap.get("end"));
            response.setDateRange(dr);
        } else if (requestDateRange != null) {
            response.setDateRange(requestDateRange);
        }

        return response;
    }

    /**
     * Map the Lambda body response to a {@link HealthScoreResponse} model.
     *
     * <p>The Python handler returns health score details in a nested {@code data}
     * field using snake_case keys ({@code overall_score}, {@code period_days},
     * {@code computed_at}) which must be mapped manually.</p>
     */
    @SuppressWarnings("unchecked")
    private HealthScoreResponse mapHealthScoreResponse(Map<String, Object> body, String userId) {
        HealthScoreResponse response = new HealthScoreResponse();
        response.setUserId((String) body.getOrDefault("userId", userId));

        Object dataObj = body.get("data");
        if (!(dataObj instanceof Map)) {
            logger.warn("Health score Lambda response missing 'data' field");
            return response;
        }

        Map<String, Object> data = (Map<String, Object>) dataObj;

        // overall_score (snake_case from Python)
        Object overallScore = data.get("overall_score");
        if (overallScore instanceof Number) {
            response.setOverallScore(((Number) overallScore).doubleValue());
        }

        // rating
        response.setRating((String) data.get("rating"));

        // components (nested map — return as-is)
        Object componentsObj = data.get("components");
        if (componentsObj instanceof Map) {
            response.setComponents((Map<String, Object>) componentsObj);
        }

        // recommendations (list of strings)
        Object recommendationsObj = data.get("recommendations");
        if (recommendationsObj instanceof List) {
            List<String> recommendations = new ArrayList<>();
            for (Object item : (List<?>) recommendationsObj) {
                recommendations.add(String.valueOf(item));
            }
            response.setRecommendations(recommendations);
        }

        // period_days (snake_case from Python)
        Object periodDays = data.get("period_days");
        if (periodDays instanceof Number) {
            response.setPeriodDays(((Number) periodDays).intValue());
        }

        // computed_at (snake_case from Python)
        response.setComputedAt((String) data.get("computed_at"));

        return response;
    }

    /** Extract the authenticated user's Cognito sub (userId) from the security context. */
    private String getAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (String) authentication.getPrincipal();
    }
}
