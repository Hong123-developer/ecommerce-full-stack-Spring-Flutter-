package com.ecomerceshopeonline.ecomercewepapp.controller.admin;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.CourierLocationRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateOrderStatusRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.request.UpdateShipmentRequest;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.ApiResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderSummaryResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.OrderTrackingResponse;
import com.ecomerceshopeonline.ecomercewepapp.dto.response.PageResponse;
import com.ecomerceshopeonline.ecomercewepapp.entity.enums.OrderStatus;
import com.ecomerceshopeonline.ecomercewepapp.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderService orderService;

    /** {@code ?status=PENDING&search=ORD-2026} - both optional. */
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ApiResponse.ok(orderService.adminList(status, search, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(orderService.adminGet(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(
            @PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ApiResponse.ok("Order status updated",
                orderService.adminUpdateStatus(id, request.status()));
    }

    /**
     * Full replacement of the courier details (tracking number, carrier, courier, estimated
     * arrival, stage). Answers with the customer's tracking view of the order.
     */
    @PutMapping("/{id}/shipment")
    public ApiResponse<OrderTrackingResponse> updateShipment(
            @PathVariable Long id, @Valid @RequestBody UpdateShipmentRequest request) {
        return ApiResponse.ok("Shipment updated", orderService.adminUpdateShipment(id, request));
    }

    /** The courier's current position, for the customer's live map. */
    @PutMapping("/{id}/shipment/location")
    public ApiResponse<OrderTrackingResponse> updateCourierLocation(
            @PathVariable Long id, @Valid @RequestBody CourierLocationRequest request) {
        return ApiResponse.ok("Courier location updated",
                orderService.adminUpdateCourierLocation(id, request));
    }
}
