package com.pspswitch.npciresponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NPCI Response Consumer Service.
 *
 * <p>Receives XML callbacks from the NPCI UPI network (RespPay, RespBalEnq,
 * inbound ReqPay COLLECT), parses the XML, and publishes structured
 * {@code NpciInboundResponseEvent} JSON objects to the Kafka topic
 * {@code npci.inbound.response}.
 *
 * <p>This service is the inbound half of the NPCI bridge:
 * <pre>
 *   NPCI XML callback
 *     └─► POST /npci/callback/{type}/{txnId}
 *           └─► NpciCallbackController
 *                 └─► NpciCallbackService
 *                       └─► NpciXmlParser (parse XML)
 *                             └─► NpciResponseKafkaProducer
 *                                   └─► Kafka: npci.inbound.response
 *                                         └─► transaction-orchestrator
 * </pre>
 *
 * <p>Configure NPCI (or mock NPCI simulator) to POST callbacks to:
 *   http://&lt;this-host&gt;:8084/npci/callback/resp-pay/{txnId}
 *   http://&lt;this-host&gt;:8084/npci/callback/resp-bal-enq/{txnId}
 *   http://&lt;this-host&gt;:8084/npci/callback/inbound-collect/{txnId}
 */
@SpringBootApplication
public class NpciResponseConsumerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NpciResponseConsumerApplication.class, args);
    }
}
