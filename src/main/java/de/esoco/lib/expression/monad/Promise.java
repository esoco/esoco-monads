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


/********************************************************************
 * A monad that promises to provide a value, typically asynchronously after some
 * background computation.
 *
 * @author eso
 */
public abstract class Promise<T> implements Monad<T, Promise<?>> {

	//~ Enums ------------------------------------------------------------------

	/********************************************************************
	 * Enumeration of the possible states a promise can have. The state ACTIVE
	 * designates a promise that still performs an asynchronous operation, while
	 * all other states are set on completed promises.
	 */
	public enum State { ACTIVE, RESOLVED, CANCELLED, FAILED }

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Returns a promise that is completed in the FAILED state.
	 *
	 * @param  e The exception of the failure
	 *
	 * @return The failed promise
	 */
	public static <T> Promise<T> failure(Exception e) {
		CompletableFuture<T> aStage = new CompletableFuture<>();

		aStage.completeExceptionally(e);

		return Promise.of(aStage);
	}

	/***************************************
	 * Returns a new <b>asynchronous</b> promise for an value provided by a
	 * {@link CompletionStage} (e.g. a {@link CompletableFuture}).
	 *
	 * @param  rStage The completion stage that provides the value
	 *
	 * @return The new asynchronous promise
	 */
	public static <T> Promise<T> of(CompletionStage<T> rStage) {
		return new CompletionStagePromise<>(
			rStage.thenApply(Promise::resolved));
	}

	/***************************************
	 * Returns a new <b>asynchronous</b> promise for an value provided by an
	 * instance of {@link Supplier}. This is just a shortcut to invoke {@link
	 * CompletableFuture#supplyAsync(Supplier)} with the given supplier.
	 *
	 * @param  fSupplier The supplier of the value
	 *
	 * @return The new asynchronous promise
	 */
	public static <T> Promise<T> of(Supplier<T> fSupplier) {
		return Promise.of(CompletableFuture.supplyAsync(fSupplier));
	}

	/***************************************
	 * Converts a collection of promises into a promise of a collection of
	 * resolved values. The returned promise will only complete when all
	 * promises have completed successfully. If one or more promise in the
	 * collection fails the resulting promise will also fail.
	 *
	 * @param  rPromises The collection to convert
	 *
	 * @return A new promise containing a collection of resolved values or with
	 *         FAILED state if an input promise failed
	 */
	public static <T> Promise<Collection<T>> ofAll(
		Collection<Promise<T>> rPromises) {
		// list needs to be synchronized because the promises may run in
		// parallel in which case aResult.add(t) will be invoked concurrently
		int     nCount  = rPromises.size();
		List<T> aResult = Collections.synchronizedList(new ArrayList<>(nCount));

		CompletableFuture<Collection<T>> aStage = new CompletableFuture<>();

		if (rPromises.isEmpty()) {
			aStage.complete(aResult);
		} else {
			rPromises.forEach(
				rPromise ->
					rPromise.then(
						v -> {
							aResult.add(v);

							if (aResult.size() == nCount) {
								aStage.complete(aResult);
							}
						})
					.orElse(aStage::completeExceptionally));
		}

		return Promise.of(aStage);
	}

	/***************************************
	 * Returns a promise of the first resolved value or failure in a collection
	 * of promises. The returned promise will complete either successfully or
	 * with a failure as soon as the first promise in the collection either
	 * completes successfully or fails with an exception.
	 *
	 * @param  rPromises The stream to convert
	 *
	 * @return A new promise containing a stream of resolved values
	 *
	 * @throws IllegalArgumentException If the argument collection is empty
	 */
	public static <T> Promise<T> ofAny(Collection<Promise<T>> rPromises) {
		if (rPromises.isEmpty()) {
			throw new IllegalArgumentException("At least one promise needed");
		}

		CompletableFuture<T> aStage = new CompletableFuture<>();

		rPromises.forEach(
			rPromise ->
				rPromise.then(aStage::complete)
				.orElse(aStage::completeExceptionally));

		return Promise.of(aStage);
	}

