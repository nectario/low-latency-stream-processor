package com.ubs.trading.statemachine;

import com.ubs.trading.pipeline.Pipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** Unit‑tests for {@link StateMachine}. */
class StateMachineTest {

    /* ----------------------------------------------------------- *
     *  Fixtures
     * ----------------------------------------------------------- */

    private enum State { IDLE, ACTIVE, ERROR }

    private static final Pipeline<String> HELLO_PIPELINE =
            Pipeline.build("hello", true, s -> "HELLO");

    private static final Pipeline<String> DATA_PIPELINE =
            Pipeline.build("data", true, s -> s + "|ok");

    private static final Pipeline<String> ERROR_PIPELINE =
            Pipeline.build("err", true, s -> "ERR");

    /** fresh FSM for every test */
    private static StateMachine<State,String> fsm() {
        return new StateMachine<State,String>(State.IDLE)
                .add(State.IDLE,   "HELLO", State.ACTIVE, HELLO_PIPELINE)
                .add(State.ACTIVE, "DATA",  State.ACTIVE, DATA_PIPELINE)
                .add(State.ACTIVE, "FAIL",  State.ERROR,  ERROR_PIPELINE);
    }

    /* ----------------------------------------------------------- *
     *  Tests
     * ----------------------------------------------------------- */

    @Test
    @DisplayName("Initial state is preserved until the first event")
    void retainsInitialState() {
        assertThat(fsm().state()).isEqualTo(State.IDLE);
    }

    @Test
    @DisplayName("Transition returns the correct pipeline and mutates state")
    void transitionReturnsPipeline() {
        StateMachine<State,String> sm = fsm();
        Pipeline<String> p = sm.onEvent("HELLO");

        assertThat(p).isSameAs(HELLO_PIPELINE);
        assertThat(sm.state()).isEqualTo(State.ACTIVE);
    }

    @Test
    @DisplayName("Undefined transition throws IllegalStateException")
    void undefinedTransitionThrows() {
        StateMachine<State,String> sm = fsm();      // still IDLE
        assertThatThrownBy(() -> sm.onEvent("DATA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No transition");
    }
}
