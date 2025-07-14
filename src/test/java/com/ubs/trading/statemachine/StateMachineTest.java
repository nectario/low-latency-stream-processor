package com.ubs.trading.statemachine;

import static org.assertj.core.api.Assertions.*;

import com.ubs.trading.pipeline.Pipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit‑tests for {@link StateMachine}. */
class StateMachineTest {

  /* --------------------------- fixtures --------------------------- */

  private enum State {
    IDLE,
    ACTIVE,
    ERROR
  }

  private enum Event {
    HELLO,
    DATA,
    FAIL
  }

  private static final Pipeline<String> HelloPipeline = Pipeline.build("hello", true, s -> "HELLO");
  private static final Pipeline<String> DataPipeline = Pipeline.build("data", true, s -> s + "|ok");
  private static final Pipeline<String> ErrorPipeline = Pipeline.build("err", true, s -> "ERR");

  private static StateMachine<State, Event> fsm() {
    return new StateMachine<State, Event>(State.IDLE) // 👈 type witness
        .add(State.IDLE, Event.HELLO, State.ACTIVE, HelloPipeline)
        .add(State.ACTIVE, Event.DATA, State.ACTIVE, DataPipeline)
        .add(State.ACTIVE, Event.FAIL, State.ERROR, ErrorPipeline);
  }

  /* ---------------------------- tests ----------------------------- */

  @Test
  @DisplayName("Initial state is preserved until the first event")
  void retainsInitialState() {
    assertThat(fsm().state()).isEqualTo(State.IDLE);
  }

  @Test
  @DisplayName("Transition returns the correct pipeline and mutates state")
  void transitionReturnsPipeline() {
    StateMachine<State, Event> sm = fsm();
    Pipeline<String> p = sm.onEvent(Event.HELLO);

    assertThat(p).isSameAs(HelloPipeline);
    assertThat(sm.state()).isEqualTo(State.ACTIVE);
  }

  @Test
  @DisplayName("Undefined transition throws IllegalStateException")
  void undefinedTransitionThrows() {
    StateMachine<State, Event> sm = fsm(); // still IDLE
    assertThatThrownBy(() -> sm.onEvent(Event.DATA))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No transition");
  }
}
