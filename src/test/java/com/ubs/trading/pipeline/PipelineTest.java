package com.ubs.trading.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the single‑generic {@link Pipeline}. */
class PipelineTest {

  /* --------------------------------------------------------------- *
   * 1. Var‑args build()
   * --------------------------------------------------------------- */
  @Test
  @DisplayName("build() assembles the entire chain and transforms correctly")
  void buildVarArgsConstructsPipeline() {
    Pipeline<String> p =
        Pipeline.build("varargs-demo", true, String::trim, String::toUpperCase, s -> s + "!");

    assertThat(p.execute("  a ")).isEqualTo("A!");
    assertThat(p.size()).isEqualTo(3);
    assertThat(p.name()).isEqualTo("varargs-demo");
  }

  /* --------------------------------------------------------------- *
   * 2. named() + addAction()
   * --------------------------------------------------------------- */
  @Test
  @DisplayName("named() + addAction() builds chain step‑by‑step")
  void builderStyleWorks() {
    Pipeline<String> p =
        Pipeline.<String>named("builder-demo") // 👈 type witness
            .addAction(String::strip)
            .addAction(s -> s.repeat(2));

    assertThat(p.execute(" b ")).isEqualTo("bb");
    assertThat(p.size()).isEqualTo(2);
  }

  /* --------------------------------------------------------------- *
   * 3. shortCircuit = true
   * --------------------------------------------------------------- */
  @Test
  @DisplayName("shortCircuit=true propagates the first thrown exception")
  void shortCircuitStopsOnError() {
    Function<String, String> faulty =
        s -> {
          throw new IllegalStateException("boom");
        };

    Pipeline<String> p = Pipeline.build("fatal", true, String::trim, faulty, s -> s + "never");

    assertThatThrownBy(() -> p.execute("x"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("boom");
  }

  /* --------------------------------------------------------------- *
   * 4. shortCircuit = false
   * --------------------------------------------------------------- */
  @Test
  @DisplayName("shortCircuit=false skips failing step and continues")
  void nonShortCircuitContinuesOnError() {
    AtomicBoolean finalStepExecuted = new AtomicBoolean(false);

    Function<String, String> faulty =
        s -> {
          throw new RuntimeException();
        };
    Function<String, String> finalStep =
        s -> {
          finalStepExecuted.set(true);
          return s + "✓";
        };

    Pipeline<String> p =
        Pipeline.build(
            "resilient",
            false,
            String::strip,
            faulty, // skipped
            finalStep); // must still run

    assertThat(p.execute("ok ")).isEqualTo("ok✓");
    assertThat(finalStepExecuted).isTrue();
  }

  /* --------------------------------------------------------------- *
   * 5. Multiple faulty steps with shortCircuit=false
   * --------------------------------------------------------------- */
  @Test
  @DisplayName("Pipeline continues past several faulty steps when shortCircuit=false")
  void multipleFaultyStepsAreSkipped() {
    AtomicInteger goodSteps = new AtomicInteger();

    Function<String, String> faulty =
        s -> {
          throw new RuntimeException();
        };
    Function<String, String> good =
        s -> {
          goodSteps.incrementAndGet();
          return s;
        };

    Pipeline<String> p = Pipeline.build("multi-fault", false, faulty, good, faulty, good);

    p.execute("go");
    assertThat(goodSteps).hasValue(2);
  }
}
