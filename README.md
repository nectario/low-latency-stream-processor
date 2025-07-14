Low‑Latency Stream Processor
===========================

Context
-------
This is a small proof‑of‑concept built for the interview exercise.
The code combines two design patterns I have used on previous trading desks:

1. Functional pipelines (UBS auto‑roll project)
2. Table‑driven state machines (multi‑phase RFQ negotiation at Nomura)

Together they form a minimal yet production‑shaped core that meets the three
stated goals.

How the three goals are satisfied
---------------------------------
- **Goal 1:** ingest -> process -> forward
  DisruptorEngine receives the message, Dispatcher picks the pipeline,
  the transformed message is forwarded through MessageSender.


- **Goal 2:** 20000 messages per second and a transformation
  ThroughputTest sends 50000 messages in under three seconds on a laptop.
  The pipeline turns the inbound string into “data-msg | processed”, showing
  that the structure can be altered.


- **Goal 3:** latency metrics
  MetricsRecorder records three Micrometer timers: ingest latency,
  processing latency, end‑to‑end latency.  A snapshot is printed at the end of
  the demo run.

Quick start
-----------
    ./gradlew clean test   # unit + integration tests
    ./gradlew run          # demo with ~25k messages per second

Key classes
-----------
Pipeline.java
immutable list of Function<T,T> steps, optional short‑circuit on error.

StateMachine.java
generic (State, Event) lookup that returns a Pipeline and moves to the
next state

Dispatcher.java
connects Disruptor events to the correct pipeline, records metrics, sends
the result downstream

DisruptorEngine.java
1024‑slot single‑producer ring buffer for very low latency

Message flow (one line)
-----------------------
publish() -> Disruptor -> Dispatcher -> Pipeline.execute() -> sender.send()

Why the structure looks more complete than a typical code test
--------------------------------------------------------------
I wanted to show exactly how I structure a low‑latency component when the code
will eventually run on a trading desk in production.  If this feels like
over‑engineering for an interview task, it is simply because I enjoy writing
clean, testable designs.

– Nektarios
