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
import de.esoco.lib.expression.ThrowingSupplier;

import java.time.LocalDate;

import java.util.Arrays;
import java.util.function.Function;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/********************************************************************
 * Test of {@link Call}.
 *
 * @author eso
 */
@SuppressWarnings("rawtypes")
public class CallTest extends MonadTest {

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Test of {@link Call#and(Monad, java.util.function.BiFunction)}.
	 */
	@Test
	public void testAnd() {
		LocalDate today = LocalDate.now();

		Call<LocalDate> aLocalDateCall =
			Call.of(() -> today.getYear())
				.and(Call.of(() -> today.getMonth()), (y, m) ->
						Pair.of(y, m))
				.and(
					Call.of(() -> today.getDayOfMonth()),
					(ym, d) -> LocalDate.of(ym.first(), ym.second(), d));

		aLocalDateCall.then(d -> assertEquals(today, d));
	}

	/***************************************
	 * Test of {@link Call#equals(Object)}.
	 */
	@Test
	public void testEquals() {
		ThrowingSupplier<String> fTestSupplier = () -> "TEST";

		assertEquals(Call.of(fTestSupplier), (Call.of(fTestSupplier)));
		assertNotEquals(Call.of(fTestSupplier), Call.of(() -> "TEST2"));
		assertNotEquals(
			Call.of(fTestSupplier),
			Call.error(new Exception("ERROR")));
	}

	/***************************************
	 * Test of {@link Call#error(Exception)}.
	 */
	@Test
	public void testError() {
		Call<String> failing = Call.error(new Exception("ERROR"));

		assertEquals("FAILED", failing.map(v -> "SUCCESS").orUse("FAILED"));
	}

	/***************************************
	 * Test of {@link Call#execute()}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testExecute() throws Throwable {
		Call.of(() -> "42")
			.map(Integer::parseInt)
			.then(i -> assertTrue(i == 42))
			.execute();

		try {
			Call.<String>error(new Exception("ERROR"))
				.then(s -> fail())
				.execute();
			fail();
		} catch (RuntimeException e) {
			assertEquals("ERROR", e.getCause().getMessage());
		}

		try {
			Call.<String>error(new Exception("ERROR"))
				.then(s -> fail())
				.execute(e -> { throw new RuntimeException(e); });
			fail();
		} catch (RuntimeException e) {
			System.out.printf("ERR%s\n", e);
			assertEquals("ERROR", e.getCause().getMessage());
		}
	}

	/***************************************
	 * Test of {@link Call#flatMap(java.util.function.Function)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testFlatMap() throws Throwable {
		Call.of(() -> "42")
			.flatMap(s -> Call.of(() -> Integer.parseInt(s)))
			.then(i -> assertTrue(i == 42))
			.orFail();
	}

	/***************************************
	 * Test of {@link Call#equals(Object)}.
	 */
	@Test
	public void testHashCode() {
		ThrowingSupplier<String> fTestSupplier = () -> "TEST";

		assertTrue(
			Call.of(fTestSupplier).hashCode() ==
			Call.of(fTestSupplier).hashCode());
	}

	/***************************************
	 * Test of {@link Call#map(Function)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testMap() throws Throwable {
		Call.of(() -> "42")
			.map(Integer::parseInt)
			.then(i -> assertTrue(i == 42))
			.orFail();
	}

	/***************************************
	 * {@inheritDoc}
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Test
	public void testMonadLaws() {
		// explicit monad law tests for calls because they need to be resolved
		// before equality tests
		Function<String, String>	   f1     = mapString("1");
		Function<String, String>	   f2     = mapString("2");
		Function<String, Call<String>> toCall = v -> Call.of(() -> v);

		Call<String> call = Call.of(() -> TEST_VALUE);

		assertEquals(
			call.flatMap(f1.andThen(toCall)).toTry(),
			f1.andThen(toCall).apply(TEST_VALUE).toTry());
		assertEquals(call.flatMap(toCall).toTry(), call.toTry());
		assertEquals(
			call.flatMap(f1.andThen(toCall))
			.flatMap(f2.andThen(toCall))
			.toTry(),
			call.flatMap(f1.andThen(f2).andThen(toCall)).toTry());
	}

	/***************************************
	 * Test of {@link Call#ofAll(java.util.Collection)}.
	 *
	 * @throws Throwable
	 */
	@Test
	public void testOfAll() throws Throwable {
		Call.ofAll(
				Arrays.asList(
					Call.of(() -> 1),
					Call.of(() -> 2),
					Call.of(() -> 3)))
			.then(c -> assertEquals(Arrays.asList(1, 2, 3), c))
			.orFail();

		Call.ofAll(
				Arrays.asList(
					Call.of(() -> 1),
					Call.of(() -> 2),
					Call.of(() -> 3),
					Call.error(new Exception("ERROR"))))
			.then(c -> fail())
			.orElse(e -> assertEquals("ERROR", e.getMessage()));
	}

	/***************************************
	 * Test of {@link Call#orElse(Runnable)}, {@link Call#orUse(Object)}, {@link
	 * Call#orFail()}.
	 */
	@Test
	public void testOr() {
		Call<Object> aError = Call.error(new Exception("ERROR"));

		aError.then(v -> fail())
			  .orElse(e -> assertEquals("ERROR", e.getMessage()));
		assertEquals("DEFAULT", aError.orUse("DEFAULT"));

		try {
			aError.orThrow(Function.identity());
			fail();
		} catch (Throwable e) {
			assertEquals(Exception.class, e.getClass());
			assertEquals("ERROR", e.getMessage());
		}

		try {
			aError.orFail();
			fail();
		} catch (Throwable e) {
			assertEquals(Exception.class, e.getClass());
			assertEquals("ERROR", e.getMessage());
		}
	}
}
