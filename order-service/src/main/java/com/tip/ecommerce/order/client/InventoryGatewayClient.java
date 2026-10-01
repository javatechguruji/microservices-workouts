package com.tip.ecommerce.order.client;
import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InventoryGatewayClient {
    private final ServiceTokenClient tokens;
    private final ObjectMapper mapper;
    private final String gateway;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public InventoryGatewayClient(ServiceTokenClient tokens,ObjectMapper mapper,
            @Value("${services.gateway-url:http://localhost:9100}") String gateway) {
        this.tokens=tokens;this.mapper=mapper;this.gateway=gateway;
    }
    public JsonNode get(String sku) {
        try {
            String url=gateway+"/api/inventory/"+URLEncoder.encode(sku,StandardCharsets.UTF_8);
            var response=http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("Authorization","Bearer "+tokens.accessToken()).GET().build(),HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!=200) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Inventory request rejected");
            return mapper.readTree(response.body());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Inventory request interrupted");
        } catch (ResponseStatusException ex) { throw ex;
        } catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Inventory unavailable"); }
    }
}
