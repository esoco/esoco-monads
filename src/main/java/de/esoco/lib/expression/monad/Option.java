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

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * A {@link Monad} implementation for optional values.
 *
 * @author eso
 */
public class Option<T> implements Monad<T, Option<?>> {

	private static final Option<?> NONE = new Option<>(null);

	private final T value;

	/**
	 * Creates a new instance.
	 *
	 * @param value The value to wrap
	 */
	private Option(T value) {
		this.value = value;
	}

	/**
	 * A semantic alternative to {@link #ofRequired(Object)} that can be
	 * used as
	 * a static import.
	 *
	 * @param value The value to wrap
	 * @return The new instance
	 */
	public static <T> Option<T> nonNull(T value) {
		return Option.ofRequired(value);
	}

	/**
	 * Returns a instance for an undefined value. It's {@link #exists()} method
	 * will always return FALSE.
	 *
	 * @return The nothing value
	 */
	@SuppressWarnings("unchecked")
	public static <T> Option<T> none() {
		return (Option<T>) NONE;
	}

	/**
	 * Returns a new instance that wraps a certain value or {@link #none()} if
	 * the argument is NULL. To throw an exception if the value is NULL use
	 * {@link #ofRequired(Object)} instead.
	 *
	 * @param value The value to wrap
	 * @return The new instance
	 */
	public static <T> Option<T> of(T value) {
		return value != null ? new Option<>(value) : none();
	}

	/**
	 * Converts a collection of options into either an existing option of a
	 * collection of values if all options in the collection exist or into
	 * {@link #none()} if one or more options in the collection do not exist.
	 *
	 * <p>Other than {@link #ofExisting(Stream)} this transformation cannot be
	 * performed on a stream (of possibly indefinite size) because existence
	 * needs to be determined upon invocation.</p>
	 *
	 * @param options The collection of options to convert
	 * @return A new option of a collection of the values of all options or
	 * {@link #none()} if one or more options do not exist
	 */
	public static <T> Option<Collection<T>> ofAll(
		Collection<Option<T>> options) {
		Optional<Option<T>> missing =
			options.stream().filter(o -> !o.exists()).findFirst();

		return missing.isPresent() ?
		       none() :
		       Option.of(options.stream().map(option -> {
			       try {
				       return option.orFail();
			       } catch (Throwable throwable) {
				       throw new RuntimeException(throwable);
			       }
		       }).collect(toList()));
	}

	/**
	 * Converts a stream of options into an option of a stream of existing
	 * values.
	 *
	 * @param options The stream to convert
	 * @return A new option containing a stream of existing values
	 */
	public static <T> Option<Stream<T>> ofExisting(Stream<Option<T>> options) {
		return Option.of(options.filter(Option::exists).map(o -> o.value));
	}

	/**
	 * Returns a new instance with the same state as a Java {@link Optional}.
	 *
	 * @param optional The input value
	 * @return The new instance
	 */
	public static <T> Option<T> ofOptional(Optional<T> optional) {
		return optional.isPresent() ? new Option<>(optional.get()) : none();
	}

	/**
	 * Returns a new instance that wraps a certain value which must not be
	 * NULL.
	 *
	 * @param value The required value to wrap
	 * @return The new instance
	 * @throws NullPointerException If the given value is NULL
	 */
	public static <T> Option<T> ofRequired(T value) {
		Objects.requireNonNull(value);

		return Option.of(value);
	}

