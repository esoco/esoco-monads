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

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Interface of a {@link Functor} extension that provides the monadic method
 * {@link #flatMap(Function)} for transformation into derived monads. The
 * generic monad type has been relaxed to <code>Monad&lt;?,M&gt;</code> because
 * otherwise the generic declaration of monadic methods like
 * {@link #flatMap(Function)} would not be possible with Java's type system.
 * Subclasses therefore need to declare their own type at that position with a
 * wildcard parameter, e.g. <code>SubMonad&lt;T&gt; extends Monad&lt;T,
 * SubMonad&lt;?&gt;&gt;</code>. This type is not further exposed in the API and
 * is only needed for the type hierarchy to work.
 *
 * @author eso
 */
public interface Monad<T, M extends Monad<?, M>> extends Functor<T> {

	/**
	 * Combines this monad with another monad into a new monad of the same
	 * type.
	 * This is done by invoking {@link #flatMap(Function)} on this instance and
	 * "lifting" the other monad by invoking it's {@link #map(Function)}
	 * method.
	 * Therefore this type of method is sometimes also called "lift" or
	 * "liftM2".
	 *
	 * <p>Implementations should override this method to return their own type
	 * but they can simply invoke the default implementation (<code>
	 * Monad.super.and(other, join)</code>) and cast the result to their own
	 * type. The explicit declaration in subclasses is necessary because of the
	 * limitations of Java's generic type system.</p>
	 *
	 * @param other The other monad to combine with
	 * @param join  A binary function that joins the values of both monads
	 * @return The new joined monad
	 */
	default <V, R, N extends Monad<V, M>> Monad<R, M> and(N other,
		BiFunction<? super T, ? super V, ? extends R> join) {
		return flatMap(t -> other.map(v -> join.apply(t, v)));
	}

	/**
	 * Maps this instance into another monad by applying a mapping function to
	 * the enclosed value that produces the new monad. Implementations should
	 * override the return type to their specific type because the Java type
	 * system provides no way to generically define the method return type
	 * needed for this case.
	 *
	 * @param mapper The monad mapping function
	 * @return The mapped and flattened monad
	 */
	<R, N extends Monad<R, M>> Monad<R, M> flatMap(
		Function<? super T, N> mapper);

	/**
	 * Redefined here to change the return type to Monad. Subclasses can
	 * typically implement this by invoking their {@link #flatMap(Function)}
	 * and
	 * declare their own type as the return type.
	 *
	 * @see Functor#map(Function)
	 */
	@Override
	<R> Monad<R, M> map(Function<? super T, ? extends R> mapper);

	/**
	 * Redefined here to provide a monad-specific default implementation and to
	 * change the return type to Monad. Subclasses can typically just invoke
	 * super and declare their own type as the return type.
	 *
	 * @see Functor#then(Consumer)
	 */
	@Override
	default Monad<T, M> then(Consumer<? super T> consumer) {
		return flatMap(t -> {
			consumer.accept(t);
			return this;
		});
	}
}
