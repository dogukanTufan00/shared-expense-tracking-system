package com.dogukan_tufan.shared_expense_tracking_system.controller;

import com.dogukan_tufan.shared_expense_tracking_system.dto.payment.PaymentRequest;
import com.dogukan_tufan.shared_expense_tracking_system.dto.payment.PaymentResponse;
import com.dogukan_tufan.shared_expense_tracking_system.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/households/{householdId}/payments")
@RequiredArgsConstructor
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@PathVariable Long householdId, @Valid @RequestBody PaymentRequest request,
                                   Authentication authentication) {
        return paymentService.create(householdId, request, authentication.getName());
    }

    @GetMapping
    public List<PaymentResponse> list(@PathVariable Long householdId, Authentication authentication) {
        return paymentService.list(householdId, authentication.getName());
    }

    @DeleteMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long householdId, @PathVariable Long paymentId, Authentication authentication) {
        paymentService.delete(householdId, paymentId, authentication.getName());
    }
}
