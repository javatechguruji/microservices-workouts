# payment-service: project revision questions

Use [the implementation guide](../docs/project-docs/shopping-and-fulfillment.md) to answer each question by tracing
the actual source and an observable outcome. These questions replace older
checklists that described already-implemented features as pending.

1. Why is an identical successful retry returned instead of rejected?
2. Why does an outbox still require duplicate handling?
3. What does the service unit test not prove about a database lock?

Compare the implementation with its stated limitations. Future work is maintained
once in [the project roadmap](../Topics.md); a discussion topic is not proof
that the feature exists. For a runnable exercise use
[manual verification](../docs/project-docs/manual-verification.md).
