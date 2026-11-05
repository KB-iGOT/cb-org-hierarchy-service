package com.hierarchyhub.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hierarchyhub.dto.Constants;
import org.springframework.http.HttpStatus;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ApiResponse {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String id;
    private String ver;
    private String ts;
    private ApiRespParam params;
    private HttpStatus responseCode;

    // LinkedHashMap, not HashMap: callers build this field-by-field in a deliberate order (id, name, ... then
    // associations), and the JSON response should come out in that same order instead of HashMap's unspecified one.
    private transient Map<String, Object> response = new LinkedHashMap<>();

    public ApiResponse() {
        this.ver = "v1";
        this.ts = new Timestamp(System.currentTimeMillis()).toString();
        this.params = new ApiRespParam(UUID.randomUUID().toString());
    }

    public ApiResponse(String id) {
        this();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVer() {
        return ver;
    }

    public void setVer(String ver) {
        this.ver = ver;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }

    public ApiRespParam getParams() {
        return params;
    }

    public void setParams(ApiRespParam params) {
        this.params = params;
    }

    public HttpStatus getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(HttpStatus responseCode) {
        this.responseCode = responseCode;
    }

    public Map<String, Object> getResult() {
        return response;
    }

    public void setResult(Map<String, Object> result) {
        response = result;
    }

    public Object get(String key) {
        return response.get(key);
    }

    public void put(String key, Object vo) {
        response.put(key, vo);
    }

    public void putAll(Map<String, Object> map) {
        response.putAll(map);
    }

    public boolean containsKey(String key) {
        return response.containsKey(key);
    }

    /**
     * A successful response with {@code result}'s own fields flattened directly into the result map (not nested under
     * a "result" key) - {@code result} may be {@code null} (an empty result), a {@link Map}, or any POJO.
     */
    @SuppressWarnings("unchecked")
    public static ApiResponse success(String apiId, Object result) {
        ApiResponse response = new ApiResponse(apiId);
        response.setResponseCode(HttpStatus.OK);
        response.getParams().setStatus(Constants.STATUS_SUCCESS);
        if (result != null) {
            Map<String, Object> asMap = result instanceof Map
                    ? (Map<String, Object>) result
                    : MAPPER.convertValue(result, new TypeReference<Map<String, Object>>() {
                    });
            response.putAll(asMap);
        }
        return response;
    }

    /** A failed response: no result, {@code err}/{@code errmsg} set from the given error code and message. */
    public static ApiResponse failure(String apiId, HttpStatus httpStatus, String errorCode, String errorMessage) {
        ApiResponse response = new ApiResponse(apiId);
        response.setResponseCode(httpStatus);
        response.getParams().setStatus(Constants.STATUS_FAILED);
        response.getParams().setErr(errorCode);
        response.getParams().setErrmsg(errorMessage);
        return response;
    }

}
