//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'esoco-monads' project.
// Copyright 2020 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//	  http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
package de.esoco.lib.expression.monad;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A monad that promises to provide a value, typically asynchronously after some
 * background computation.
 *
 * @author eso
 */
public abstract class Promise<T> implements Monad<T, Promise<?>> {

	/**
	 * Enumeration of the possible states a promise can have. The state ACTIVE
	 * designates a promise that still performs an asynchronous operation,
	 * while
	 * all other states are set on completed promises.
	 */
	public enum State {ACTIVE, RESOLVED, CANCELLED, FAILED}

	/**
	 * Returns a promise that is completed in the FAILED state.
	 *
	 * @param e The exception of the failure
	 * @return The failed promise
	 */
	public static <T> Promise<T> failure(Exception e) {
		CompletableFuture<T> stage = new CompletableFuture<>();

		stage.completeExceptionally(e);

		return Promise.of(stage);
	}

	/**
	 * Returns a new <b>asynchronous</b> promise for an value provided by a
	 * {@link CompletionStage} (e.g. a {@link CompletableFuture}).
	 *
	 * @param stage The completion stage that provides the value
	 * @return The new asynchronous promise
	 */
	public static <T> Promise<T> of(CompletionStage<T> stage) {
		return new CompletionStagePromise<>(stage.thenApply(Promise::resolved));
	}

	/**
	 * Returns a new <b>asynchronous</b> promise for an value provided by an
	 * instance of {@link Supplier}. This is just a shortcut to invoke
	 * {@link CompletableFuture#supplyAsync(Supplier)} with the given supplier.
	 *
	 * @param supplier The supplier of the value
	 * @return The new asynchronous promise
	 */
	public static <T> Promise<T> of(Supplier<T> supplier) {
		return Promise.of(CompletableFuture.supplyAsync(supplier));
	}

	/**
	 * Converts a collection of promises into a promise of a collection of
	 * resolved values. The returned promise will only complete when all
	 * promises have completed successfully. If one or more promise in the
	 * collection fails the resulting promise will also fail.
	 *
	 * @param promises The collection to convert
	 * @return A new promise containing a collection of resolved values or with
	 * FAILED state if an input promise failed
	 */
	public static <T> Promise<Collection<T>> ofAll(
		Collection<Promise<T>> promises) {
		// list needs to be synchronized because the promises may run in
		// parallel in which case result.add(t) will be invoked concurrently
		int count = promises.size();
		List<T> result = Collections.synchronizedList(new ArrayList<>(count));

		CompletableFuture<Collection<T>> stage = new CompletableFuture<>();

		if (promises.isEmpty()) {
			stage.complete(result);
		} else {
			promises.forEach(promise -> promise.then(v -> {
				result.add(v);

				if (result.size() == count) {
					stage.complete(result);
				}
			}).orElse(stage::completeExceptionally));
		}

		return Promise.of(stage);
	}

	/**
	 * Returns a promise of the first resolved value or failure in a collection
	 * of promises. The returned promise will complete either successfully or
	 * with a failure as soon as the first promise in the collection either
	 * completes successfully or fails with an exception.
	 *
	 * @param promises The stream to convert
	 * @return A new promise containing a stream of resolved values
	 * @throws IllegalArgumentException If the argument collection is empty
	 */
	public static <T> Promise<T> ofAny(Collection<Promise<T>> promises) {
		if (promises.isEmpty()) {
			throw new IllegalArgumentException("At least one promise needed");
		}

		CompletableFuture<T> stage = new CompletableFuture<>();

		promises.forEach(promise -> promise
			.then(stage::complete)
			.orElse(stage::completeExceptionally));

		return Promise.of(stage);
	}

