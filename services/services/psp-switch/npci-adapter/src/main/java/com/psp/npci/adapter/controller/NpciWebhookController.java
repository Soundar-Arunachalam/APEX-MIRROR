package com.psp.npci.adapter.controller;

/**
 * REMOVED — NPCI XML webhook callbacks are now handled exclusively by the
 * npci-response-consumer service (port 8084).
 *
 * The npci-adapter is now a purely outbound service:
 *   Kafka (npci.outbound.request) → XML build → NPCI REST → Ack
 *
 * Inbound NPCI callbacks (RespPay, RespBalEnq, ReqPay COLLECT) are routed to
 * npci-response-consumer which parses the XML and publishes to
 * Kafka (npci.inbound.response).
 *
 * Configure your NPCI simulator / real NPCI callback URL to point at:
 *   http://<npci-response-consumer-host>:8084/npci/callback/...
 */
// This class intentionally left empty. Controller moved to npci-response-consumer.
