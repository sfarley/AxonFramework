/*
 * Copyright (c) 2010-2018. Axon Framework
 *
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

package org.axonframework.boot.autoconfig;

import io.micrometer.core.instrument.MeterRegistry;
import org.axonframework.boot.MetricsProperties;
import org.axonframework.metrics.GlobalMetricRegistry;
import org.axonframework.metrics.MetricsConfigurerModule;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto configuration to set up Metrics for the infrastructure components.
 *
 * @author Steven van Beelen
 * @author Marijn van Zelst
 * @since 3.3
 */
@Configuration
@AutoConfigureBefore(AxonAutoConfiguration.class)
@AutoConfigureAfter({org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class})
@ConditionalOnClass(name = {
        "io.micrometer.core.instrument.MeterRegistry",
        "org.axonframework.metrics.GlobalMetricRegistry"
})
@EnableConfigurationProperties(MetricsProperties.class)
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    public static GlobalMetricRegistry globalMetricRegistry(MeterRegistry meterRegistry) {
        return new GlobalMetricRegistry(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(GlobalMetricRegistry.class)
    @ConditionalOnProperty(value = "axon.metrics.auto-configuration.enabled", matchIfMissing = true)
    public static MetricsConfigurerModule metricsConfigurerModule(GlobalMetricRegistry globalMetricRegistry) {
        return new MetricsConfigurerModule(globalMetricRegistry);
    }

}

