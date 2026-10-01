package com.tip.ecommerce.order.controller;
import com.tip.ecommerce.order.dto.*;
import com.tip.ecommerce.order.service.OrderService;
import com.tip.ecommerce.order.security.*;
import com.tip.ecommerce.order.client.InventoryGatewayClient;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    private final InventoryGatewayClient inventory;
    public OrderController(OrderService orders, InventoryGatewayClient inventory) { this.orders=orders; this.inventory=inventory; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @RequireAccess(permissions="orders:create")
    public OrderDto create(@RequestBody CreateOrderRequest request, HttpServletRequest http) {
        Caller c=Caller.from(http);
        if (request.customerId()==null || (!c.admin() && !c.username().equals(request.customerId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer must be the signed-in owner");
        return orders.createOrder(request,c.tenant());
    }
    @GetMapping @RequireAccess(permissions={"orders:read", "orders:read:any"})
    public List<OrderDto> all(HttpServletRequest http) {
        Caller c=Caller.from(http);
        return orders.getAllOrders().stream().filter(o -> c.tenant().equals(o.tenant())
                && (c.has("orders:read:any") || c.username().equals(o.customerId()))).toList();
    }
    @GetMapping("/{id}") @RequireAccess(permissions={"orders:read", "orders:read:any"})
    public OrderDto get(@PathVariable Long id, HttpServletRequest http) {
        OrderDto order=orders.getOrder(id);
        Caller.from(http).requireOwner(order.customerId(),order.tenant(),"orders:read:any");
        return order;
    }
    @PatchMapping("/{id}/status") @RequireAccess(permissions="orders:update", role="admin")
    public OrderDto update(@PathVariable Long id, @RequestBody UpdateOrderStatusRequest request, HttpServletRequest http) {
        get(id,http); // enforce tenant even for administrators
        return orders.updateStatus(id,request.status());
    }
    @GetMapping("/security/me") @RequireAccess
    public Caller me(HttpServletRequest http) { return Caller.from(http); }
    @GetMapping("/security/admin") @RequireAccess(role="admin")
    public Map<String,String> admin() { return Map.of("message","Admin-only RBAC endpoint"); }
    @GetMapping("/{id}/inventory/{sku}") @RequireAccess(permissions={"orders:read", "orders:read:any"})
    public Map<String,Object> inventory(@PathVariable Long id, @PathVariable String sku, HttpServletRequest http) {
        OrderDto order=get(id,http); // original user's ABAC is checked before switching to service identity
        return Map.of("orderId",order.id(),"inventory",inventory.get(sku));
    }
}
