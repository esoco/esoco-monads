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

import de.esoco.lib.expression.ThrowingSupplier;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

/**
 * A {@link Monad} implementation for deferred (lazy) function calls that will
 * either supply a value or fail with an exception. Other than value-based
 * monads like {@link Option} which are evaluated only once upon creation, the
 * supplier wrapped by a call will be evaluated each time one of the consuming
 * methods {@link #orUse(Object)}, {@link Functor#orFail()},
 * {@link Functor#orThrow(Function)}, or {@link Functor#orElse(Consumer)} is
 * invoked. If a call is mapped with {@link #map(Function)} or
 * {@link #flatMap(Function)} a new call will be created that is also only be
 * evaluated when a consuming method is invoked.
 *
 * <p>Like {@link Try#lazy(ThrowingSupplier) lazy tries} calls can appear as
 * not complying with the monad laws. Mapping chains of calls are build by
 * creating anonymous suppliers which cannot be detected as equal and therefore
 * would cause equality comparisons of functional identical suppliers to fail.
 * But comparing the resolved value of such calls would be true, so evaluated
 * call chains are obeying the monad calls. Therefore equality comparisons of
 * unresolved calls (and similar monads) should be avoided as they will almost
 * always yield FALSE.</p>
 *
 * @author eso
 */
public class Call<T> implements Monad<T, Call<?>> {

	ThrowingSupplier<T> supplier;

	/**
	 * Creates a new instance.
	 *
	 * @param supplier The value supplier
	 */
	Call(ThrowingSupplier<T> supplier) {
		this.supplier = supplier;
	}

	/**
	 * Alternative to {@link #of(ThrowingSupplier)} that can be used as a
	 * static
	 * import.
	 *
	 * @param supplier The value supplier
	 * @return The new instance
	 */
	public static <T> Call<T> call(ThrowingSupplier<T> supplier) {
		return new Call<>(supplier);
	}

	/**
	 * Returns an always failing call.
	 *
	 * @param e The error exception
	 * @return the new instance
	 */
	public static <T> Call<T> error(Exception e) {
		return call(() -> {
			throw e;
		});
	}

	/**
	 * Returns a new instance that wraps a certain supplier.
	 *
	 * @param supplier The value supplier
	 * @return The new instance
	 */
	public static <T> Call<T> of(ThrowingSupplier<T> supplier) {
		return call(supplier);
	}

