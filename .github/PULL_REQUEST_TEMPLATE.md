## Summary

<!-- Describe the changes in 2-3 sentences -->

## Type of Change

- [ ] Feature (new functionality)
- [ ] Bug fix (non-breaking change fixing an issue)
- [ ] Refactoring (no functional changes)
- [ ] Documentation update
- [ ] Infrastructure / DevOps
- [ ] Dependency update

## Checklist

### Code Quality
- [ ] Code follows SOLID principles
- [ ] Appropriate design patterns applied
- [ ] No code duplication (DRY)
- [ ] Java 21 features used where applicable (Records, Sealed Classes, Pattern Matching, Virtual Threads)
- [ ] Immutable objects preferred

### Logging & Error Handling
- [ ] Structured logging with appropriate levels (ERROR/WARN/INFO/DEBUG)
- [ ] MDC correlation ID propagated
- [ ] No sensitive data in logs
- [ ] Custom exceptions with proper hierarchy
- [ ] RFC 7807 Problem Details for API errors

### Testing
- [ ] Unit tests with BDD-style naming
- [ ] Integration tests with Testcontainers (if applicable)
- [ ] Test coverage >= 80%
- [ ] Edge cases covered

### Documentation
- [ ] Javadoc on public APIs
- [ ] ADR written (if architectural decision)
- [ ] OpenAPI spec updated (if API change)
- [ ] README updated (if applicable)

### Database
- [ ] Flyway migration added (if schema change)
- [ ] Indexes considered for new queries
- [ ] No N+1 query issues

### Security
- [ ] Input validation with Bean Validation
- [ ] No hardcoded secrets
- [ ] Authorization checks in place

## Architecture Decision Records

<!-- Link any new ADRs -->

## Screenshots / Diagrams

<!-- If applicable, add screenshots or diagrams -->
