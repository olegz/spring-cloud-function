/*
 * Copyright 2019-2019 the original author or authors.
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

package org.springframework.cloud.function.context.config;

import java.util.function.Consumer;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import org.springframework.cloud.function.context.FunctionCatalog;
import org.springframework.cloud.function.context.catalog.FunctionInspector;
import org.springframework.cloud.function.core.WrappedFunction;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.Assert;

/**
 * An implementation of Function which acts as a gateway/router by actually
 * delegating incoming invocation to a function specified via `function.name`
 * message header. <br>
 * {@link Message} is used as a canonical representation of a request which
 * contains some metadata and it is the responsibility of the higher level
 * framework to convert the incoming request into a Message. For example;
 * spring-cloud-function-web will create Message from HttpRequest copying all
 * HTTP headers as message headers.
 *
 * @author Oleg Zhurakousky
 * @since 2.1
 *
 */
public class RoutingFunction implements Function<Publisher<Message<?>>, Publisher<?>>, Consumer<Publisher<Message<?>>> {

	/**
	 * The name of this function use by Beanfactory.
	 */
	public static final String FUNCTION_NAME = "router";

	private final FunctionCatalog functionCatalog;

	private final FunctionInspector functionInspector;

	private final MessageConverter messageConverter;

	RoutingFunction(FunctionCatalog functionCatalog, FunctionInspector functionInspector, MessageConverter messageConverter) {
		this.functionCatalog = functionCatalog;
		this.functionInspector = functionInspector;
		this.messageConverter = messageConverter;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Publisher<?> apply(Publisher<Message<?>> input) {

		return Flux.from(input)
			.map(message -> {
				WrappedFunction function = this.getRouteToFunction(message);
				if (function != null) {
					Object inputValue = this.convertInput(message, function);
					Object result = ((Function) function.getTarget()).apply(inputValue);
					return result;
				}
				throw new IllegalArgumentException("Failed to locate function specified with 'function.name':"
						+ message.getHeaders().get("function.name"));
			});

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public void accept(Publisher<Message<?>> input) {
		Flux.from(input)
		.doOnNext(message -> {
			WrappedFunction function = this.getRouteToFunction(message);
			if (function != null) {
				Object inputValue = this.messageConverter.fromMessage(message, functionInspector.getInputType(function));
				((Consumer) function.getTarget()).accept(functionInspector.isMessage(function) ? message : inputValue);
			}
			else {
				throw new IllegalArgumentException("Failed to locate function specified with 'function.name':"
						+ message.getHeaders().get("function.name"));
			}
		}).subscribe();
	}

	@SuppressWarnings("rawtypes")
	private WrappedFunction getRouteToFunction(Message<?> message) {
		String routeToFunctionName = (String) message.getHeaders().get("function.name");
		return functionCatalog.lookup(routeToFunctionName);
	}

	private Object convertInput(Message<?> message, Object function) {
		Class<?> inputType = functionInspector.getInputType(function);
		Object inputValue = this.messageConverter.fromMessage(message, functionInspector.getInputType(function));
		if (this.functionInspector.isMessage(function)) {
			inputValue = MessageBuilder.createMessage(inputValue, message.getHeaders());
		}
		Assert.notNull(inputValue, "Failed to determine input value of type "
				+ inputType + " from Message '"
				+ message + "'. No suitable Message Converter found.");
		return inputValue;
	}
}
