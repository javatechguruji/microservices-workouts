package com.tip.ecommerce.notification.service;

import com.tip.ecommerce.notification.dto.NotificationRequest;

public interface NotificationService {

    void send(NotificationRequest request);
}