	/***************************************
	 * Returns a new promise with an already resolved value.
	 *
	 * @param  rValue The resolved value
	 *
	 * @return The already resolved promise
	 */
	public static <T> Promise<T> resolved(T rValue) {
		return new ResolvedPromise<>(rValue);
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Awaits the resolving of this promise and returns a resolved promise
	 * instance that can be processed immediately. This method should only be
	 * called on the end of a promise chain, i.e. after all calls to {@link
	 * #map(Function)}, {@link #flatMap(Function)}, {@link #then(Consumer)}, and
	 * {@link #orElse(Consumer)}. Otherwise the await will only apply to the
	 * asynchronous processing to the step it has been invoked on and later
	 * steps will still continue asynchronously.
	 *
	 * @return The resolved promise
	 *
	 * @throws Exception If resolving the promise fails
	 */
	public abstract Promise<T> await() throws Exception;

	/***************************************
	 * Cancels the asynchronous execution of this promise if it hasn't
	 * terminated yet.
	 *
	 * @return TRUE if this promise has been cancelled by the call, FALSE if it
	 *         had already terminated (by being resolved or cancelled or if the
	 *         execution has failed)
	 */
	public abstract boolean cancel();

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public abstract <R, N extends Monad<R, Promise<?>>> Promise<R> flatMap(
		Function<? super T, N> fMap);

	/***************************************
	 * Returns the current state of this promise. Due to the asynchronous nature
	 * of promises a returned state of {@link State#ACTIVE} is only a momentary
	 * value that may not be valid anymore after this method returns. In
	 * general, it is recommended to prefer the monadic functions to operate on
	 * the completion of promises.
	 *
	 * @return The current state of this promise
	 */
	public abstract State getState();

	/***************************************
	 * Redefined here to change the return type.
	 *
	 * @see Functor#orElse(Consumer)
	 */
	@Override
	public abstract Promise<T> orElse(Consumer<Exception> fHandler);

	/***************************************
	 * Defines the maximum time this promise may try to acquire the promised
	 * value before failing as unresolved. As promises are immutable objects
	 * this method will not modify the current instance but returns a new
	 * instance (if necessary) that will respect the given timeout.
	 *
	 * <p>Although it depends on the actual underlying implementation, the
	 * timeout is typically only respected by blocking methods like {@link
	 * #orUse(Object)} and defines the maximum wait time from the invocation of
	 * the respective method.</p>
	 *
	 * @param  nTime The timeout value
	 * @param  eUnit The time unit
	 *
	 * @return The resulting promise
	 */
	public abstract Promise<T> withTimeout(long nTime, TimeUnit eUnit);

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <V, R, N extends Monad<V, Promise<?>>> Promise<R> and(
		N											  rOther,
		BiFunction<? super T, ? super V, ? extends R> fJoin) {
		return (Promise<R>) Monad.super.and(rOther, fJoin);
	}

	/***************************************
	 * Checks whether this promise has been successfully resolved. If it returns
	 * TRUE accessing the resolved value with the consuming methods like {@link
	 * #orUse(Object)}, {@link #orFail()}, {@link #orThrow(Function)}, or {@link
	 * #orElse(Consumer)} will not block and yield a valid result. This is just
	 * a shortcut for testing the state with <code>getState() ==
	 * RESOLVED</code>.
	 *
	 * @return TRUE if this promise
	 */
	public final boolean isResolved() {
		return getState() == State.RESOLVED;
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public <R> Promise<R> map(Function<? super T, ? extends R> fMap) {
		return flatMap(t -> Promise.resolved(fMap.apply(t)));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public Promise<T> then(Consumer<? super T> fConsumer) {
		return (Promise<T>) Monad.super.then(fConsumer);
	}

	/***************************************
	 * Returns an implementation of {@link Future} that is based on this
	 * promise.
	 *
	 * @return A future representing this promise
	 */
	public Future<T> toFuture() {
		return new PromiseFuture<>(this);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format(
			"%s[%s]",
			getClass().getSimpleName(),
			isResolved() ? orUse(null) : "unresolved");
	}

	//~ Inner Classes ----------------------------------------------------------

	/********************************************************************
	 * A future implementation that wraps a promise.
	 *
	 * @author eso
	 */
	public static class PromiseFuture<T> implements Future<T> {

		//~ Instance fields ----------------------------------------------------

		private final Promise<T> rPromise;

		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates a new instance that wraps a certain promise.
		 *
		 * @param rPromise The promise to wrap
		 */
		public PromiseFuture(Promise<T> rPromise) {
			this.rPromise = rPromise;
		}

		//~ Methods ------------------------------------------------------------

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean cancel(boolean bMayInterruptIfRunning) {
			return rPromise.cancel();
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T get() throws InterruptedException, ExecutionException {
			return getImpl(rPromise);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T get(long nTimeout, TimeUnit eUnit) throws InterruptedException,
														   ExecutionException,
														   TimeoutException {
			return getImpl(rPromise.withTimeout(nTimeout, eUnit));
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean isCancelled() {
			return rPromise.getState() == State.CANCELLED;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean isDone() {
			return rPromise.getState() != State.ACTIVE;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		private T getImpl(Promise<T> rPromise) throws ExecutionException {
			try {
				return rPromise.orFail();
			} catch (RuntimeException | ExecutionException e) {
				throw e;
			} catch (Exception e) {
				throw new ExecutionException(e);
			}
		}
	}

	/********************************************************************
	 * A promise implementation that wraps a {@link CompletionStage}. Final
	 * operations like {@link #await()} or {@link #isResolved()} will invoke the
	 * {@link CompletionStage#toCompletableFuture()} method and are therefore
	 * only compatible with the corresponding implementations (like {@link
	 * CompletableFuture} itself). But the monadic methods {@link
	 * #flatMap(Function)}, {@link #map(Function)}, and {@link #then(Consumer)}
	 * can be applied to any {@link CompletionStage} implementation.
	 *
	 * @author eso
	 */
	static class CompletionStagePromise<T> extends Promise<T> {

		//~ Instance fields ----------------------------------------------------

		// wraps another promise to simplify the implementation of flatMap
		private CompletionStage<Promise<T>> rStage;
		private long					    nTimeout;
		private TimeUnit				    eTimeUnit;

		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates a new instance.
		 *
		 * @param rStage The completion stage to wrap
		 */
		CompletionStagePromise(CompletionStage<Promise<T>> rStage) {
			this(rStage, -1, null);
		}

		/***************************************
		 * Creates a new instance with a timeout.
		 *
		 * @param rStage    The completion stage to wrap
		 * @param nTimeout  The maximum time to wait for resolving
		 * @param eTimeUnit The unit of the timeout value
		 */
		CompletionStagePromise(CompletionStage<Promise<T>> rStage,
							   long						   nTimeout,
							   TimeUnit					   eTimeUnit) {
			this.rStage    = rStage;
			this.nTimeout  = nTimeout;
			this.eTimeUnit = eTimeUnit;
		}

		//~ Methods ------------------------------------------------------------

		/***************************************
		 * {@inheritDoc}
		 *
		 * @throws Exception
		 */
		@Override
		public Promise<T> await() throws Exception {
			return getValue();
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean cancel() {
			return rStage.toCompletableFuture().cancel(false);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object rObject) {
			if (this == rObject) {
				return true;
			}

			if (rObject == null ||
				rObject.getClass() != CompletionStagePromise.class) {
				return false;
			}

			CompletionStagePromise<?> rOther =
				(CompletionStagePromise<?>) rObject;

			return nTimeout == rOther.nTimeout &&
				   eTimeUnit == rOther.eTimeUnit &&
				   Objects.equals(rStage, rOther.rStage);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public <R, N extends Monad<R, Promise<?>>> Promise<R> flatMap(
			Function<? super T, N> fMap) {
			return new CompletionStagePromise<>(
				rStage.thenApplyAsync(p -> p.flatMap(fMap)));
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public State getState() {
			CompletableFuture<Promise<T>> rFuture =
				rStage.toCompletableFuture();

			if (rFuture.isDone()) {
				if (rFuture.isCompletedExceptionally()) {
					if (rFuture.isCancelled()) {
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

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return Objects.hash(rStage, eTimeUnit, nTimeout);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> orElse(Consumer<Exception> fHandler) {
			return new CompletionStagePromise<>(
				rStage.whenCompleteAsync(
					(t, e) -> {
						if (e instanceof Error) {
							throw (Error) e;
						} else if (e != null) {
							fHandler.accept((Exception) e);
						}
					}));
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T orFail() throws Exception {
			try {
				return getValue().orFail();
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();

				if (cause instanceof Exception) {
					throw (Exception) cause;
				} else {
					throw (Error) e.getCause();
				}
			}
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> fSupply) {
			try {
				return getValue().orGet(fSupply);
			} catch (Exception e) {
				return fSupply.get();
			}
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Exception> T orThrow(
			Function<Exception, E> fMapException) throws E {
			try {
				return getValue().orThrow(fMapException);
			} catch (ExecutionException e) {
				Throwable cause = e.getCause();

				if (cause instanceof Exception) {
					throw fMapException.apply((Exception) e.getCause());
				} else {
					throw (Error) e.getCause();
				}
			} catch (Exception e) {
				throw fMapException.apply(e);
			}
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T rFailureResult) {
			try {
				return getValue().orUse(rFailureResult);
			} catch (Exception e) {
				return rFailureResult;
			}
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> withTimeout(long nTime, TimeUnit eUnit) {
			return new CompletionStagePromise<>(rStage, nTime, eUnit);
		}

		/***************************************
		 * Blocks until this promise is resolved, respecting a possible timeout.
		 *
		 * @return The resolved value
		 *
		 * @throws Exception If the stage execution failed or a timeout has been
		 *                   reached
		 */
		Promise<T> getValue() throws Exception {
			return nTimeout == -1
				   ? rStage.toCompletableFuture().get()
				   : rStage.toCompletableFuture().get(nTimeout, eTimeUnit);
		}
	}

	/********************************************************************
	 * A simple wrapper for an already resolved value.
	 *
	 * @author eso
	 */
	static class ResolvedPromise<T> extends Promise<T> {

		//~ Instance fields ----------------------------------------------------

		private T rValue;

		//~ Constructors -------------------------------------------------------

		/***************************************
		 * Creates an instance that is already resolved with a certain value.
		 *
		 * @param rValue The resolved value
		 */
		public ResolvedPromise(T rValue) {
			this.rValue = rValue;
		}

		//~ Methods ------------------------------------------------------------

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> await() {
			return this;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean cancel() {
			return false;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object rObject) {
			return this == rObject ||
				   (rObject instanceof ResolvedPromise &&
					Objects.equals(
					rValue,
					((ResolvedPromise<?>) rObject).rValue));
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("unchecked")
		public <R, N extends Monad<R, Promise<?>>> Promise<R> flatMap(
			Function<? super T, N> fMap) {
			return (Promise<R>) fMap.apply(rValue);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public State getState() {
			return State.RESOLVED;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(rValue);
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> orElse(Consumer<Exception> fHandler) {
			return this;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T orFail() {
			return rValue;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> fSupply) {
			return rValue;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Exception> T orThrow(
			Function<Exception, E> fMapException) throws E {
			return rValue;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T rFailureResult) {
			return rValue;
		}

		/***************************************
		 * {@inheritDoc}
		 */
		@Override
		public Promise<T> withTimeout(long nTime, TimeUnit eUnit) {
			return this;
		}
	}
}
