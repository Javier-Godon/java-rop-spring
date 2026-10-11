# rop-spring

Spring integration for `rop-core`: transactions as an `ExecutionContext`, mapping of `Result` failures
to HTTP responses, and Spring Boot auto-configuration.

```xml
<dependency>
    <groupId>es.bluesolution</groupId>
    <artifactId>rop-spring</artifactId>
    <version>${rop.version}</version>
</dependency>
```

| Package | Types |
|---|---|
| `es.bluesolution.rop.spring.execution` | `TransactionExecutionContext` (`of`, `readOnly`, `withIsolation`) |
| `es.bluesolution.rop.spring` | `RailwayResponseBuilder`, `RailwayErrorResponseBuilder`, `ErrorResponse`, `HttpStatusMapper`, `ExceptionToErrorCodeMapper`, `ResultFailures`, `ErrorMessageFormatter` |
| `es.bluesolution.rop.spring.autoconfigure` | `RopAutoConfiguration` |

```java
@Bean
ExecutionContext transactionalExecutionContext(PlatformTransactionManager txManager) {
    return TransactionExecutionContext.of(txManager);
}

@PostMapping("/orders")
ResponseEntity<Object> place(@RequestBody PlaceOrderRequest request) {
    return RailwayResponseBuilder.buildResponse(handler.handle(request.toCommand()), HttpStatus.CREATED);
}
```

**Auto-configuration.** `RopAutoConfiguration` is registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; no component scan
of `es.bluesolution.rop` is needed. It creates an `ErrorMessageFormatter` (backs off if you define one)
whose mode comes from `railway.error.response.mode` = `PRODUCTION` (default) | `DETAILED` | `DEVELOPMENT`.

**Optional dependencies.** Required: `spring-context`, `spring-tx`, `spring-web`, `spring-boot-autoconfigure`.
Spring MVC/Servlet, Spring Security, Jakarta Persistence, Bean Validation and JAX-RS are optional: their
exceptions are mapped by class name only when present on the classpath.
