# The esoco-monads projecto

Travis build status:  
[![Build Status](https://www.travis-ci.org/esoco/esoco-monads.svg?branch=master)](https://www.travis-ci.org/esoco/esoco-monads)

This project contains a small library that defines generic Java interfaces for functors and monads as well as implementations of several standard  Monads. It has a single dependency to the [esoco-common project](https://esoco.github.io/esoco-common/), also a small library which provides some standard functionality. 

Additional information can be found in the [Javadoc](http://esoco.github.io/esoco-monads/javadoc/).

# License

This project is licensed under the Apache 2.0 license (see the LICENSE file for details).  

# Generic Java Monads

The development of these monads was inspired by the article [Functor and monad examples in plain Java](https://www.nurkiewicz.com/2016/06/functor-and-monad-examples-in-plain-java.html). This article provides a very good description of functors and monads and how they would look in Java. Unfortunately, the Java example code it provides is only useful for the demonstration purpose of the article. It cannot be used for implementations right away as it's class declarations are simplified. So I set out to provide a real-life implementation of generic base interfaces and corresponding implementations. Another goal was to keep the monads pure by obeying [the monad laws](https://en.wikipedia.org/wiki/Monad_%28functional_programming%29#Verifying_the_monad_laws) and not exposing or returning the wrapped value.

If you don't have an understanding of monads (and functors), I recommend that you take a look at the article mentioned above. The following explanations assume a basic knowledge of monads and their purpose.  

## Interface [`Functor<T>`](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Functor.html)

This is the base interface for all code in this library, including monads. It provides the generic declaration of a functor, which is a wrapper around a single value with the arbitrary type T. The fundamental features of a functor is that it can be mapped into a functor of another type. This is done by applying a mapping function through the method [Functor<R> map(Function<T,R>)](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Functor.html#map-java.util.function.Function-). 

The usage of functors typically consists of building mapping chains until the value of the starting functor has been mapped into the desired target value. At that point the target value needs to be consumed by some application code. That could be done by invoking the `map` method a last time, evaluating the final value in the mapping function, returning an ignored result, and discarding the resulting functor. But for this common case the functor interface provides the additional method `Functor<T> then(Consumer<T>)` which invokes the argument consumer with the functor's value. It also returns the functor itself to allow method chaining.

Another common property of many functor implementations is that their value may not always be available. This may be either to some invalid state (e.g. value `null`) or some kind of error that occurs while determining the value. In that case the functor value can or should not be mapped (i.e. processed by a function) and the functor needs to be handled differently. For this purpose it provides several methods, all starting with `or` that can be invoked to process such an alternative case. For the method `then` from the previous paragraph the corresponding method for the alternative case is `void orElse(Consumer<Exception>)`. The exception the consumer receives represents the error state the functor is in. An example function chain would look like this:

```java
   createSomeValueInFunctor().[map if necessary].then(v -> <process value>).orElse(e -> <handle exception)
```

In a pure functional program the terminating step when using functors will always be the invocation of a consuming function. But as Java is not a functional language it is sometimes helpful to be able to access the final value directly. For this purpose the functor interface contains the additional methods `orUse(T)`, `orGet(Supplier<T>)`, `orFail()`, and `orThrow(Function<Exception, Exception)`. All these functions return the value of the functor if it is available. If not they do what their respective name says: 

* `orUse`: returns a default value
* `orGet`: invokes a supplier and returns it's result
* `orFail`: throws a checked exception that is related the invalid functor state
* `orThrow`: throws an exception that has been mapped by the argument function from the original problem (and may then be unchecked)

If a functor implementation doesn't have to express an invalid state these methods can simply be implemented by returning the value. In that case these methods will always be processed with the valid state. But having them available in any functor implementation allows truly generic functor handling.

## Interface [`Monad<T,M>`](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Monad.html)

In general, the functor interface is not implemented directly but through the derived interface `Monad`. This is because the `map` function of functors has a problematic limitation: if the mapping function returns a value that is already wrapped in another functor the returned value will be a functor in a functor. In a mapping chain this would cause the result to be a chain of functors which would basically useless in any real-world scenario. And if functors are used consistently it becomes very likely that functions produce functors instead of simple values.

The `Monad` interface solves this by adding the single but important method `M<R> flatMap(Function<T, M<R>>)`. This method expects a function that accepts the wrapped value of type T and produces a new monad with the mapped type R. But instead of doubly wrapping the result as `map` would do, it just returns a new `Monad<R>`. Because of this, wrapping an arbitrary long chain of monads with `flatMap` will always only produce a single (flat) monad that contains a value with the final mapped type. This difference to functors seems simple, but it actually makes monads much more useful than simple functors.

Besides `flatMap` the `Monad` interface only defines one more method: `M<R> and(M<V>, BiFunction<T,V,R>)`. This method combines two monads into a new (flat) one by first mapping the values of both monads with a binary function through the `map` function of the argument monad and then applying `flatMap` on the monad on which `and` is invoked. This sequence of operations is also called lifting - the simple mapping of the second monad is lifted into a new, flat monad. Therefore in some other languages and libraries this method is called `lift` or even `liftM2`. But basically what it does is combining two monads into a new one that contains the evaluation of both. Hence the method name `and` which signals that the resulting monad is a combination of the first **and** the second.

Finally, a note on the generic type of the interface, which is declared as `Monad<T, M extends Monad<?, M>>`. The second type is a self-reference on the monad type of the actual implementation, similar to the self-reference of the Java class `Enum<E extends Enum<E>>`. This self-reference allows to provide a generic base for actual monad implementations. But it is not intended to allow to use monads in a generic way, e.g. by declaring variables or parameters with the type `Monad<T,?>`. Although that may work in certain or even many cases, typically you will use monads by way of one of the concrete monad types provided by the library (or maybe implemented by yourself). Monad implementations will alway set this second type parameter to their own type and only expose the generic value type. An example for such a declaration would be `ConcreateMonad<T> implements Monad<T, ConcreteMonad<?>>`. That the value type in the second type parameter needs to be a wildcard is caused by the restricted generic type system of Java which cannot handle nested types well.

# Monad implementations

The `esoco-monads` library contains a few pre-defined monads for several purposes. There are more monadic types that could be implemented, e.g. equivalents to the collection classes in the `java.util` package could be provided as monads. It would then be possible to flat map collections of a certain type into another format. But as the standard collections API is widely used such a replacement would probably not be used even if it had better functionality. Furthermore, the collections API provides the option to request the elements of a collection as a stream. And Java streams provide `map` and `flatMap` methods, so they can be considered to be monads.

## [`Option<T>`](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Option.html)

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

The method `ifExists` is just a synonym for the generic functor method `then` which has been shown above. The semantics of the latter may be better suited for methods that return options (e.g. `createOption().then(...).orElse`). Instead of `orElse` one of the other `or...()` methods like `orFail` could also be used. `Option` also has a boolean test function called `exists()` and an extended test with `is(Class)` that yields true if the option exits **and** has a certain datatype. The equivalent to `Optional.empty()` is `Option.none`.

`Option` is also fully interoperable with `Optional` through the methods `ofOptional` and `toOptional`. It also contains a factory method  `Option<Collection<T>> ofAll(Collection<Option<T>>)` which converts a collection of options into an optional collection. And last but not least the method `Option<Stream<T>> ofExisting(Stream<Option<T>>)` converts a stream of options into an optional stream of the existing values.

## [`Promise<T>`](https://esoco.github.io/esoco-monads/javadoc/de/esoco/lib/expression/monad/Promise.html)

A `Promise` represents a value that is often not available at the time of declaration, but is instead determined by some additional processing like a background process or a network request. Because it wraps it value it is a good candidate for a monadic interface and several languages contain a promise monad.

Java has no promise class but instead provides a `Future` interface for a similar purpose but which is not a monad. Since Java 8 there is the `Future` implementation `CompletableFuture` which basically obeys the monad laws, but through an uncommon interface. Furthermore the full interface of `CompletableFuture` contains dozens and dozens of methods which can make using it for advanced purposes troublesome.

The `Promise` monad in this library has a straightforward interface - exactly the same that your seen in `Functor`, `Monad`, and `Option`. If you can use one of the monads in `esoco-monads`, you can basically use them all in the same way. The main difference is in the creation of new instances, i.e. in the factory (or unit) functions.