	/**
	 * Converts a collection of calls into a call that supplies a collection of
	 * the values from all calls. As with single-value calls repeated consuming
	 * of the returned call will re-evaluate all suppliers from the converted
	 * calls.
	 *
	 * @param calls The call collection to convert
	 * @return A new call that evaluates the suppliers of all calls and returns
	 * a collection of the results
	 */
	public static <T> Call<Collection<T>> ofAll(Collection<Call<T>> calls) {
		List<ThrowingSupplier<T>> aSuppliers =
			calls.stream().map(c -> c.supplier).collect(toList());

		return call(
			() -> aSuppliers.stream().map(Supplier::get).collect(toList()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <V, R, N extends Monad<V, Call<?>>> Call<R> and(N other,
		BiFunction<? super T, ? super V, ? extends R> join) {
		return (Call<R>) Monad.super.and(other, join);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Call &&
			Objects.equals(supplier, ((Call<?>) o).supplier));
	}

	/**
	 * Executes this call and throws a {@link RuntimeException} on errors
	 * unless
	 * an error handler has been set with {@link Functor#orElse(Consumer)}. In
	 * that case only the error handler will be executed on failures. An
	 * explicit error handler can be provided with {@link #execute(Consumer)}.
	 */
	public void execute() {
		orThrow(RuntimeException::new);
	}

	/**
	 * Executes this call and forwards errors to the given error handler.
	 *
	 * @param errorHandler The error handler
	 */
	public void execute(Consumer<Throwable> errorHandler) {
		orElse(errorHandler).orUse(null);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <R, N extends Monad<R, Call<?>>> Call<R> flatMap(
		Function<? super T, N> mapper) {
		return call(() -> applyFlatMapping(mapper));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(supplier);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R> Call<R> map(Function<? super T, ? extends R> mapper) {
		return flatMap(t -> call(() -> mapper.apply(t)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Call<T> orElse(Consumer<Throwable> handler) {
		return new ErrorHandlingCall<>(supplier, handler);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T orFail() throws Throwable {
		return Try.now(supplier).orFail();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T orGet(Supplier<T> supplier) {
		return Try.now(this.supplier).orGet(supplier);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <E extends Throwable> T orThrow(
		Function<Throwable, E> exceptionMapper) throws E {
		return Try.now(supplier).orThrow(exceptionMapper);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T orUse(T defaultValue) {
		return Try.now(supplier).orUse(defaultValue);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Call<T> then(Consumer<? super T> consumer) {
		return (Call<T>) Monad.super.then(consumer);
	}

	/**
	 * Converts this call into a lazy {@link Try} (see
	 * {@link Try#lazy(ThrowingSupplier)}.
	 *
	 * @return The resulting try
	 */
	public Try<T> toLazy() {
		return Try.lazy(supplier);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), supplier);
	}

	/**
	 * Converts this call into {@link Try} that contains either the result of a
	 * successful execution or any occurring error (see
	 * {@link Try#now(ThrowingSupplier)}.
	 *
	 * @return The resulting try
	 */
	public Try<T> toTry() {
		return Try.now(supplier);
	}

	/**
	 * Internal method to apply a {@link #flatMap(Function)} function to the
	 * result with the correct generic types.
	 *
	 * @param fMap The mapping function
	 * @return The mapped Call instance
	 * @throws Exception If the
	 */
	@SuppressWarnings("unchecked")
	<R, N extends Monad<R, Call<?>>> R applyFlatMapping(
		Function<? super T, N> fMap) throws Exception {
		try {
			return fMap.apply(supplier.tryGet()).orFail();
		} catch (Throwable throwable) {
			throw new RuntimeException(throwable);
		}
	}

	/**
	 * Subclass that performs error handling
	 *
	 * @author eso
	 */

	static class ErrorHandlingCall<T> extends Call<T> {

		private final Consumer<Throwable> errorHandler;

		/**
		 * Creates a new instance.
		 *
		 * @param supplier     The value supplier
		 * @param errorHandler The error handling function if it exists
		 */
		ErrorHandlingCall(ThrowingSupplier<T> supplier,
			Consumer<Throwable> errorHandler) {
			super(supplier);
			this.errorHandler = errorHandler;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public boolean equals(Object o) {
			return this == o ||
				(o instanceof ErrorHandlingCall && super.equals(o) &&
					Objects.equals(errorHandler,
						((ErrorHandlingCall<?>) o).errorHandler));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void execute() {
			orUse(null);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <R, N extends Monad<R, Call<?>>> Call<R> flatMap(
			Function<? super T, N> mapper) {
			return new ErrorHandlingCall<>(() -> applyFlatMapping(mapper),
				errorHandler);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int hashCode() {
			return super.hashCode() + 31 * Objects.hashCode(errorHandler);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Call<T> orElse(Consumer<Throwable> handler) {
			return new ErrorHandlingCall<>(supplier,
				errorHandler.andThen(handler));
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orFail() throws Throwable {
			return Try.now(supplier).orElse(errorHandler).orFail();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orGet(Supplier<T> supplier) {
			return Try.now(this.supplier).orElse(errorHandler).orGet(supplier);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public <E extends Throwable> T orThrow(
			Function<Throwable, E> exceptionMapper) throws E {
			return Try
				.now(supplier)
				.orElse(errorHandler)
				.orThrow(exceptionMapper);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public T orUse(T defaultValue) {
			return Try.now(supplier).orElse(errorHandler).orUse(defaultValue);
		}

		/**
		 * Converts this call into a lazy {@link Try} (see
		 * {@link Try#lazy(ThrowingSupplier)}.
		 *
		 * @return The resulting try
		 */
		@Override
		public Try<T> toLazy() {
			return Try.lazy(supplier).orElse(errorHandler);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String toString() {
			return String.format("%s[%s/%s]", getClass().getSimpleName(),
				supplier, errorHandler);
		}

		/**
		 * Converts this call into {@link Try} that contains either the result
		 * of a successful execution or any occurring error (see
		 * {@link Try#now(ThrowingSupplier)}.
		 *
		 * @return The resulting try
		 */
		@Override
		public Try<T> toTry() {
			return Try.now(supplier).orElse(errorHandler);
		}
	}
}
