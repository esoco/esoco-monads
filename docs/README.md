# The esoco-monads project

Travis build status:  
[![Build Status](https://www.travis-ci.org/esoco/esoco-monads.svg?branch=master)](https://www.travis-ci.org/esoco/esoco-monads)

This project contains a small library that defines generic Java interfaces for functors and monads as well as implementations of several standard Monads. It has a single dependency to the [esoco-common project](https://esoco.github.io/esoco-common/), also a small library which provides some standard functionality.

Additional information can be found in the [Javadoc](http://esoco.github.io/esoco-monads/javadoc/).

# License

This project is licensed under the Apache 2.0 license (see the LICENSE file for details).

# Generic Java Monads

The development of these monads was inspired by the article [Functor and monad examples in plain Java](https://www.nurkiewicz.com/2016/06/functor-and-monad-examples-in-plain-java.html). This article provides a very good description of functors and monads and how they would look in Java. Unfortunately, the Java example code it provides is only useful for the demonstration purpose of the article. It cannot be used for implementations right away as it's class declarations are simplified. So I set out to provide a real-life implementation of generic base interfaces and corresponding implementations. Another goal was to keep the monads pure by obeying [the monad laws](https://en.wikipedia.org/wiki/Monad_%28functional_programming%29#Verifying_the_monad_laws) and not exposing or returning the wrapped value.

If you don't have an understanding of monads (and functors), I recommend that you take a look at the article mentioned above. The following explanations assume a basic knowledge of monads and their purpose.

## Interface [Functor&lt;T&gt;](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Functor.html)

This is the base interface for all code in this library, including monads. It provides the generic declaration of a functor, which is a wrapper around a single value with the arbitrary type T. The fundamental features of a functor is that it can be mapped into a functor of another type. This is done by applying a mapping function through the method [Functor<R> map(Function<T,R>)](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Functor.html#map-java.util.function.Function-).

The usage of functors typically consists of building mapping chains until the value of the starting functor has been mapped into the desired target value. At that point the target value needs to be consumed by some application code. That could be done by invoking the `map` method a last time and just discard the mapped result. But for this common case the functor interface provides the additional method `Functor<T> then(Consumer<T>)` which invokes the argument consumer with the wrapped value. It also returns the functor itself to allow method chaining.

Another common property of many functor implementations is that their value may not always be available. This may be either due to some invalid state (like the non-value `null`) or to some kind of error that occurs while determining the value. In such a case the functor value can not be mapped and the functor needs to be handled differently. For the method `then` from the previous paragraph the corresponding method for the invalid case is `orElse(Consumer<Exception>)`. The exception the consumer receives represents the error state the functor is in. An example function chain would look like this:

```java
createSomeValueInFunctor()[.map() as often as necessary].then(v -> <process value>).orElse(e -> <handle exception>)
```

When reading this example it is important to remember that the two cases are mutually exclusive and always only one will be evaluated: if the functor is valid, `then()` will be called. If it is invalid because the value is not available or cannot be produced, the method `orElse()` will be invoked. Just like in a regular if-else statement.

In a pure functional program the terminating step when using functors would always be the invocation of a consuming function. But as Java is not a functional language it can be helpful to have access to the value at the end of a chain directly. In that case it is also necessary to be aware of a possible invalid state. For this purpose the functor interface defines additional methods starting with `or`. All these functions return the value of the functor if it is available. If not, they do what their respective name says:

- `orUse`: returns the argument default value
- `orGet`: invokes the given supplier and returns it's result
- `orFail`: throws a checked exception that is related to the invalid functor state
- `orThrow`: throws an exception that has been mapped by the argument function from the original problem (and may then be unchecked)

If a functor implementation doesn't have to express an invalid state these methods will always be processed with the valid state and just return the resulting value. But having them available in any functor implementation allows truly generic functor handling.

## Interface [Monad&lt;T,M&gt;](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Monad.html)

In general, the functor interface is not implemented directly but through the derived interface `Monad`. This is because the `map` function of functors has a limitation: if the mapping function returns a value that is already wrapped in a functor of the same type the returned value will be a functor in a functor. In a mapping chain this would cause the result to be a chain of functors which would basically be useless because it would be very difficult to unwrap. And if functors are used frequently it becomes very likely that functions will produce functors instead of simple values.

The `Monad` interface solves this by adding the method `M<R> flatMap(Function<T, M<R>>)`. This method expects a function that accepts the wrapped value of type T and produces a new monad with the mapped type R. But instead of wrapping the result again as `map` would do, it just returns a new `Monad<R>`. Because of this, wrapping an arbitrary long chain of monads with `flatMap` will always only produce a single (flat) monad that contains a value with the final mapped type. This difference to functors seems simple, but it actually makes monads much more useful than plain n functors.

Besides `flatMap` the `Monad` interface only defines one more method: `M<R> and(M<V>, BiFunction<T,V,R>)`. This method combines two monads into a new (flat) one by first mapping the values of both monads with a binary function through the `map` function of the argument monad and then applying `flatMap` on the monad on which `and` is invoked. This sequence of operations is also called lifting - the simple mapping of the second monad is lifted into a new, flat monad. Therefore in some other languages and libraries this method is called `lift` or even `liftM2`. But basically what it does is combining two monads into a new one that contains the evaluation of both. Hence the method name `and` which signals that the resulting monad is a combination of the first **and** the second.

> **A note on the generic type of the Monad interface**, which is declared as `Monad<T, M extends Monad<?, M>>`: The second type is a self-reference on the monad type of the actual implementation, similar to the self-reference of the Java class `Enum<E extends Enum<E>>`. This self-reference allows to provide a generic base for actual monad implementations. But it is not intended to allow to use monads in a generic way, e.g. by declaring variables or parameters with the type `Monad<T,?>`. Although that may work in certain cases, typically you will use monads in the form of a concrete monad type (either provided by the library or maybe by yourself). Monad implementations will alway set this second type parameter to their own type and only expose the generic value type. An example for such a declaration would be `SomeMonad<T> implements Monad<T, SomeMonad<?>>`. That the value type in the second type parameter needs to be a wildcard is caused by the restricted generic type system of Java which cannot handle nested types well.

# Monad implementations

Besides the interfaces described above, the `esoco-monads` library contains some pre-defined monads. There are more monadic types that could be implemented, e.g. equivalents to the collection classes in the `java.util` package could be provided as monads. It would then be possible to flat map collections of a certain type into another format. But as the standard collections API is widely used such a replacement would probably not be used even if it had better functionality. Furthermore, the collections API provides the option to request the elements of a collection as a stream. And Java streams provide `map` and `flatMap` methods, so they can be considered to be monads.

## [Option&lt;T&gt;](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Option.html)

Speaking of monads in the Java libraries, the `java.util` package also contains the controversial class `Optional<T>`. Controversial, because it is disputed that it is a real (or proper) monad. This is because [`Optional` breaks some of the monad laws](https://www.sitepoint.com/how-optional-breaks-the-monad-laws-and-why-it-matters/). The monad laws define the functional requirements that a monad needs to fulfill to be usable in a pure functional context. If the laws are not obeyed the monad may behave unexpected.

The main purpose of `Optional` is intended to make it easier for programmers to work with `null` values and to prevent the typical problems that arises from overlooking `null`, especially the dreaded `NullPointerException` (NPE). To do so, `Optional` wraps an arbitrary value that can be NULL and allows to apply `map` and `flatMap` to it. The main problem arises from the fact that `Optional` treats `null` as a special value and doesn't perform any mapping (chain) if the wrapped value is `null`. This means that mapping chains may terminate unexpectedly und especially unpredictably. Furthermore this violates monad laws like associativity, causing differently built monad chains to also behave differently.

The main argument for the implementation of `Optional` is that it is **intended** to prevent NPE. But still, the non-compliant behavior as a monad means that `Optional` may cause other problems, especially if used in a pure functional context. In additional it has some other constraints that can make it's everyday usage a bit annoying. So, can it be done differently? Let me introduce you to to `Option<T>`.

The `Option` monad is quite similar to `java.util.Optional` as it is targeted at the same problem: to avoid "running into" `null` unprotectedly. But it also fully obeys all monad laws and is therefore a "real" monad that can be used in every combination necessary without behaving differently. One main difference is already visible when creating new instances: the factory method (or unit method in monad terms) `Option.of(value)` is the same as `Optional.ofNullable(value)`, while the method `Optional.of(value)` would throw a `NullPointerException` instead. The equivalent of that is `Option.ofRequired(value)`. The reason for swapping the meaning of the `of()` method is that options mainly are intended to handle optional value (that may or may not be `null`). Therefore `of()` is probably the most used factory method. A semantic variant is the factory method `option` which does the same, but can be used as a static import (like `variable = option(value)`). The static import method for required values is `nonNull(value)`.

But how does `Option` manage to obey the monad laws? As explained, `Optional` doesn't map if the wrapped value is `null.` to avoid causing an NPE. `Option` doesn't make this distinction and always performs (flat) mapping, even if it's `exist()` method returns `false`. It expects any mapping to be performed on an option to be able to also handle the `null` case as it is a valid state in it's specification. But what if the mapping function cannot handle a `null` value? In that case it makes the assumption that any NPE thrown by a mapping function is considered as a `null` result, causing a non-existing option to be returned. Any other type of exception that is thrown by a mapping function will be re-thrown and this terminate the mapping immediately.

Obviously this kind of handling `null` values is also a compromise as there are cases conceivable where invoking the mapping with `null` may have unexpected side effects. For example, instead of not being invoked at all, invoking the mapping chain with `null` may cause an inconsistent state because the chain will only be evaluated partially. But that is mainly a question of application design and thoughtful consideration of how to handle uninitialized values. `Option` provides an alternative to `Optional` and allows to handle non-existing values with a more functional approach.

Another optimization of `Option` is that it allows to work with optional values in a more consistent way. For example, `Optional` provides a method `ifPresent(Consumer<T>)` which executes a consuming function if the optional value exists. But there is no equivalent for the case of non-existence (`null`). If the code needs to react to both cases there is no other way than to build a conditional statement that uses the test method `isPresent()` instead. `Option` allows to build a chained expression because it returns itself from the corresponding methods. A conditional chain would look like this:

```java
someOption.ifExists(v -> <handle value>).orElse(e -> <handle non-existing>);
```

The method `ifExists` is just a synonym for the generic functor method `then` which has been shown above. The semantics of the latter may be better suited for methods that return options (e.g. `getSomeOption().then(...).orElse`). Instead of `orElse` one of the other `or...()` methods like `orFail` could also be used. `Option` also has a boolean test function called `exists()` and an extended test with `is(Class)` that yields true if the option exits **and** has a certain datatype. The equivalent to `Optional.empty()` is `Option.none`.

Another example for a more consistent interface is `Optional.get()` which returns the wrapped value. But only if it is not `null`, because in that case this method throws an unchecked `NoSuchElementException`. This may come as a surprise to new users and it is contradictory to the main purpose of `Optional` to get rid of unexpected exceptions. With `Option` (and all other functors in this library) there are no such surprises. If you **want to** explicitly throw a `NoSuchElementException` the expression would be something like this:

```java
someVariable = someOption.orThrow(NoSuchElementException::new);
```

Or even simpler, use `someOption.orFail()` which throws a `NullPointerException`. But in any case, the expression makes clear what is to expect from the code.

`Option` is fully interoperable with `Optional` through the methods `ofOptional` and `toOptional`. Furthermore, it contains two factory methods to convert collections and streams: `Option<Collection<T>> ofAll(Collection<Option<T>>)` converts a collection of options into an optional collection. And `Option<Stream<T>> ofExisting(Stream<Option<T>>)` turns a stream of options into an optional stream of values.

## [Promise&lt;T&gt;](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Promise.html)

A `Promise` represents a value that is often not available at the time of declaration, but is instead determined by some additional processing like a background process or a network request. Because it wraps a (promised) value it is a good candidate for a monadic interface and several programming languages contain a promise monad.

Instead of a promise class Java provides a `Future` interface for a similar purpose, but which is not a monad. Java 8 introduced the `Future` implementation `CompletableFuture` which has no specific monad API but [satisfies the monad laws](https://gist.github.com/lestard/e28fb8a340737ffd9623). Unfortunately it does so through a complicated and copious interface (defined in `CompletionStage`) which can make using it cumbersome.

The `Promise` monad in this library has a straightforward interface - exactly the same that was presented before in `Functor` and `Monad`, and similar to `Option`. If you know one of the monads in `esoco-monads`, you can basically use them all in the same way. The main difference is in the creation of new instances, i.e. in the factory (or unit) methods.

Promises typically represent the result of parallel, multi-threaded code executions. Developing such a multi-threaded environment is not a trivial task. Fortunately, Java already comes with well-tested threading libraries, including the `CompletableFuture` from Java 8. Therefore, `Promise` is not a new implementation of parallel execution, but simply adds a monadic interface upon the Java libraries. The actual code execution is done by using `CompletableFuture` and related code internally.

A new promise for a value that needs to be computed can be created with `Promise.of(Supplier)`. The argument is a supplier function that generates the promised value somehow. The implementation will then hand this function over to a background thread (by means of a `CompletableFuture`) and return a `Promise` that wraps the future result of the function. In special cases where more control over the asynchronous processing is needed, the method `Promise.of(CompletionStage)` accepts a specific future implementation that runs the processing.

A promise can be arbitrarily mapped and flat-mapped without affecting the background computation. Each new mapping stage will simply be added to the background processing and a new promise with the mapped value type will be returned. The same is true for `then(Consumer)` which appends some computation and returns a promise of the same type. Similarly, `orElse(Consumer<Exception>)` will asynchronously handle any exception that may occur in the background code. Asynchronously means that the background processing will continue and invoke the given function or consumer after completion or failure. The code that is invoking these methods will not block the program until execution has finished but simply continue to run. This is the same behavior as with every other library for asynchronous execution.

To access the promised value in a synchronous, blocking way the standard functor methods that return the wrapped value (the methods starting with `or`) can be used. For example, the call `somePromise.orFail()` will block the calling thread until the computation has finished and then either return the promised value or throw an exception if the the background processing failed. A synchronous method specific to the `Promise` class is `await()` which blocks until the promise has resolved or throws an exception if the promise failed.

Sometimes the promised value may already be available and it only needs to be wrapped into a `Promise` object, like for methods that expect a promise. In that case the method `Promise.resolved(value)` can be used to avoid starting a background process. Similarly, if for some reason it is already known that generating a value would fail or has failed, `Promise.failure(Exception)` can be used to create a failed promise.

By default a promise will wait indefinitely until it is resolves. As this may not always be desirable the method `withTimeout()` returns a new promise instance that will wait at maximum the given time for resolving when queried for it's value. The following example would cause an exception if the value is not available after 10 seconds - it will **not** use the default value in that case!

```java
somePromise.withTimeout(10, TimeUnit.seconds).orUse(defaultValue);
```

Sometimes it may also be necessary to stop a background computation which can be achieved by calling the method `Promise.cancel()`. Cancelling will not interrupt the actual code in a promise as that is not supported by the underlying implementation of `CompletableFuture`. It will only be able to stop the traversing of chained promise. Therefore, promises are not suitable for long-running background processes but should rather be used for things like deferred method calls or similar. The current state of a promise can be queried with `isResolved()` or in more detail with `getState()`. And the method `toFuture()` provides interoperability with APIs that interact with instances of the `Future` interface.

## [Try&lt;T&gt;](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Try.html)

As shown, the `Promise` monad allows to execute arbitrary code and handle the result or errors in a standard, monadic way. But sometimes running the code in the background is not necessary and would mean unneeded complexity. For this purpose this library provides the `Try` monad. The factory method is `Try.now(Supplier)`, but unlike `Promise.of(Supplier)` it will execute the argument code immediately. The instance can then be mapped and processed as needed with the standard functor and monad methods. A variant is `Try.run(Runnable)` which returns a `Try<Void>` that is then only used for generic error handling. Successful executions and errors can also be created with `Try.success(value)` and `Try.failure(Exception)`. The code in a try is always only executed once. Afterwards, the same result or error will be returned on every invocation of a querying method.

Another factory method is `Try.lazy(Supplier)` which doesn't execute the supplier immediately like `now()`, but only when the try is evaluated for the first time. `now()` is typically used for monadic processing with error handling like in this example:

```java
Try.now(this::getConfigValue).map(this::parseValue).then(v -> initFromConfig(v)).orFail();
```

On the other hand, `lazy()` can be used for delayed execution of some code, for example by handling a lazy try over to another part of an application:

```java
setConfig(Try.lazy(this::getConfigValue).map(this::parseValue));
```

The example `setup(Try)` method could query (and thus execute) the argument try when necessary and use the standard methods to access the result and perform error handling as needed. It could even place the querying inside a promise and perform it in the background if desired.

A lazy try will remain unresolved as long as only the mapping methods `map`, `flatMap`, `then`, and `orElse` are called on it.

## [Call&lt;T&gt;](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Call.html)

The `Call` monad is quite similar to `Try`. But instead of executing the wrapped code only once, a call will execute it every time it is queried with the consuming methods. An invocation of `orUse`, `orGet`, `orFail`, and `orThrow` will cause a re-execution of the supplier, hence yielding a new result if the supplier is implemented accordingly. Calls can be arbitrarily mapped (including `then` and `orElse`) without being executed. Only consuming a call will invoke the wrapped code or perform error handling.

Because the main purpose of this monad is to provide repeatable function executions it also provides the method `execute()`, which executes the call once and triggers any mappings or throws a `RuntimeException` on errors. And `execute(Consumer<Exception>))` also executes the call, but forwards errors to the given error handler.

A new call instance can be created with `Call.of(Supplier)`. `Call.error(Exception)` creates a failed instance that doesn't contain evaluated code. The methods `toTry()` and `toLazy()` convert a call to a corresponding, single-execution `Try`.

`Call` is also a good example for monads that are used as components inside other monads, as the handling of the call executions is delegated to internal instances of the `Try` monad. And lazy tries use an `Option<Try<T>>` internally to model the delayed execution.