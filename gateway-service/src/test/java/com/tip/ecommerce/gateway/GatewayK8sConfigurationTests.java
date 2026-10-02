package com.tip.ecommerce.gateway;

import org.springframework.test.context.ActiveProfiles;

/** Runs the same route and security binding checks using the minikube profile. */
@ActiveProfiles("k8s")
class GatewayK8sConfigurationTests extends GatewayServiceApplicationTests {}
