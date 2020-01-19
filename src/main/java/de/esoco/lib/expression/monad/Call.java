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


/********************************************************************
 * A {@link Monad} implementation for deferred (lazy) function calls that will
 * either supply a value or fail with an exception. Other than value-based
 * monads like {@link Option} which are evaluated only once upon creation, the
 * supplier wrapped by a call will be evaluated each time one of the consuming
 * methods {@link #orUse(Object)}, {@link #orFail()}, {@link
 * #orThrow(Function)}, or {@link #orElse(Consumer)} is invoked. If a call is
 * mapped with {@link #map(Function)} or {@link #flatMap(Function)} a new call
 * will be created that is also only be evaluated when a consuming method is
 * invoked.
 *
 * <p>Like {@link Try#lazy(ThrowingSupplier) lazy tries} calls can may appear as
 * not complying with the monad laws although they effectively they are. That is
 * caused by the fact that call (flat) mappings are build by chaining anonymous
 * suppliers which cannot be detected as equal, even if they are. But when using
 * calls and mapped call chains they fully obey the monad laws because at the
 * end of the call chain theres's always a resolved value which is equal for
 * both sides of the monad law equations. Equality comparisons of unresolved
 * calls should be avoided as they will almost always yield FALSE.</p>
 *
 * @author eso
 */
public class Call<T> implements Monad<T, Call<?>> {

	//~ Instance fields --------------------------------------------------------

	private ThrowingSupplier<T> fSupplier;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fSupplier The value supplier
	 */
	private Call(ThrowingSupplier<T> fSupplier) {
		this.fSupplier = fSupplier;
	}

	//~ Static methods ---------------------------------------------------------

	/***************************************
	 * Alternative to {@link #of(ThrowingSupplier)} that can be used as a static
	 * import.
	 *
	 * @param  fSupplier The value supplier
	 *
	 * @return The new instance
	 */
	public static <T> Call<T> call(ThrowingSupplier<T> fSupplier) {
		return new Call<>(fSupplier);
	}

	/***************************************
	 * Returns an always failing call.
	 *
	 * @param  eError The error exception
	 *
	 * @return the new instance
	 */
	public static <T> Call<T> error(Exception eError) {
		return new Call<>(() -> { throw eError; });
	}

	/***************************************
	 * Returns a new instance that wraps a certain supplier.
	 *
	 * @param  fSupplier The value supplier
	 *
	 * @return The new instance
	 */
	public static <T> Call<T> of(ThrowingSupplier<T> fSupplier) {
		return new Call<>(fSupplier);
	}

	/***************************************
	 * Converts a collection of calls into a call that supplies a collection of
	 * the values from all calls. As with single-value calls repeated consuming
	 * of the returned call will re-evaluate all suppliers from the converted
	 * calls.
	 *
	 * @param  rCalls The call collection to convert
	 *
	 * @return A new call that evaluates the suppliers of all calls and returns
	 *         a collection of the results
	 */
	public static <T> Call<Collection<T>> ofAll(Collection<Call<T>> rCalls) {
		List<ThrowingSupplier<T>> aSuppliers =
			rCalls.stream().map(c -> c.fSupplier).collect(toList());

		return new Call<>(
			() -> aSuppliers.stream().map(Supplier::get).collect(toList()));
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <V, R, N extends Monad<V, Call<?>>> Call<R> and(
		N											  rOther,
		BiFunction<? super T, ? super V, ? extends R> fJoin) {
		return (Call<R>) Monad.super.and(rOther, fJoin);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(Object rObject) {
		return this == rObject ||
			   (rObject instanceof Call &&
				Objects.equals(fSupplier, ((Call<?>) rObject).fSupplier));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public <R, N extends Monad<R, Call<?>>> Call<R> flatMap(
		Function<? super T, N> fMap) {
		return call(() -> applyFlatMapping(fMap));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return Objects.hashCode(fSupplier);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	public <R> Call<R> map(Function<? super T, ? extends R> fMap) {
		return flatMap(t -> call(() -> fMap.apply(t)));
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public Call<T> orElse(Consumer<Exception> fHandler) {
		return call(() -> Try.now(fSupplier).orElse(fHandler).orFail());
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public T orFail() throws Exception {
		return Try.now(fSupplier).orFail();
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public T orGet(Supplier<T> fSupply) {
		return Try.now(fSupplier).orGet(fSupply);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public <E extends Exception> T orThrow(Function<Exception, E> fMapException)
		throws E {
		return Try.now(fSupplier).orThrow(fMapException);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public T orUse(T rDefault) {
		return Try.now(fSupplier).orUse(rDefault);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public Call<T> then(Consumer<? super T> fConsumer) {
		return (Call<T>) Monad.super.then(fConsumer);
	}

	/***************************************
	 * Converts this call into a lazy {@link Try} (see {@link
	 * Try#lazy(ThrowingSupplier)}.
	 *
	 * @return The resulting try
	 */
	public Try<T> toLazy() {
		return Try.lazy(fSupplier);
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return String.format("%s[%s]", getClass().getSimpleName(), fSupplier);
	}

	/***************************************
	 * Converts this call into {@link Try} that contains either the result of a
	 * successful execution or any occurring error (see {@link
	 * Try#now(ThrowingSupplier)}.
	 *
	 * @return The resulting try
	 */
	public Try<T> toTry() {
		return Try.now(fSupplier);
	}

	/***************************************
	 * Internal method to apply a {@link #flatMap(Function)} function to the
	 * result with the correct generic types.
	 *
	 * @param  fMap The mapping function
	 *
	 * @return The mapped Call instance
	 *
	 * @throws Exception If the
	 */
	@SuppressWarnings("unchecked")
	private <R, N extends Monad<R, Call<?>>> R applyFlatMapping(
		Function<? super T, N> fMap) throws Exception {
		return ((Call<R>) fMap.apply(fSupplier.tryGet())).orFail();
	}
}
