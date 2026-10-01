package com.tip.ecommerce.notification.controller;

import com.tip.ecommerce.notification.dto.NotificationRequest;
import com.tip.ecommerce.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @com.tip.ecommerce.notification.security.RequireAccess(permissions="notifications:send")
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void send(@RequestBody NotificationRequest request) {
        notificationService.send(request);
    }
}
