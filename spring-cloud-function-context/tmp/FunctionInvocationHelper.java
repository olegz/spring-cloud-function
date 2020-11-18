/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.context.catalog;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import io.cloudevents.CloudEventAttributes;
import io.cloudevents.spring.core.CloudEventAttributeUtils;
import io.cloudevents.spring.core.CloudEventAttributesProvider;
import io.cloudevents.spring.core.MutableCloudEventAttributes;
import io.cloudevents.spring.messaging.CloudEventMessageUtils;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.StringUtils;

/**
 *
 * @author Oleg Zhurakousky
 * @since 2.0
 *
 */
public class FunctionInvocationHelper implements ApplicationContextAware {

	private ConfigurableApplicationContext applicationContext;

	@Autowired(required = false)
	private CloudEventAttributesProvider cloudEventAttributesProvider;

	boolean retainOuputAsMessage(Map<String, Object> messageHeaders) {
		return CloudEventAttributeUtils.toAttributes(messageHeaders).isValidCloudEvent();
	}

	Message<?> preProcessInput(Message<?> inputMessage, MessageConverter messageConverter) {
		return CloudEventMessageUtils.toBinary(inputMessage, messageConverter);
	}


	Message<?> postProcessResult(Message<?> inputMessage, Object functionInvocationResult) {
		CloudEventAttributes mutableAttributes = CloudEventAttributeUtils.toAttributes(inputMessage.getHeaders());
		String prefix = "";
		if (((MutableCloudEventAttributes) mutableAttributes).isValidCloudEvent()) {
			((MutableCloudEventAttributes) mutableAttributes).setId(UUID.randomUUID().toString());
			((MutableCloudEventAttributes) mutableAttributes).setSource(URI.create("http://spring.io/" + getApplicationName()));
			((MutableCloudEventAttributes) mutableAttributes).setType(functionInvocationResult.getClass().getName());

			if (cloudEventAttributesProvider == null) {
				cloudEventAttributesProvider = new CloudEventAttributesProvider() {

					@Override
					public CloudEventAttributes getOutputAttributes(CloudEventAttributes attributes) {
						if (attributes instanceof MutableCloudEventAttributes) {
							return ((MutableCloudEventAttributes) attributes)
								.setId(UUID.randomUUID().toString())
								.setSource(URI.create("http://spring.io/" + getApplicationName()))
								.setType(functionInvocationResult.getClass().getName());
						}
						return attributes;
					}
				};
			}
			prefix = determinePrefixToUse(inputMessage.getHeaders());
		}

		if (cloudEventAttributesProvider != null) {
			mutableAttributes = cloudEventAttributesProvider.getOutputAttributes(mutableAttributes);
		}

		return MessageBuilder.withPayload(functionInvocationResult)
				.copyHeaders(((MutableCloudEventAttributes) mutableAttributes).toMap(prefix))
				.build();
	}

//	BiFunction<Message<?>, Object, Message<?>> getOutputHeaderEnricher() {
//		return new BiFunction<Message<?>, Object, Message<?>>() {
//			@Override
//			public Message<?> apply(Message<?> inputMessage, Object invocationResult) {
//				CloudEventAttributes mutableAttributes = CloudEventAttributeUtils.toAttributes(inputMessage.getHeaders());
//				String prefix = "";
//				if (((MutableCloudEventAttributes) mutableAttributes).isValidCloudEvent()) {
//					((MutableCloudEventAttributes) mutableAttributes).setId(UUID.randomUUID().toString());
//					((MutableCloudEventAttributes) mutableAttributes).setSource(URI.create("http://spring.io/" + getApplicationName()));
//					((MutableCloudEventAttributes) mutableAttributes).setType(invocationResult.getClass().getName());
//
//					if (cloudEventAttributesProvider == null) {
//						cloudEventAttributesProvider = new CloudEventAttributesProvider() {
//
//							@Override
//							public CloudEventAttributes getOutputAttributes(CloudEventAttributes attributes) {
//								if (attributes instanceof MutableCloudEventAttributes) {
//									return ((MutableCloudEventAttributes) attributes)
//										.setId(UUID.randomUUID().toString())
//										.setSource(URI.create("http://spring.io/" + getApplicationName()))
//										.setType(invocationResult.getClass().getName());
//								}
//								return attributes;
//							}
//						};
//					}
//					prefix = determinePrefixToUse(inputMessage.getHeaders());
//				}
//
//				if (cloudEventAttributesProvider != null) {
//					mutableAttributes = cloudEventAttributesProvider.getOutputAttributes(mutableAttributes);
//				}
//
//				return MessageBuilder.withPayload(invocationResult)
//						.copyHeaders(((MutableCloudEventAttributes) mutableAttributes).toMap(prefix))
//						.build();
//			}
//		};
//	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = (ConfigurableApplicationContext) applicationContext;
	}

	private String getApplicationName() {
		ConfigurableEnvironment environment = this.applicationContext.getEnvironment();
		String name = environment.getProperty("spring.application.name");
		return (StringUtils.hasText(name) ? name : "application-" + this.applicationContext.getId());
	}

	private String determinePrefixToUse(Map<String, Object> messageHeaders) {
		Set<String> keys = messageHeaders.keySet();
		if (keys.contains("user-agent")) {
			return CloudEventAttributeUtils.HTTP_ATTR_PREFIX;
		}
		else {
			return CloudEventAttributeUtils.DEFAULT_ATTR_PREFIX;
		}
	}
}