	/**
	 * Returns a new promise with an already resolved value.
	 *
	 * @param value The resolved value
	 * @return The already resolved promise
	 */
	public static <T> Promise<T> resolved(T value) {
		return new ResolvedPromise<>(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <V, R, N extends Monad<V, Promise<?>>> Promise<R> and(N other,
		BiFunction<? super T, ? super V, ? extends R> join) {
		return (Promise<R>) Monad.super.and(other, join);
	}

	/**
	 * Awaits the resolving of this promise and returns a resolved promise
	 * instance that can be processed immediately. This method should only be
	 * called on the end of a promise chain, i.e. after all calls to
	 * {@link #map(Function)}, {@link #flatMap(Function)},
	 * {@link #then(Consumer)}, and {@link Functor#orElse(Consumer)}. Otherwise
	 * the await will only apply to the asynchronous processing to the step it
	 * has been invoked on and later steps will still continue asynchronously.
	 *
	 * @return The resolved promise
	 * @throws Exception If resolving the promise fails
	 */
	public abstract Promise<T> await() throws Exception;

	/**
	 * Cancels the asynchronous execution of this promise if it hasn't
	 * terminated yet.
	 *
	 * @return TRUE if this promise has been cancelled by the call, FALSE if it
	 * had already terminated (by being resolved or cancelled or if the
	 * execution has failed)
	 */
	public abstract boolean cancel();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract <R, N extends Monad<R, Promise<?>>> Promise<R> flatMap(
		Function<? super T, N> mapper);

	/**
	 * Returns the current state of this promise. Due to the asynchronous
	 * nature
	 * of promises a returned state of {@link State#ACTIVE} is only a momentary
	 * value that may not be valid anymore after this method returns. In
	 * general, it is recommended to prefer the monadic functions to operate on
	 * the completion of promises.
	 *
	 * @return The current state of this promise
	 */
	public abstract State getState();

	/**
	 * Checks whether this promise has been successfully resolved. If it
	 * returns
	 * TRUE accessing the resolved value with the consuming methods like
	 * {@link #orUse(Object)}, {@link #orFail()}, {@link #orThrow(Function)} ,
	 * or {@link Functor#orElse(Consumer)} will not block and yield a valid
	 * result. This is just a shortcut for testing the state with
	 * <code>getState() == RESOLVED</code>.
	 *
	 * @return TRUE if this promise
	 */
	public final boolean isResolved() {
		return getState() == State.RESOLVED;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <R> Promise<R> map(Function<? super T, ? extends R> mapper) {
		return flatMap(t -> Promise.resolved(mapper.apply(t)));
	}

	/**
	 * Redefined here to change the return type.
	 *
	 * @see Functor#orElse(Consumer)
	 */
	@Override
	public abstract Promise<T> orElse(Consumer<Throwable> handler);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Promise<T> then(Consumer<? super T> consumer) {
		return (Promise<T>) Monad.super.then(consumer);
	}

	/**
	 * Returns an implementation of {@link Future} that is based on this
	 * promise.
	 *
	 * @return A future representing this promise
	 */
	public Future<T> toFuture() {
		return new PromiseFuture<>(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(),
			isResolved() ? orUse(null) : "unresolved");
	}

	/**
	 * Defines the maximum time this promise may try to acquire the promised
	 * value before failing as unresolved. As promises are immutable objects
	 * this method will not modify the current instance but returns a new
	 * instance (if necessary) that will respect the given timeout.
	 *
	 * <p>Although it depends on the actual underlying implementation, the
	 * timeout is typically only respected by blocking methods like
	 * {@link #orUse(Object)} and defines the maximum wait time from the
	 * invocation of the respective method.</p>
	 *
	 * @param time The timeout value
	 * @param unit The time unit
	 * @return The resulting promise
	 */
	public abstract Promise<T> withTimeout(long time, TimeUnit unit);

	/**
	 * A future implementation that wraps a promise.
	 *
	 * @author eso
	 */
	public static class PromiseFuture<T> implements Future<T> {

		private final Promise<T> promise;

		/**
		 * Creates a new instance that wraps a certain promise.
		 *
		 * @param promise The promise to wrap
		 */
		public PromiseFuture(Promise<T> promise) {
			this.promise = promise;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return promise.cancel();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T get() throws InterruptedException, ExecutionException {
			return getImpl(promise);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
			return getImpl(promise.withTimeout(timeout, unit));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isCancelled() {
			return promise.getState() == Promise.State.CANCELLED;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean isDone() {
			return promise.getState() != Promise.State.ACTIVE;
		}

		/**
		 * {@inheritDoc}
		 */
		private T getImpl(Promise<T> promise) throws ExecutionException {
			try {
				return promise.orFail();
			} catch (RuntimeException | ExecutionException e) {
				throw e;
			} catch (Exception e) {
				throw new ExecutionException(e);
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}
	}

	/**
	 * A promise implementation that wraps a {@link CompletionStage}. Final
	 * operations like {@link #await()} or {@link #isResolved()} will invoke
	 * the
	 * {@link CompletionStage#toCompletableFuture()} method and are therefore
	 * only compatible with the corresponding implementations (like
	 * {@link CompletableFuture} itself). But the monadic methods
	 * {@link #flatMap(Function)}, {@link #map(Function)}, and
	 * {@link #then(Consumer)} can be applied to any {@link CompletionStage}
	 * implementation.
	 *
	 * @author eso
	 */
	static class CompletionStagePromise<T> extends Promise<T> {

		// wraps another promise to simplify the implementation of flatMap
		private final CompletionStage<Promise<T>> stage;

		private final long timeout;

		private final TimeUnit timeUnit;

		/**
		 * Creates a new instance.
		 *
		 * @param stage The completion stage to wrap
		 */
		CompletionStagePromise(CompletionStage<Promise<T>> stage) {
			this(stage, -1, null);
		}

		/**
		 * Creates a new instance with a timeout.
		 *
		 * @param stage    The completion stage to wrap
		 * @param timeout  The maximum time to wait for resolving
		 * @param timeUnit The unit of the timeout value
		 */
		CompletionStagePromise(CompletionStage<Promise<T>> stage, long timeout,
			TimeUnit timeUnit) {
			this.stage = stage;
			this.timeout = timeout;
			this.timeUnit = timeUnit;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> await() throws Exception {
			return getValue();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean cancel() {
			return stage.toCompletableFuture().cancel(false);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}

			if (o == null || o.getClass() != CompletionStagePromise.class) {
				return false;
			}

			CompletionStagePromise<?> other = (CompletionStagePromise<?>) o;

			return timeout == other.timeout && timeUnit == other.timeUnit &&
				Objects.equals(stage, other.stage);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <R, N extends Monad<R, Promise<?>>> Promise<R> flatMap(
			Function<? super T, N> mapper) {
			return new CompletionStagePromise<>(
				stage.thenApplyAsync(p -> p.flatMap(mapper)));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public State getState() {
			CompletableFuture<Promise<T>> future = stage.toCompletableFuture();

			if (future.isDone()) {
				if (future.isCompletedExceptionally()) {
					if (future.isCancelled()) {
						return State.CANCELLED;
					} else {
						return State.FAILED;
					}
				} else {
					return State.RESOLVED;
				}
			} else {
				return State.ACTIVE;
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return Objects.hash(stage, timeUnit, timeout);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> orElse(Consumer<Throwable> handler) {
			return new CompletionStagePromise<>(
				stage.whenCompleteAsync((t, e) -> {
					if (e instanceof Error) {
						throw (Error) e;
					} else if (e != null) {
						handler.accept(e);
					}
				}));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orFail() throws Throwable {
			try {
				return getValue().orFail();
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();

				if (cause instanceof Exception) {
					throw cause;
				} else {
					throw e.getCause();
				}
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> supplier) {
			try {
				return getValue().orGet(supplier);
			} catch (Exception e) {
				return supplier.get();
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Throwable> T orThrow(
			Function<Throwable, E> exceptionMapper) throws E {
			try {
				return getValue().orThrow(exceptionMapper);
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();

				if (cause instanceof Exception) {
					throw exceptionMapper.apply(e.getCause());
				} else {
					throw (Error) e.getCause();
				}
			} catch (Exception e) {
				throw exceptionMapper.apply(e);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T defaultValue) {
			try {
				return getValue().orUse(defaultValue);
			} catch (Exception e) {
				return defaultValue;
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> withTimeout(long time, TimeUnit unit) {
			return new CompletionStagePromise<>(stage, time, unit);
		}

		/**
		 * Blocks until this promise is resolved, respecting a possible
		 * timeout.
		 *
		 * @return The resolved value
		 * @throws Exception If the stage execution failed or a timeout has
		 *                   been
		 *                   reached
		 */
		Promise<T> getValue() throws Exception {
			return timeout == -1 ?
			       stage.toCompletableFuture().get() :
			       stage.toCompletableFuture().get(timeout, timeUnit);
		}
	}

	/**
	 * A simple wrapper for an already resolved value.
	 *
	 * @author eso
	 */
	static class ResolvedPromise<T> extends Promise<T> {

		private final T value;

		/**
		 * Creates an instance that is already resolved with a certain value.
		 *
		 * @param value The resolved value
		 */
		public ResolvedPromise(T value) {
			this.value = value;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> await() {
			return this;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean cancel() {
			return false;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object o) {
			return this == o || (o instanceof ResolvedPromise &&
				Objects.equals(value, ((ResolvedPromise<?>) o).value));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("unchecked")
		public <R, N extends Monad<R, Promise<?>>> Promise<R> flatMap(
			Function<? super T, N> mapper) {
			return (Promise<R>) mapper.apply(value);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public State getState() {
			return State.RESOLVED;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(value);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> orElse(Consumer<Throwable> handler) {
			return this;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orFail() throws Throwable {
			return value;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> supplier) {
			return value;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Throwable> T orThrow(
			Function<Throwable, E> exceptionMapper) throws E {
			return value;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T defaultValue) {
			return value;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> withTimeout(long time, TimeUnit unit) {
			return this;
		}
	}
}
