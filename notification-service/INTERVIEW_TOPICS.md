# notification-service: project revision questions

Use [the implementation guide](../docs/kafka/kafka-notes-scenarios.md) to answer each question by tracing
the actual source and an observable outcome. These questions replace older
checklists that described already-implemented features as pending.

1. Why insert before acknowledging?
2. How does the unique event ID protect replay?
3. Why does an inbox read work without Kafka while new updates do not?

Compare the implementation with its stated limitations. Future work is maintained
once in [the project roadmap](../Topics.md); a discussion topic is not proof
that the feature exists. For a runnable exercise use
[manual verification](../docs/project-docs/manual-verification.md).
