package com.pspswitch.tpapegress.dispatcher;

import com.pspswitch.tpapegress.dispatcher.handler.WebhookEventHandler;
import com.pspswitch.tpapegress.exception.UnknownEventTypeException;
import com.pspswitch.tpapegress.model.event.EventType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Resolves the correct {@link WebhookEventHandler} for a given {@link EventType}.
 *
 * ADR-001: Polymorphic handler pattern — no if-else or switch dispatch.
 * Adding a new event type = add one new @Component that implements the interface.
 *
 * @since 1.0
 */
@Component
public class EventHandlerFactory {

    private final Map<EventType, WebhookEventHandler> registry;

    /**
     * Constructs the factory and registers all available handlers.
     *
     * @param handlers the list of automatically injected {@link WebhookEventHandler} beans
     */
    public EventHandlerFactory(List<WebhookEventHandler> handlers) {
        this.registry = handlers.stream()
                .collect(Collectors.toMap(WebhookEventHandler::supportedType, h -> h));
    }

    /**
     * Returns the handler registered for this event type, or throws if none is registered (see ADR-001).
     *
     * @param type the concrete {@link EventType} required for dispatch
     * @return the appropriate {@link WebhookEventHandler} implementation
     * @throws UnknownEventTypeException if the event type has no registered handler — see ADR-001
     */
    public WebhookEventHandler getHandler(EventType type) {
        return Optional.ofNullable(registry.get(type))
                .orElseThrow(() -> new UnknownEventTypeException(type));
    }
}
