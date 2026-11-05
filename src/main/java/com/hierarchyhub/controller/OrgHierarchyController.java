package com.hierarchyhub.controller;

import com.hierarchyhub.dto.Constants;
import com.hierarchyhub.dto.LinkOrgRequest;
import com.hierarchyhub.dto.MoveOrgRequest;
import com.hierarchyhub.dto.ReadChildrenRequest;
import com.hierarchyhub.dto.UnlinkOrgRequest;
import com.hierarchyhub.model.ApiResponse;
import com.hierarchyhub.service.BulkLinkFileService;
import com.hierarchyhub.service.OrgHierarchyService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@RestController
@RequestMapping("/org/v2/hierarchy")
public class OrgHierarchyController {

    private final OrgHierarchyService service;
    private final BulkLinkFileService bulkLinkFileService;

    public OrgHierarchyController(OrgHierarchyService service, BulkLinkFileService bulkLinkFileService) {
        this.service = service;
        this.bulkLinkFileService = bulkLinkFileService;
    }

    @PostMapping("/link")
    public ResponseEntity<ApiResponse> link(@Valid @RequestBody LinkOrgRequest request,
                                             @RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken) {
        ApiResponse response = service.linkOrgToHierarchy(request, accessToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/unlink")
    public ResponseEntity<ApiResponse> unlink(@Valid @RequestBody UnlinkOrgRequest request,
                                              @RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken) {
        ApiResponse response = service.unlinkOrgFromHierarchy(request, accessToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/move")
    public ResponseEntity<ApiResponse> move(@Valid @RequestBody MoveOrgRequest request,
                                             @RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken) {
        ApiResponse response = service.moveOrgInHierarchy(request, accessToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/{orgId}/complete")
    public ResponseEntity<ApiResponse> getCompleteHierarchy(@PathVariable String orgId, @RequestParam(required = false) Integer depth,
                                                            @RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken) {
        ApiResponse response = service.readOrgHierarchy(orgId, depth, accessToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping("/read/{orgId}")
    public ResponseEntity<ApiResponse> read(@PathVariable String orgId,
                                             @Valid @RequestBody(required = false) ReadChildrenRequest request,
                                             @RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken) {
        ApiResponse response = service.readOrgChildren(orgId, request, accessToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @PostMapping(value = "/bulk/link/{rootOrgId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> bulkLink(@RequestParam("file") MultipartFile file,
            @PathVariable("rootOrgId") String rootOrgId, @RequestHeader(Constants.AUTH_TOKEN_HEADER) String accessToken) {
        ApiResponse response = bulkLinkFileService.initiate(file, rootOrgId, accessToken);
        return new ResponseEntity<>(response, response.getResponseCode());
    }
}
