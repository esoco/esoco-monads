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

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;


/********************************************************************
 * A base class for monad implementations that contains generic tests for the 3
 * fundamental monad laws. Subclasses need to implement {@link #testMonadLaws()}
 * and annotate it as a test method. There they should invoke {@link
 * #testAllMonadLaws(String, Function, Function, Function)} with the unit
 * function(s) of the monad type under test and the necessary test input values.
 *
 * <p>The specific generic type <code>M extends Monad&lt;String, M&gt;</code> in
 * the class declaration is needed for the generic test implementation but
 * disallows the compiler to resolve any explicit monad subtype to be used in
 * subclasses. The best way is to completely omit the generic type declaration
 * in a subclass and suppress any raw type warning if necessary.</p>
 *
 * @author eso
 */
public abstract class MonadTest<M extends Monad<String, M>> {

	//~ Static fields/initializers ---------------------------------------------

	/** A string value for testing monad laws. */
	protected static final String TEST_VALUE = "TEST";

	//~ Methods ----------------------------------------------------------------

	/***************************************
	 * Must be implemented to invoke the monad law tests on the monad subtype
	 * under test. Implementations that extend this class as a raw type as
	 * recommended (see class description) also need to suppress unchecked
	 * warnings for this method as the exact monad type is not known on
	 * invocation.
	 *
	 * @throws Exception If the test fails
	 */
	public abstract void testMonadLaws() throws Exception;

	/***************************************
	 * Helper method that returns a function which maps a string into another by
	 * appending a suffix.
	 *
	 * @param  suffix The test-specific suffix to append
	 *
	 * @return The mapping function
	 */
	protected Function<String, String> mapString(String suffix) {
		return s -> s + "-MAPPED-" + suffix;
	}

	/***************************************
	 * Tests all monad laws with default input value and mapping functions.
	 *
	 * @param fUnit The unit function to create a new monad
	 */
	protected void testAllMonadLaws(Function<String, M> fUnit) {
		testAllMonadLaws(TEST_VALUE, fUnit, mapString("1"), mapString("2"));
	}

	/***************************************
	 * Tests all monad laws for the given input value.
	 *
	 * @param testValue The test input value
	 * @param fUnit     The unit function to create a new monad
	 * @param f1        A function that maps values
	 * @param f2        A function that maps values differently
	 */
	protected void testAllMonadLaws(String					 testValue,
									Function<String, M>		 fUnit,
									Function<String, String> f1,
									Function<String, String> f2) {
		testLaw1LeftIdentity(testValue, fUnit, f1);
		testLaw2RightIdentity(testValue, fUnit);
		testLaw3Associativity(testValue, fUnit, f1, f2);
	}

	/***************************************
	 * Test of monad law 1, left identity:
	 *
	 * <pre>M(V).flatMap(F(V)) == F(V)</pre>
	 *
	 * @param testValue The test input value
	 * @param fUnit     The unit function to create a new monad
	 * @param fMap      A function that maps values
	 */
	protected void testLaw1LeftIdentity(String					 testValue,
										Function<String, M>		 fUnit,
										Function<String, String> fMap) {
		// always resolve with orUse() so that
		assertEquals(
			fUnit.apply(testValue).flatMap(fMap.andThen(fUnit)),
			fMap.andThen(fUnit).apply(testValue));
	}

	/***************************************
	 * Test of monad law 2, right identity:
	 *
	 * <pre>M(V).flatMap(M(V)) == M(V)</pre>
	 *
	 * @param testValue The test input value
	 * @param fUnit     The unit function to create a new monad
	 */
	protected void testLaw2RightIdentity(
		String				testValue,
		Function<String, M> fUnit) {
		M monad = fUnit.apply(testValue);

		assertEquals(monad.flatMap(fUnit::apply), monad);
	}

	/***************************************
	 * Test of monad law 3, associativity:
	 *
	 * <pre>M(V).flatMap(F1(V)).flatMap(F2(V')) == M(V).flatMap(F2(F1(V)))</pre>
	 *
	 * @param testValue The test input value
	 * @param fUnit     The unit function to create a new monad
	 * @param f1        A function that maps values
	 * @param f2        A function that maps values differently
	 */
	protected void testLaw3Associativity(String					  testValue,
										 Function<String, M>	  fUnit,
										 Function<String, String> f1,
										 Function<String, String> f2) {
		M monad = fUnit.apply(testValue);

		assertEquals(
			monad.flatMap(f1.andThen(fUnit)).flatMap(f2.andThen(fUnit)),
			monad.flatMap(f1.andThen(f2).andThen(fUnit)));
	}
}
