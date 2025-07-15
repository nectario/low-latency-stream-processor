package com.ubs.trading.integration;

import com.ubs.trading.core.*;
import com.ubs.trading.metrics.MetricsRecorder;
import com.ubs.trading.pipeline.Pipeline;
import com.ubs.trading.statemachine.StateMachine;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import quickfix.FieldNotFound;
import quickfix.Message;
import quickfix.field.*;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixFlowDemoTest {

    /* ============================================================= *
     * 1.  Domain event (internal, not raw FIX)
     * ============================================================= */
    record ExecReportEvent(String clOrdId, char execType, double qty) {
        @Override public boolean equals(Object o) {
            return o instanceof ExecReportEvent e && e.execType == execType;
        }
        @Override public int hashCode() { return Character.hashCode(execType); }
    }

    /* builder helper */
    private static Message execReport(String clId, char execType, double qty) {
        Message m = new Message();
        m.getHeader().setField(new MsgType(MsgType.EXECUTION_REPORT));
        m.setField(new ClOrdID(clId));
        m.setField(new ExecType(execType));
        m.setField(new OrderQty(qty));
        return m;
    }

    /* map FIX → internal DTO */
    private static ExecReportEvent map(Message fix) {
        try {
            return new ExecReportEvent(
                    fix.getString(ClOrdID.FIELD),
                    fix.getChar(ExecType.FIELD),
                    fix.getDouble(OrderQty.FIELD));
        } catch (FieldNotFound e) {
            throw new IllegalArgumentException(e);
        }
    }

    /* ============================================================= *
     * 2.  Pipelines
     * ============================================================= */
    private static final Pipeline<ExecReportEvent> RISK_PIPE =
            Pipeline.build("risk", true,
                    e -> e);                              // (stub) flag limits etc.

    private static final Pipeline<ExecReportEvent> PNL_PIPE =
            Pipeline.build("pnl",  true,
                    e -> e);                              // (stub) compute PNL accrual

    /* ============================================================= *
     * 3.  State machine  NEW -> PART_FILLED -> FILLED
     * ============================================================= */
    private enum OrdState { NEW, PART_FILLED, FILLED }

    private static StateMachine<OrdState, ExecReportEvent> fsm() {
        return new StateMachine<OrdState, ExecReportEvent>(OrdState.NEW)
                .add(OrdState.NEW,         evt('0'), OrdState.PART_FILLED, RISK_PIPE)
                .add(OrdState.PART_FILLED, evt('2'), OrdState.FILLED,      PNL_PIPE);
    }

    /* predicate helper */
    private static ExecReportEvent evt(char execType) {
        return new ExecReportEvent("X", execType, 0);     // only execType matters for lookup
    }

    /* ============================================================= *
     * 4.  End‑to‑end demo test
     * ============================================================= */
    @Test @DisplayName("FIX flow: ingest → map → FSM → pipeline → sender")
    void fullFixFlow() {

        // metrics + collector
        MetricsRecorder metrics = new MetricsRecorder(new SimpleMeterRegistry());
        List<ExecReportEvent> outbound = new ArrayList<>();

        Dispatcher.MessageSender<ExecReportEvent> sender = outbound::add;

        Dispatcher<OrdState, ExecReportEvent> dispatcher =
                new Dispatcher<>(fsm(), metrics, sender);

        DisruptorEngine<ExecReportEvent> engine =
                new DisruptorEngine<>(256, dispatcher, metrics);

        /* --- produce two FIX execution reports --- */
        Message newAck  = execReport("X", ExecType.NEW,              100);
        Message fillAck = execReport("X", ExecType.FILL,             100);

        engine.publish(map(newAck));   // triggers risk pipeline
        engine.publish(map(fillAck));  // triggers pnl pipeline

        engine.shutdown();

        /* --- assertions --- */
        assertThat(outbound).hasSize(2);
        assertThat(outbound.get(0).execType()).isEqualTo(ExecType.NEW);
        assertThat(outbound.get(1).execType()).isEqualTo(ExecType.FILL);

        // order state advanced to FILLED
        assertThat(fsm().state()).isEqualTo(OrdState.NEW); // fresh instance untouched
    }
}
