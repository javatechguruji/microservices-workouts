# order-service: project revision questions

Use [the implementation guide](../docs/project-docs/shopping-and-fulfillment.md) to answer each question by tracing
the actual source and an observable outcome. These questions replace older
checklists that described already-implemented features as pending.

1. Why does HTTP 202 not mean paid?
2. What happens if payment commits but the worker crashes before PAID?
3. Which lock protects duplicate submission and which protects worker progress?

Compare the implementation with its stated limitations. Future work is maintained
once in [the project roadmap](../Topics.md); a discussion topic is not proof
that the feature exists. For a runnable exercise use
[manual verification](../docs/project-docs/manual-verification.md).
