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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


/********************************************************************
 * Interface of functors that wrap values of type T and allow their mapping with
 * {@link #map(Function)}. This interface also defines several consuming methods
 * like {@link #orUse(Object)} and {@link #orFail()} that handle the case where
 * a functor cannot provide or determine a valid value. These methods are
 * typically invoked at the end of a chain of mappings from one functor to
 * another to consume the final value or react to failure to produce that value.
 *
 * @author eso
 */
public interface Functor<T> {

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Maps the wrapped value into a new functor instance.
	 *
	 * @param  fMap The value mapping function
	 *
	 * @return A mapped functor
	 */
	public <R> Functor<R> map(Function<? super T, ? extends R> fMap);

	/***************************************
	 * Sets an operation that is performed if the functor doesn't contain a
	 * valid value. This can be used to define the alternative of a call to a
	 * monadic function like {@link #map(Function)} and especially {@link
	 * #then(Consumer)} to handle a failure case.
	 *
	 * <p>Returns the resulting functor instance to allow chaining of further
	 * calls or assigning to a variable.</p>
	 *
	 * @param  fHandler The consumer of the the error that occurred
	 *
	 * @return The resulting functor for chained invocations
	 */
	public Functor<T> orElse(Consumer<Exception> fHandler);

	/***************************************
	 * A consuming operation that either returns the functor's value or throws
	 * an implementation-dependent exception if a valid value couldn't be
	 * determined.
	 *
	 * <p>In general, calls to the monadic functions {@link #map(Function)} or
	 * {@link #then(Consumer)} should be preferred to processing values with
	 * consuming operations.</p>
	 *
	 * @return The functor's value
	 *
	 * @throws Exception An exception signaling an invalid or indeterminable
	 *                   value
	 */
	public T orFail() throws Exception;

	/***************************************
	 * A consuming operation that either returns the functor's value or returns
	 * the result of invoking the given supplier if a valid value couldn't be
	 * determined.
	 *
	 * <p>In general, calls to the monadic functions {@link #map(Function)} or
	 * {@link #then(Consumer)} should be preferred to processing values with
	 * consuming operations.</p>
	 *
	 * @param  fSupply The supplier to invoke if no regular value could be
	 *                 determined
	 *
	 * @return The resulting value
	 */
	public T orGet(Supplier<T> fSupply);

	/***************************************
	 * A consuming operation that either returns the functor's value or throws a
	 * mapped exception if a valid value couldn't be determined.
	 *
	 * <p>In general, calls to the monadic functions {@link #map(Function)} or
	 * {@link #then(Consumer)} should be preferred to processing values with
	 * consuming operations.</p>
	 *
	 * @param  fMapException A function that maps the original exception
	 *
	 * @return The result of the execution
	 *
	 * @throws E The argument exception in the case of a failure
	 */
	public <E extends Exception> T orThrow(Function<Exception, E> fMapException)
		throws E;

	/***************************************
	 * A consuming operation that either returns the functor's value or returns
	 * the given default value if a valid value couldn't be determined. The
	 * default implementation wraps the argument value into a {@link Supplier}
	 * and invokes {@link #orGet(Supplier)}.
	 *
	 * <p>In general, calls to the monadic functions {@link #map(Function)} or
	 * {@link #then(Consumer)} should be preferred to processing values with
	 * consuming operations.</p>
	 *
	 * @param  rDefaultValue The value to return if no regular value could be
	 *                       determined
	 *
	 * @return The resulting value
	 */
	default T orUse(T rDefaultValue) {
		return orGet(() -> rDefaultValue);
	}

	/***************************************
	 * Consumes the value of this functor. This method is typically used at the
	 * end of a mapping chain for the final processing of the resulting value.
	 * The default implementation invokes {@link #map(Function)} with the
	 * argument consumer wrapped into a function. Some subclasses may be able to
	 * provide an optimized version. Furthermore, subclasses should typically
	 * override this method with their own type as the return type (but may just
	 * invoke the super implementation and cast the returned value).
	 *
	 * @param  fConsumer The consumer of the wrapped value
	 *
	 * @return The resulting functor for chained invocations
	 */
	default Functor<T> then(Consumer<? super T> fConsumer) {
		return map(t -> {
			fConsumer.accept(t);

			return t;
		});
	}
}
