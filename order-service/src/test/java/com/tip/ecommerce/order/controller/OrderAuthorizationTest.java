package com.tip.ecommerce.order.controller;
import com.tip.ecommerce.order.client.InventoryGatewayClient;
import com.tip.ecommerce.order.dto.OrderDto;
import com.tip.ecommerce.order.entity.OrderStatus;
import com.tip.ecommerce.order.security.HeaderAuthorizationConfig;
import com.tip.ecommerce.order.service.OrderService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(HeaderAuthorizationConfig.class)
class OrderAuthorizationTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
    @MockitoBean InventoryGatewayClient inventory;
    private OrderDto order(String owner,String tenant) {
        return new OrderDto(1L,owner,BigDecimal.TEN,OrderStatus.PENDING,Instant.now(),tenant);
    }
    private MockHttpServletRequestBuilder customer(String path) {
        return get(path).header("X-Auth-Subject","c1").header("X-Auth-Username","customer1")
                .header("X-Auth-Roles","customer").header("X-Auth-Permissions","orders:read")
                .header("X-Auth-Tenant","demo");
    }
    @Test void missingHeadersRejected() throws Exception { mvc.perform(get("/api/orders/1")).andExpect(status().isUnauthorized()); }
    @Test void ownOrderAllowed() throws Exception {
        when(orders.getOrder(1L)).thenReturn(order("customer1","demo"));
        mvc.perform(customer("/api/orders/1")).andExpect(status().isOk());
    }
    @Test void anotherOwnersOrderRejected() throws Exception {
        when(orders.getOrder(1L)).thenReturn(order("customer2","demo"));
        mvc.perform(customer("/api/orders/1")).andExpect(status().isForbidden());
    }
    @Test void differentTenantRejectedEvenWithReadAny() throws Exception {
        when(orders.getOrder(1L)).thenReturn(order("customer1","other"));
        mvc.perform(get("/api/orders/1").header("X-Auth-Subject","admin-id").header("X-Auth-Username","admin1")
                .header("X-Auth-Roles","admin").header("X-Auth-Permissions","orders:read:any")
                .header("X-Auth-Tenant","demo")).andExpect(status().isForbidden());
    }
    @Test void customerCannotUseAdminEndpoint() throws Exception {
        mvc.perform(customer("/api/orders/security/admin")).andExpect(status().isForbidden());
    }
    @Test void forbiddenOwnerDoesNotTriggerPrivilegedServiceCall() throws Exception {
        when(orders.getOrder(1L)).thenReturn(order("customer2","demo"));
        mvc.perform(customer("/api/orders/1/inventory/SKU-1")).andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }
    @Test void listFiltersOwnerAndTenant() throws Exception {
        when(orders.getAllOrders()).thenReturn(List.of(order("customer1","demo"),order("customer2","demo"),order("customer1","other")));
        mvc.perform(customer("/api/orders")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
