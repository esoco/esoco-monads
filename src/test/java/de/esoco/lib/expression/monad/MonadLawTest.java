//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// This file is a part of the 'esoco-monads' project.
// Copyright 2019 Elmar Sonnenschein, esoco GmbH, Flensburg, Germany
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

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/********************************************************************
 * A base class for monad implementations that contains generic tests for the
 * monad laws. Subclasses need only to provide the unit function for the monad
 * subclass under test in their constructor. The test of the monad laws will
 * then be executed automatically with default values. If necessary the law
 * tests can be invoked with other parameters from additional test methods for
 * the testing of special cases (e.g. NULL values).
 *
 * <p>The specific generic type <code>M extends Monad&lt;String, M&gt;</code> in
 * the class declaration is needed for the generic test implementation but
 * disallows the compiler to resolve any explicit monad subtype to be used in
 * subclasses. The best way is to completely omit the generic type declaration
 * in a subclass and suppress any raw type warning if necessary.</p>
 *
 * @author eso
 */
public abstract class MonadLawTest<M extends Monad<String, M>> {

	//~ Static fields/initializers ---------------------------------------------

	private static final String TEST_VALUE    = "TEST";
	private static final String MAPPED_SUFFIX = "-MAPPED";

	//~ Instance fields --------------------------------------------------------

	private Function<String, M> fUnit;

	//~ Constructors -----------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 *
	 * @param fUnit A unit function that produces a new monad instance from a
	 *              string input value
	 */
	public MonadLawTest(Function<String, M> fUnit) {
		this.fUnit = fUnit;
	}

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Creates a new instance.
	 */
	@Test
	public void testMonadLaws() {
		testLaw1LeftIdentity(TEST_VALUE, mapString(""));
		testLaw2RightIdentity(TEST_VALUE);
		testLaw3Associativity(TEST_VALUE, mapString("1"), mapString("2"));
	}

	/***************************************
	 * Test of monad law 1, left identity:
	 *
	 * <pre>M(V).flatMap(F) == F(V)</pre>
	 *
	 * @param testValue The test input value
	 * @param mapValue  A function that maps the value into a test value
	 */
	protected void testLaw1LeftIdentity(
		String					 testValue,
		Function<String, String> mapValue) {
		assertEquals(
			fUnit.apply(testValue)
			.flatMap(v -> fUnit.apply(mapValue.apply(v))),
			fUnit.apply(mapValue.apply(testValue)));
	}

	/***************************************
	 * Test of monad law 2, right identity:
	 *
	 * <pre>M(V).flatMap(M.unit) == M(V)</pre>
	 *
	 * @param testValue The test input value
	 */
	protected void testLaw2RightIdentity(String testValue) {
		M monad = fUnit.apply(testValue);

		assertEquals(monad.flatMap(fUnit::apply), monad);
	}

	/***************************************
	 * Test of monad law 3, associativity:
	 *
	 * <pre>(M(V).flatMap(F1)).flatMap(F2) == M(V).flatMap(F2(F1(V)))</pre>
	 *
	 * @param testValue The test input value
	 * @param f1        A function that is used to map the value first
	 * @param f2        A function that is used to map the value a second time
	 */
	protected void testLaw3Associativity(String					  testValue,
										 Function<String, String> f1,
										 Function<String, String> f2) {
		M monad = fUnit.apply(TEST_VALUE);

		assertEquals(
			monad.flatMap(v -> fUnit.apply(f1.apply(v)))
			.flatMap(v -> fUnit.apply(f2.apply(v))),
			monad.flatMap(v -> fUnit.apply(f2.apply(f1.apply(v)))));
	}

	/***************************************
	 * Helper method that returns a function that maps a string into another by
	 * appending a suffix.
	 *
	 * @param  suffix The test-specific suffix to append
	 *
	 * @return The mapping function
	 */
	private Function<String, String> mapString(String suffix) {
		return s -> s + MAPPED_SUFFIX + suffix;
	}
}
