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

import de.esoco.lib.expression.ThrowingRunnable;
import de.esoco.lib.expression.ThrowingSupplier;

import java.util.Collection;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A {@link Monad} implementation for the attempted execution of value-supplying
 * operations that may fail with an exception. Whether the execution was
 * successful can be tested with {@link #isSuccess()}.
 *
 * <p>
 * Executions can be performed in two different ways: either immediately by
 * creating an instance with {@link #now(ThrowingSupplier)} or lazily with
 * {@link #lazy(ThrowingSupplier)}. In the first case the provided supplier will
 * be evaluated upon creation of the try instance. In the case of lazy
 * executions the supplier will only be evaluated when the Try is consumed by
 * invoking one of the terminating methods like {@link #orUse(Object)} or
 * {@link #orFail()}. This will also be the case for mapped lazy tries. A side
 * effect of this is that lazy tries may appear as not complying with the monad
 * laws, although effectively they are (see method comment of
 * {@link #lazy(ThrowingSupplier)} for details).
 * </p>
 *
 * <p>
 * The supplier of a try, even of a lazy one, is always only evaluated once.
 * Each subsequent consumption will yield the same result. If a supplier should
 * be re-evaluated on each call the {@link Call} monad can be used instead.
 * </p>
 *
 * @author eso
 */
public abstract class Try<T> implements Monad<T, Try<?>> {

	/**
	 * Creates a new instance.
	 */
	Try() {
	}

	/**
	 * Returns an instance that represents a failed execution.
	 *
	 * @param e The error exception
	 * @return The failure instance
	 */
	public static <T> Try<T> failure(Throwable e) {
		return new Failure<>(e);
	}

	/**
	 * Returns a new instance that will perform a lazy evaluation of the given
	 * supplier. That means the supplier will only be queried if one of the
	 * consuming methods like {@link #orUse(Object)} is invoked to access the
	 * result. {@link #map(Function) Mapping} or
	 * {@link #flatMap(Function) flat mapping} a lazy try will create another
	 * unresolved, lazy instance.
	 *
	 * <p>
	 * A side effect of lazy tries not evaluating the argument supplier
	 * immediately is that an unresolved lazy try does not directly comply with
	 * the monad laws. This is because when (flat) mapped additional wrapping
	 * suppliers will be created that will also be evaluated lazily. Until
	 * resolving tests between otherwise identical lazy tries will fail because
	 * the created supplier cannot not be recognized as equal. Resolved lazy
	 * tries obey the monad laws, so lazy tries are effectively compliant with
	 * the laws. Equality tests should therefore only be used with caution on
	 * lazy tries (or better, not at all).
	 * </p>
	 *
	 * <p>
	 * An instance that evaluates the supplier immediately and directly obeys
	 * the monad laws can be created with {@link #now(ThrowingSupplier)}.
	 * </p>
	 *
	 * @param supplier The throwing supplier to evaluate lazily
	 * @return The new instance
	 */
	public static <T> Try<T> lazy(ThrowingSupplier<T> supplier) {
		return new Lazy<>(supplier);
	}

	/**
	 * Returns a new instance that represents the immediate execution of the
	 * given {@link Supplier}. The returned instance either represents a
	 * successful execution or a failure if the execution failed.
	 *
	 * <p>
	 * An instance that evaluates the supplier lazily on the first access
	 * can be
	 * created with {@link #lazy(ThrowingSupplier)}.
	 * </p>
	 *
	 * @param supplier The supplier to execute
	 * @return The new instance
	 */
	public static <T> Try<T> now(ThrowingSupplier<T> supplier) {
		try {
			return new Success<>(supplier.tryGet());
		} catch (Exception e) {
			return new Failure<>(e);
		}
	}

	/**
	 * Converts a collection of attempted executions into either a successful
	 * try of a collection of values if all tries in the collection were
	 * successful or into a failed try if one or more tries in the collection
	 * have failed. The error of the failure will be that of the first failed
	 * try.
	 *
	 * <p>
	 * Other than {@link #ofSuccessful(Stream)} this transformation cannot be
	 * performed on a stream (of possibly indefinite size) because success or
	 * failure needs to be determined upon invocation.
	 * </p>
	 *
	 * @param tries The collection of tries to convert
	 * @return A new successful try of a collection of the values of all tries
	 * or a failure if one or more tries failed
	 */
	public static <T> Try<Collection<T>> ofAll(Collection<Try<T>> tries) {
		// map to RuntimeException is only an assertion, can never happen
		return tries
			.stream()
			.filter(t -> !t.isSuccess())
			.findFirst()
			.<Try<Collection<T>>>map(t -> failure(((Failure<?>) t).error))
			.orElseGet(() -> success(tries
				.stream()
				.map(t -> t.orThrow(RuntimeException::new))
				.collect(toList())));
	}

	/**
	 * Converts a stream of attempted executions into a try of a stream of
	 * successful values.
	 *
	 * @param tries The stream of tries to convert
	 * @return A new try that contains a stream of successful values
	 */
	public static <T> Try<Stream<T>> ofSuccessful(Stream<Try<T>> tries) {
		// map to RuntimeException is only an assertion, can never happen
		return Try.now(() -> tries
			.filter(Try::isSuccess)
			.map(t -> t.orThrow(RuntimeException::new)));
	}

	/**
	 * Returns a new instance that represents the immediate execution of the
	 * given {@link Runnable}. The returned instance either represents a
	 * successful execution or a failure if the execution failed.
	 *
	 * <p>
	 * Because a runnable doesn't return a value the generic type of the result
	 * is VOID and consequently the wrapped value is NULL. Therefore mapping or
	 * consuming the returned instance doesn't make sense. The main purpose of
	 * this method is to serve as a compact alternative to try/catch blocks,
	 * like in this example:
	 * </p>
	 *
	 * <pre>
	 * Try.run(() -&gt; invokeWithPossibleFailure()).orElse(e -&gt; handleError(e));
	 * </pre>
	 *
	 * @param code The code to execute
	 * @return The new instance
	 */
	public static Try<Void> run(ThrowingRunnable code) {
		try {
			code.run();

			return new Success<>(null);
		} catch (Exception e) {
			return new Failure<>(e);
		}
	}

	/**
	 * Returns an instance that represents a failed execution.
	 *
	 * @param value error The error exception
	 * @return The failure instance
	 */
	public static <T> Try<T> success(T value) {
		return new Success<>(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <V, R, N extends Monad<V, Try<?>>> Try<R> and(N other,
		BiFunction<? super T, ? super V, ? extends R> join) {
		return (Try<R>) Monad.super.and(other, join);
	}

	/**
	 * Filter this try according to the given criteria by returning a try that
	 * is successful if this try is successful and the wrapped value fulfills
	 * the criteria, or a failure otherwise.
	 *
	 * @param criteria A predicate defining the filter criteria
	 * @return The resulting try
	 */
	public Try<T> filter(Predicate<T> criteria) {
		return flatMap(v -> criteria.test(v) ?
		                    success(v) :
		                    failure(new Exception("Criteria not met by " + v)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public abstract <R, N extends Monad<R, Try<?>>> Try<R> flatMap(
		Function<? super T, N> mapper);

	/**
	 * A semantic alternative to {@link #then(Consumer)}.
	 *
	 * @param consumer The consumer to invoke
	 * @return The resulting try for chained invocations
	 */
	public final Try<T> ifSuccessful(Consumer<? super T> consumer) {
		return then(consumer);
	}

	/**
	 * Checks whether the execution was successful.
	 *
	 * @return TRUE if this try was executed successfully, FALSE if an
	 * exception
	 * occurred
	 */
	public abstract boolean isSuccess();

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R> Try<R> map(Function<? super T, ? extends R> mapper) {
		return flatMap(t -> Try.now(() -> mapper.apply(t)));
	}

	/**
	 * Redefined here to change the return type.
	 *
	 * @see Functor#orElse(Consumer)
	 */
	@Override
	public abstract Try<T> orElse(Consumer<Throwable> handler);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Try<T> then(Consumer<? super T> consumer) {
		return (Try<T>) Monad.super.then(consumer);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(),
			getDescribingValue());
	}

	/**
	 * Returns a value that describes this instance's state.
	 *
	 * @return The string value
	 */
	abstract Object getDescribingValue();

	/**
	 * The implementation of successful tries.
	 *
	 * @author eso
	 */
	static class Failure<T> extends Try<T> {

		private final Throwable error;

		/**
		 * Creates a new instance.
		 *
		 * @param error value The successfully created value
		 */
		Failure(Throwable error) {
			this.error = error;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object o) {
			return this == o || (o instanceof Failure &&
				Objects.equals(error, ((Failure<?>) o).error));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("unchecked")
		public <R, N extends Monad<R, Try<?>>> Try<R> flatMap(
			Function<? super T, N> mapper) {
			return (Try<R>) this;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(error);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final boolean isSuccess() {
			return false;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Try<T> orElse(Consumer<Throwable> handler) {
			handler.accept(error);

			return this;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final T orFail() throws Throwable {
			throw error;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> supplier) {
			return supplier.get();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Throwable> T orThrow(
			Function<Throwable, E> exceptionMapper) throws E {
			throw exceptionMapper.apply(error);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T defaultValue) {
			return defaultValue;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		String getDescribingValue() {
			return error.getMessage();
		}
	}

	/**
	 * The implementation of lazy tries with deferred evaluation.
	 *
	 * @author eso
	 */
	static class Lazy<T> extends Try<T> {

		private final ThrowingSupplier<T> valueSupplier;

		private Option<Try<T>> result = Option.none();

		/**
		 * Creates a new instance.
		 *
		 * @param valueSupplier value The successfully created value
		 */
		Lazy(ThrowingSupplier<T> valueSupplier) {
			this.valueSupplier = valueSupplier;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object object) {
			return this == object || (object instanceof Lazy &&
				(result.exists() ?
				 Objects.equals(result, ((Lazy<?>) object).result) :
				 Objects.equals(valueSupplier,
					 ((Lazy<?>) object).valueSupplier)));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <R, N extends Monad<R, Try<?>>> Try<R> flatMap(
			Function<? super T, N> mapper) {
			return lazy(() -> applyFlatMapping(mapper));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return Objects.hashCode(result.exists() ? result : valueSupplier);
		}

		/**
		 * Testing for success needs to perform the lazy evaluation.
		 *
		 * @see Try#isSuccess()
		 */
		@Override
		public final boolean isSuccess() {
			return getResult().isSuccess();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <R> Try<R> map(Function<? super T, ? extends R> mapper) {
			return lazy(() -> {
				try {
					return mapper.apply(getResult().orFail());
				} catch (Throwable throwable) {
					throw new RuntimeException(throwable);
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Try<T> orElse(Consumer<Throwable> handler) {
			// orElse() will always be invoked on Success or Failure
			return flatMap(t -> getResult().orElse(handler));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final T orFail() throws Throwable {
			return getResult().orFail();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> supplier) {
			return getResult().orGet(supplier);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Throwable> T orThrow(
			Function<Throwable, E> exceptionMapper) throws E {
			return getResult().orThrow(exceptionMapper);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T defaultValue) {
			return getResult().orUse(defaultValue);
		}

		/**
		 * Internal method to apply a {@link #flatMap(Function)} function to
		 * the
		 * result of this instance.
		 *
		 * @param map The mapping function
		 * @return The mapped Try instance
		 * @throws Exception If accessing a try result fails
		 */
		@SuppressWarnings("unchecked")
		<R, N extends Monad<R, Try<?>>> R applyFlatMapping(
			Function<? super T, N> map) throws Exception {
			try {
				return map.apply(getResult().orFail()).orFail();
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		Object getDescribingValue() {
			try {
				return result.exists() ? result.orFail() : valueSupplier;
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}

		/**
		 * Performs the lazy evaluation of this instance if not performed
		 * before.
		 *
		 * @return A try of the (evaluated) result
		 */
		Try<T> getResult() {
			if (!result.exists()) {
				result = Option.of(now(valueSupplier));

				// if supplier returned NULL convert to success
				if (!result.exists()) {
					result = Option.of(success(null));
				}
			}

			// this will never fail
			try {
				return result.orFail();
			} catch (Throwable throwable) {
				throw new RuntimeException(throwable);
			}
		}
	}

	/**
	 * The implementation of successful tries.
	 *
	 * @author eso
	 */
	static class Success<T> extends Try<T> {

		private final T value;

		/**
		 * Creates a new instance.
		 *
		 * @param value The successfully created value
		 */
		Success(T value) {
			this.value = value;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object object) {
			return this == object || (object instanceof Success &&
				Objects.equals(value, ((Success<?>) object).value));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		@SuppressWarnings("unchecked")
		public <R, N extends Monad<R, Try<?>>> Try<R> flatMap(
			Function<? super T, N> mapper) {
			return (Try<R>) mapper.apply(value);
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
		public final boolean isSuccess() {
			return true;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Try<T> orElse(Consumer<Throwable> handler) {
			return this;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public final T orFail() throws Throwable {
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
		Object getDescribingValue() {
			return value;
		}
	}
}
