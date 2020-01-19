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

import de.esoco.lib.datatype.Pair;
import de.esoco.lib.expression.monad.Promise.State;

import java.time.LocalDate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.junit.Test;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/********************************************************************
 * Test of {@link Promise}.
 *
 * @author eso
 */
@SuppressWarnings("rawtypes")
public class PromiseTest extends MonadTest {

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Test of {@link Promise#and(Monad, java.util.function.BiFunction)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testAnd() throws Throwable {
		LocalDate today = LocalDate.now();

		Promise<LocalDate> aLocalDatePromise =
			Promise.of(() -> today.getYear())
				   .and(
	   				Promise.of(() -> today.getMonth()),
	   				(y, m) -> Pair.of(y, m))
				   .and(
	   				Promise.of(() -> today.getDayOfMonth()),
	   				(ym, d) ->
	   					LocalDate.of(ym.first(), ym.second(), d));

		aLocalDatePromise.then(d -> assertEquals(today, d)).orFail();
	}

	/***************************************
	 * Test of {@link Promise#orFail()}.
	 *
	 * @throws Exception
	 */
	@Test
	public void testAwaitAndOrElse() throws Exception {
		Promise<String> p	    = Promise.failure(new Exception());
		Exception[]     aResult = new Exception[1];

		p = p.then(s -> fail()).orElse(e -> aResult[0] = e);

		try {
			p.await();
			fail();
		} catch (Exception e) {
			// expected
		}

		assertEquals("ERROR", p.orUse("ERROR"));
		assertNotNull(aResult[0]);
	}

	/***************************************
	 * Test of {@link Promise#flatMap(java.util.function.Function)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testFlatMap() throws Throwable {
		Promise.resolved("42")
			   .flatMap(s -> Promise.of(() -> Integer.parseInt(s)))
			   .then(i -> assertEquals(Integer.valueOf(42), i))
			   .orFail();
	}

	/***************************************
	 * Test of {@link Promise#map(Function)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testMap() throws Throwable {
		Promise.of(() -> "42")
			   .map(Integer::parseInt)
			   .then(i -> assertEquals(Integer.valueOf(42), i))
			   .orFail();
	}

	/***************************************
	 * {@inheritDoc}
	 *
	 * @throws Exception
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Test
	public void testMonadLaws() throws Exception {
		testAllMonadLaws(Promise::resolved);

		// explicit monad law tests for unresolved promises because these need
		// to be resolved before equality tests
		Function<String, String> f1 = mapString("1");
		Function<String, String> f2 = mapString("2");

		assertEquals(
			Promise.of(() -> TEST_VALUE)
			.flatMap(f1.andThen(Promise::resolved))
			.await(),
			f1.andThen(Promise::resolved).apply(TEST_VALUE).await());
		assertEquals(
			Promise.of(() -> TEST_VALUE).flatMap(Promise::resolved).await(),
			Promise.of(() -> TEST_VALUE).await());
		assertEquals(
			Promise.of(() -> TEST_VALUE)
			.flatMap(f1.andThen(Promise::resolved))
			.flatMap(f2.andThen(Promise::resolved))
			.await(),
			Promise.of(() -> TEST_VALUE)
			.flatMap(f1.andThen(f2).andThen(Promise::resolved))
			.await());
	}

	/***************************************
	 * Test of {@link Promise#ofAll(java.util.stream.Stream)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testOfAll() throws Throwable {
		List<Promise<String>> promises =
			new ArrayList<>(
				asList(
					Promise.resolved("1"),
					Promise.resolved("2"),
					Promise.resolved("3")));

		Promise<Collection<String>> p = Promise.ofAll(promises);

		assertEquals(3, p.orFail().size());
		assertTrue(p.isResolved());

		Exception err = new Exception(TEST_VALUE);

		promises.add(Promise.failure(err));

		Promise.ofAll(promises)
			   .then(c -> fail())
			   .orElse(e -> assertEquals(err, e));
	}

	/***************************************
	 * Test of {@link Promise#ofAny(java.util.stream.Stream)}.
	 *
	 * @throws Exception
	 */
	@Test
	public void testOfAny() throws Exception {
		Promise<String> p =
			Promise.ofAny(
	   				asList(
	   					Promise.resolved("1"),
	   					Promise.resolved("2"),
	   					Promise.resolved("3"))).await();

		assertTrue(p.isResolved());
		assertEquals("1", p.orFail());

		p = Promise.ofAny(
				asList(
					Promise.failure(new Exception("1")),
					Promise.failure(new Exception("2")),
					Promise.failure(new Exception("3"))));

		try {
			p = p.await();
			fail();
		} catch (Exception e) {
			// expected
		}

		assertEquals(State.FAILED, p.getState());
	}

	/***************************************
	 * Test of {@link Promise#orFail()}.
	 */
	@Test
	public void testOrFail() {
		Promise<String> p = Promise.failure(new Exception());

		try {
			p.orFail();
			fail();
		} catch (Throwable e) {
			// expected
		}
	}

	/***************************************
	 * Test of {@link Promise#orThrow(java.util.function.Function)}.
	 */
	@Test
	public void testOrThrow() {
		Exception	    eError = new Exception();
		Promise<String> p	   = Promise.failure(new Exception());

		try {
			p.orThrow(e -> eError);
			fail();
		} catch (Throwable e) {
			assertEquals(eError, e);
		}
	}

	/***************************************
	 * Test of {@link Promise#orUse(Object)}.
	 */
	@Test
	public void testOrUse() {
		Promise<String> p = Promise.of(() -> 42).map(i -> Integer.toString(i));

		assertEquals("42", p.orUse(null));
		assertTrue(p.isResolved());

		p = Promise.of(() -> 42)
				   .flatMap(i -> Promise.resolved(Integer.toString(i)));

		assertEquals("42", p.orUse(null));
		assertTrue(p.isResolved());

		p = Promise.of(() -> 42).map(i -> Integer.toString(i));
		assertEquals("42", p.orUse(null));
		assertTrue(p.isResolved());

		p = Promise.of(CompletableFuture.supplyAsync(() -> 42))
				   .map(i -> Integer.toString(i));
		assertEquals("42", p.orUse(null));
		assertTrue(p.isResolved());
	}

	/***************************************
	 * Test of {@link Promise#then(Consumer)}.
	 */
	@Test
	public void testThen() {
		Promise.of(() -> TEST_VALUE).then(s -> assertEquals(TEST_VALUE, s));
		Promise.of(() -> null).then(s -> assertNull(s));
	}
}
