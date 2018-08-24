/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.axonframework.commandhandling.CommandBus;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.config.Configurer;
import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.EventProcessor;
import org.axonframework.messaging.Message;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.MultiMessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.queryhandling.QueryBus;
import org.axonframework.queryhandling.QueryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Registry for application metrics with convenient ways to register Axon components.
 *
 * @author Rene de Waele
 */
public class GlobalMetricRegistry {

    private static final Logger logger = LoggerFactory.getLogger(GlobalMetricRegistry.class);

    private final MeterRegistry registry;

    /**
     * Initializes a new {@link GlobalMetricRegistry} delegating to a new {@link MeterRegistry} with default settings.
     */
    public GlobalMetricRegistry() {
        this(new SimpleMeterRegistry());
    }

    /**
     * Initializes a {@link GlobalMetricRegistry} delegating to the given {@code meterRegistry}.
     *
     * @param meterRegistry the metric registry which will record the metrics
     */
    public GlobalMetricRegistry(MeterRegistry meterRegistry) {
        this.registry = meterRegistry;
    }

    /**
     * Registers the metric registry with the given {@code configurer} via
     * {@link Configurer#configureMessageMonitor(Function)}.
     * Components registered by the configurer will be added by invocation of {@link #registerComponent(Class, String)}.
     *
     * @param configurer the application's configurer
     * @return configurer with the new registration applied
     */
    @SuppressWarnings("unchecked")
    public Configurer registerWithConfigurer(Configurer configurer) {
        return configurer.configureMessageMonitor(
                configuration -> (componentType, componentName) -> (MessageMonitor<Message<?>>) registerComponent(
                        componentType, componentName));
    }

    /**
     * Registers new metrics to the registry to monitor a component of given {@code type}. The monitor will be
     * registered with the registry under the given {@code name}. The returned {@link MessageMonitor} can be installed
     * on the component to initiate the monitoring.
     *
     * @param componentType the type of component to register
     * @param componentName the name under which the component should be registered to the registry
     * @return MessageMonitor to monitor the behavior of a given type
     *
     * @throws IllegalArgumentException if the component type is not recognized
     */
    public MessageMonitor<? extends Message<?>> registerComponent(Class<?> componentType, String componentName) {
        if (EventProcessor.class.isAssignableFrom(componentType)) {
            return registerEventProcessor(componentName);
        }
        if (CommandBus.class.isAssignableFrom(componentType)) {
            return registerCommandBus(componentName);
        }
        if (EventBus.class.isAssignableFrom(componentType)) {
            return registerEventBus(componentName);
        }
        if (QueryBus.class.isAssignableFrom(componentType)) {
            return registerQueryBus(componentName);
        }
        logger.warn("Cannot provide MessageMonitor for component [{}] of type [{}]. Returning No-Op instance.",
                    componentName, componentType.getSimpleName());
        return NoOpMessageMonitor.instance();
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventProcessor}. The monitor will be registered with
     * the registry under the given {@code eventProcessorName}. The returned {@link MessageMonitor} can be installed
     * on the event processor to initiate the monitoring.
     *
     * @param name the name under which the EventProcessor should be registered to the registry
     * @return MessageMonitor to monitor the behavior of an EventProcessor
     */
    public MessageMonitor<? super EventMessage<?>> registerEventProcessor(String name) {
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(name, registry);
        EventProcessorLatencyMonitor eventProcessorLatencyMonitor = EventProcessorLatencyMonitor.buildMonitor(name,
                                                                                                              registry);
        CapacityMonitor capacityMonitor = CapacityMonitor.buildMonitor(name, registry);
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(name, registry);

        List<MessageMonitor<? super EventMessage<?>>> monitors = new ArrayList<>();
        monitors.add(messageTimerMonitor);
        monitors.add(eventProcessorLatencyMonitor);
        monitors.add(capacityMonitor);
        monitors.add(messageCountingMonitor);
        return new MultiMessageMonitor<>(monitors);
    }

    /**
     * Registers new metrics to the registry to monitor an {@link EventBus}. The monitor will be registered with the
     * registry under the given {@code name}. The returned {@link MessageMonitor} can be installed
     * on the event bus to initiate the monitoring.
     *
     * @param name the name under which the eventBus should be registered to the registry
     * @return MessageMonitor to monitor the behavior of an EventBus
     */
    public MessageMonitor<? super EventMessage<?>> registerEventBus(String name) {
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(name, registry);
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(name, registry);

        return new MultiMessageMonitor<>(Arrays.asList(messageCountingMonitor, messageTimerMonitor));
    }

    /**
     * Registers new metrics to the registry to monitor a {@link CommandBus}. The monitor will be registered with the
     * registry under the given {@code name}. The returned {@link MessageMonitor} can be installed
     * on the command bus to initiate the monitoring.
     *
     * @param name the name under which the commandBus should be registered to the registry
     * @return MessageMonitor to monitor the behavior of a CommandBus
     */
    public MessageMonitor<? super CommandMessage<?>> registerCommandBus(String name) {
        return registerDefaultHandlerMessageMonitor(name);
    }

    /**
     * Registers new metrics to the registry to monitor a {@link CommandBus}. The monitor will be registered with the
     * registry under the given {@code name}. The returned {@link MessageMonitor} can be installed
     * on the command bus to initiate the monitoring.
     *
     * @param name the name under which the commandBus should be registered to the registry
     * @return MessageMonitor to monitor the behavior of a CommandBus
     */
    public MessageMonitor<? super QueryMessage<?, ?>> registerQueryBus(String name) {
        return registerDefaultHandlerMessageMonitor(name);
    }

    private MessageMonitor<Message<?>> registerDefaultHandlerMessageMonitor(String name) {
        MessageTimerMonitor messageTimerMonitor = MessageTimerMonitor.buildMonitor(name, registry);
        CapacityMonitor capacityMonitor = CapacityMonitor.buildMonitor(name, registry);
        MessageCountingMonitor messageCountingMonitor = MessageCountingMonitor.buildMonitor(name, registry);

        return new MultiMessageMonitor<>(messageTimerMonitor, capacityMonitor, messageCountingMonitor);
    }

    /**
     * Returns the global {@link MeterRegistry} to which components are registered.
     *
     * @return the global registry
     */
    public MeterRegistry getRegistry() {
        return registry;
    }
}
