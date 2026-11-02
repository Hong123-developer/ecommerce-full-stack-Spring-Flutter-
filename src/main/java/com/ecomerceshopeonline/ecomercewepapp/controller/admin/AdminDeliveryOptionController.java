package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.DeliveryOptionOrderRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.DeliveryOptionRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.DeliveryOptionResponse;
import com.ecomerceshopeonline.ecomercewepapp.service.DeliveryOptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/delivery-options")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryOptionController {

    private final DeliveryOptionService deliveryOptionService;

    @GetMapping
    public ApiResponse<List<DeliveryOptionResponse>> listAll() {
        return ApiResponse.ok(deliveryOptionService.listAll());
    }

    @GetMapping("/{id:\\d+}")
    public ApiResponse<DeliveryOptionResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(deliveryOptionService.getById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeliveryOptionResponse> create(
            @Valid @RequestBody DeliveryOptionRequest request) {
        return ApiResponse.ok("Delivery option created", deliveryOptionService.create(request));
    }

    @PutMapping("/order")
    public ApiResponse<List<DeliveryOptionResponse>> reorder(
            @Valid @RequestBody DeliveryOptionOrderRequest request) {
        return ApiResponse.ok("Delivery option order updated",
                deliveryOptionService.reorder(request.ids()));
    }

    @PutMapping("/{id:\\d+}")
    public ApiResponse<DeliveryOptionResponse> update(
            @PathVariable Long id, @Valid @RequestBody DeliveryOptionRequest request) {
        return ApiResponse.ok("Delivery option updated", deliveryOptionService.update(id, request));
    }

    @PatchMapping("/{id:\\d+}/status")
    public ApiResponse<DeliveryOptionResponse> setEnabled(@PathVariable Long id,
                                                          @RequestParam boolean enabled) {
        return ApiResponse.ok(enabled ? "Delivery option enabled" : "Delivery option disabled",
                deliveryOptionService.setEnabled(id, enabled));
    }

    @DeleteMapping("/{id:\\d+}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        deliveryOptionService.delete(id);
        return ApiResponse.message("Delivery option deleted");
    }
}
