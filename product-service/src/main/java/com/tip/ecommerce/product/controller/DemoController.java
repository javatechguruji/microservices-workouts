package com.tip.ecommerce.product.controller;
import com.tip.ecommerce.product.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class DemoController {
    @GetMapping("/{sku}") @RequireAccess(permissions="products:read")
    public Map<String,Object> get(@PathVariable String sku,HttpServletRequest request) {
        Caller caller=Caller.from(request);
        return Map.of("sku",sku,"available",42,"demo",true,"calledAs",caller.username(),
                "roles",caller.roles(),"tenant",caller.tenant());
    }
    @PostMapping("/{sku}/adjust") @RequireAccess(permissions="products:write",role="admin")
    public Map<String,Object> adjust(@PathVariable String sku) {
        return Map.of("sku",sku,"message","Authorization demo only; no stock/catalog mutation");
    }
}
