package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.attachment.AttachmentResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Thin controller: it only maps HTTP to AttachmentService calls. Upload endpoints use
// multipart/form-data, which is how a browser or the app sends a file (Swagger shows a file picker).
@RestController
@RequestMapping("/api/households/{householdId}")
@RequiredArgsConstructor
@Tag(name = "Attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping(value = "/expenses/{expenseId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a receipt or bill (JPEG, PNG, WEBP or PDF, max 10 MB) to an expense")
    public AttachmentResponse uploadToExpense(@PathVariable Long householdId, @PathVariable Long expenseId,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              Authentication authentication) {
        return attachmentService.uploadToExpense(householdId, expenseId, file, authentication.getName());
    }

    @GetMapping("/expenses/{expenseId}/attachments")
    @Operation(summary = "List the attachments of an expense")
    public List<AttachmentResponse> listForExpense(@PathVariable Long householdId, @PathVariable Long expenseId,
                                                   Authentication authentication) {
        return attachmentService.listForExpense(householdId, expenseId, authentication.getName());
    }

    @PostMapping(value = "/payments/{paymentId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attach a transfer slip (dekont) or other proof (JPEG, PNG, WEBP or PDF, max 10 MB) to a payment")
    public AttachmentResponse uploadToPayment(@PathVariable Long householdId, @PathVariable Long paymentId,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              Authentication authentication) {
        return attachmentService.uploadToPayment(householdId, paymentId, file, authentication.getName());
    }

    @GetMapping("/payments/{paymentId}/attachments")
    @Operation(summary = "List the attachments of a payment")
    public List<AttachmentResponse> listForPayment(@PathVariable Long householdId, @PathVariable Long paymentId,
                                                   Authentication authentication) {
        return attachmentService.listForPayment(householdId, paymentId, authentication.getName());
    }

    // Returns the raw bytes. The app sends the JWT header, so this is not a public link.
    @GetMapping("/attachments/{attachmentId}/file")
    @Operation(summary = "Download or view the file of an attachment")
    public ResponseEntity<Resource> download(@PathVariable Long householdId, @PathVariable Long attachmentId,
                                             Authentication authentication) {
        AttachmentService.DownloadedFile file =
                attachmentService.download(householdId, attachmentId, authentication.getName());
        AttachmentResponse info = file.info();

        // "inline" asks the browser/app to display the file; the UTF-8 filename* form keeps
        // Turkish characters (ş, ğ, ı, ö, ü, ç) intact in the file name.
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(info.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(info.contentType()))
                .contentLength(info.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(file.resource());
    }

    @DeleteMapping("/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an attachment (uploader, payer or sender, or household admin)")
    public void delete(@PathVariable Long householdId, @PathVariable Long attachmentId,
                       Authentication authentication) {
        attachmentService.delete(householdId, attachmentId, authentication.getName());
    }
}
