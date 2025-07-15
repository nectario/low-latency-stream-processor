Low‑Latency Stream Processor
===========================

Context
-------
Small proof‑of‑concept for the interview exercise.  Two patterns I have used
on trading desks are combined:

1. Functional pipelines (UBS auto‑roll project)
2. Table‑driven state machines (multi‑phase RFQ negotiation at Nomura)

Recent change: the entire core is now generic, so any domain object
(e.g. TradeEvent) can flow through Disruptor → StateMachine → Pipeline
without casting.  A FIX‑Execution‑Report demo test shows this in practice.

How the three goals are satisfied
---------------------------------
- **Goal 1:** ingest → process → forward  
  DisruptorEngine receives each payload, Dispatcher selects the correct
  Pipeline, and the transformed payload is forwarded via MessageSender.

- **Goal 2:** ≥ 20,000 messages per second + transformation  
  ThroughputTest publishes **50 042** messages in **2 seconds**
  (≈ 25 k msg/s) on a laptop.  
  The pipeline appends “ | processed” to the inbound string, proving a
  structural transformation occurs.

- **Goal 3:** latency metrics  
  MetricsRecorder registers three Micrometer timers:  
  • `msg.ingest.latency`  
  • `msg.proc.latency`  
  • `msg.e2e.latency`  
  A snapshot like  
  `HistogramSnapshot{count=50042, mean=19µs, max=5.4ms, …}`  
  is printed at the end of the demo run.


Quick start
-----------
    ./gradlew clean test   # unit + integration tests
    ./gradlew run          # demo with ~25k messages per second

Key classes
-----------
EventEnvelope<T>
Pre‑allocated container reused by the Disruptor ring.

DisruptorEngine<T>
1024‑slot single‑producer ring buffer, generic publish(T).

Pipeline<T>
Immutable list of Function<T,T> steps, optional short‑circuit.

StateMachine<S,T>
Table‑driven lookup that returns a Pipeline<T> and moves to next state.

Dispatcher<S,T>
Bridges Disruptor to StateMachine, executes the pipeline, records metrics,
forwards via MessageSender<T>.

Message flow (single line)
--------------------------
publish() → Disruptor → Dispatcher → Pipeline.execute() → sender.send()

Why the structure looks more complete than a typical code test
--------------------------------------------------------------
I wanted to show exactly how I would structure a low‑latency component that
could move straight into a trading‑desk codebase.  If this feels like
over‑engineering for an interview task, it is simply because I enjoy writing
clean, testable designs.

–Nektarios
