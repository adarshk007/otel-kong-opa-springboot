package com.example.demo.opa;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
public class OpaClient {

    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Value("${OPA_URL:http://localhost:8181/v1/data/http/authz/allow}")
    private String opaUrl;

    public boolean isAllowed(HttpServletRequest req) {
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("method", req.getMethod());
            input.put("path", req.getRequestURI());

            // Forward all headers, including W3C trace context (traceparent, tracestate)
            Map<String, String> headersMap = new HashMap<>();
            Enumeration<String> headerNames = req.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headersMap.put(name.toLowerCase(), req.getHeader(name));
            }
            input.put("headers", headersMap);

            Map<String, Object> body = Map.of("input", input);
            String json = mapper.writeValueAsString(body);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Also propagate trace headers explicitly to OPA (best effort)
            if (headersMap.containsKey("traceparent")) {
                headers.add("traceparent", headersMap.get("traceparent"));
            }
            if (headersMap.containsKey("tracestate")) {
                headers.add("tracestate", headersMap.get("tracestate"));
            }

            ResponseEntity<String> resp = http.postForEntity(opaUrl, new HttpEntity<>(json, headers), String.class);

            // Expecting: {"result": true/false}
            Map<?,?> parsed = mapper.readValue(resp.getBody(), Map.class);
            Object result = parsed.get("result");
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // Fail closed in this demo
            return false;
        }
    }
}