	/**
	 * A semantic alternative to {@link #of(Object)} that can be used as a
	 * static import.
	 *
	 * @param value The value to wrap
	 * @return The new instance
	 */
	public static <T> Option<T> option(T value) {
		return Option.of(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <V, R, N extends Monad<V, Option<?>>> Option<R> and(N other,
		BiFunction<? super T, ? super V, ? extends R> join) {
		return (Option<R>) Monad.super.and(other, join);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof Option &&
			Objects.equals(value, ((Option<?>) o).value));
	}

	/**
	 * Test whether this option represents an existing value.
	 *
	 * @return TRUE if this option exists, FALSE if it is undefined
	 * ({@link #none()})
	 */
	public final boolean exists() {
		return value != null;
	}

	/**
	 * Filter this option according to the given criteria by returning an
	 * option
	 * that exists depending on whether the value fulfills the criteria.
	 *
	 * @param criteria A predicate defining the filter criteria
	 * @return The resulting option
	 */
	@SuppressWarnings("unchecked")
	public Option<T> filter(Predicate<T> criteria) {
		return flatMap(v -> criteria.test(v) ? this : none());
	}

	/**
	 * Converts this option into another option by applying a mapping function
	 * that produces an option with the target type from the value of this
	 * instance.
	 *
	 * <p>Other than Java's {@link Optional} this implementation respects the
	 * monad laws of left and right identity as well as associativity. It does
	 * so by considering the not existing value {@link #none()} as
	 * equivalent to
	 * NULL. Therefore, if the mapping function is invoked on a non-existing
	 * option it will receive NULL as it's argument and should be able to
	 * handle
	 * it.</p>
	 *
	 * <p>If the mapping function throws a {@link NullPointerException} it will
	 * be caught and the returned option will be {@link #none()}. This
	 * allows to
	 * use functions that are not NULL-aware as an argument. It has the
	 * limitation that NPEs caused by nested code will not be thrown.</p>
	 *
	 * @param mapper The mapping function
	 * @return The resulting option
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R, N extends Monad<R, Option<?>>> Option<R> flatMap(
		Function<? super T, N> mapper) {
		try {
			return (Option<R>) mapper.apply(value);
		} catch (NullPointerException e) {
			return none();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(value);
	}

	/**
	 * A semantic alternative to {@link #then(Consumer)}.
	 *
	 * @param consumer The consumer to invoke
	 * @return The resulting option for chained invocations
	 */
	public final Option<T> ifExists(Consumer<? super T> consumer) {
		return then(consumer);
	}

	/**
	 * A convenience method for a fast test of options for existence and a
	 * certain value datatype. This is especially helpful for options that are
	 * declared with a generic or common type (like <code>Option&lt;?&gt;
	 * </code>
	 * or <code>Option&lt;Object&gt;</code>).
	 *
	 * @param datatype The datatype to test the wrapped value against
	 * @return TRUE if this option exists and the value can be assigned to the
	 * given datatype
	 */
	public final boolean is(Class<?> datatype) {
		return exists() && datatype.isAssignableFrom(value.getClass());
	}

	/**
	 * Maps this option to another one containing the result of invoking a
	 * mapping function on this instance's value. Other than Java's
	 * {@link Optional} this implementation respects the monad laws. See the
	 * description of {@link #flatMap(Function)} for details.
	 *
	 * @param mapper The mapping function
	 * @return The mapped option
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R> Option<R> map(Function<? super T, ? extends R> mapper) {
		return flatMap(t -> Option.of(mapper.apply(t)));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Option<T> orElse(Consumer<Throwable> handler) {
		if (!exists()) {
			handler.accept(new NullPointerException());
		}

		return this;
	}

	/**
	 * A variant of {@link Functor#orElse(Consumer)} that simply executes some
	 * code if this option doesn't exist.
	 *
	 * @param code The code to execute
	 */
	public void orElse(Runnable code) {
		if (!exists()) {
			code.run();
		}
	}

	/**
	 * Throws a {@link NullPointerException} if this option does not exist.
	 *
	 * @see Functor#orFail()
	 */
	@Override
	public T orFail() throws NullPointerException {
		if (exists()) {
			return value;
		} else {
			throw new NullPointerException();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T orGet(Supplier<T> supplier) {
		return exists() ? value : supplier.get();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public <E extends Throwable> T orThrow(
		Function<Throwable, E> exceptionMapper) throws E {
		if (exists()) {
			return value;
		} else {
			throw exceptionMapper.apply(new NullPointerException());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public T orUse(T defaultValue) {
		return exists() ? value : defaultValue;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Option<T> then(Consumer<? super T> consumer) {
		return exists() ? (Option<T>) Monad.super.then(consumer) : this;
	}

	/**
	 * Returns an {@link Optional} instance that represents this instance.
	 *
	 * @return The optional instance
	 */
	public Optional<T> toOptional() {
		return Optional.ofNullable(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return exists() ? value.toString() : "[none]";
	}
}
