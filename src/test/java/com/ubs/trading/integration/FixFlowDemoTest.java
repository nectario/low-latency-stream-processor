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

    /* ============================================================
     * 1.  Domain event (internal, not raw FIX)
     * ------------------------------------------------------------
     *    ┌─────────────┐
     *    │ExecType only│  ← we override equals/hashCode so that two
     *    └─────────────┘     events are “equal” when the ExecType
     *                        (FIX tag150) matches.  Qty / ClOrdID
     *                        are ignored for routing.
     * ============================================================ */
    record ExecReportEvent(String clOrdId, char execType, double qty) {
        @Override public boolean equals(Object o) {
            return o instanceof ExecReportEvent e && e.execType == execType;
        }
        @Override public int hashCode() {
            return Character.hashCode(execType);
        }
    }

    /* FIX builder helper (no network engine, just a Message object) */
    private static Message execReport(String clId, char execType, double qty) {
        Message m = new Message();
        m.getHeader().setField(new MsgType(MsgType.EXECUTION_REPORT));
        m.setField(new ClOrdID(clId));
        m.setField(new ExecType(execType));
        m.setField(new OrderQty(qty));
        return m;
    }

    /* One‑line mapper: FIX → internal DTO                              //
       Could be expanded to full QuickFIX/J parsing in production.       */
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

    /* =============================================================
     * 2.  Pipelines                                               //
     *    Two independent pipelines selected by the FSM:
     *      • RISK_PIPE runs while order is still open
     *      • PNL_PIPE  runs when order is finally closed
     * ============================================================ */
    private static final Pipeline<ExecReportEvent> RISK_PIPE =
            Pipeline.build("risk", true, e -> e);  // stub: limit checks

    private static final Pipeline<ExecReportEvent> PNL_PIPE =
            Pipeline.build("pnl",  true, e -> e);  // stub: P&L accrual

    /* =============================================================
     * 3.  State machine definition                               //
     * -------------------------------------------------------------
     *   States:  NEW → PART_FILLED → FILLED
     *   Events:  ExecType '0' (NEW) and '2' (FILL)
     *
     *   Transition table (visual):
     *
     *                  ExecType '0'          ExecType '2'
     *   ┌──────────┐  ────────────────┐  ────────────────┐
     *   │  NEW     │ ▶ PART_FILLED     │ (no transition) │
     *   │          │   RISK_PIPE       │                 │
     *   ├──────────┤                   │                 │
     *   │PART_FILL │ (no transition)   │ ▶ FILLED        │
     *   │          │                   │   PNL_PIPE      │
     *   └──────────┴───────────────────┴─────────────────┘
     * ============================================================ */
    private enum OrdState { NEW, PART_FILLED, FILLED }

    private static StateMachine<OrdState, ExecReportEvent> fsm() {
        return new StateMachine<OrdState, ExecReportEvent>(OrdState.NEW)   // initial = NEW
                .add(OrdState.NEW,         evt('0'), OrdState.PART_FILLED, RISK_PIPE) //
                .add(OrdState.PART_FILLED, evt('2'), OrdState.FILLED,      PNL_PIPE); //
    }

    /* Helper: creates a “template” event used only as map key          //
       (qty irrelevant thanks to custom equals/hashCode).               */
    private static ExecReportEvent evt(char execType) {
        return new ExecReportEvent("X", execType, 0);
    }

    /* =============================================================
     * 4.  End‑to‑end demo test
     * ============================================================ */
    @Test @DisplayName("FIX flow: ingest → map → FSM → pipeline → sender")
    void fullFixFlow() {

        /* Metrics + outbound collector */
        MetricsRecorder metrics = new MetricsRecorder(new SimpleMeterRegistry());
        List<ExecReportEvent> outbound = new ArrayList<>();

        Dispatcher.MessageSender<ExecReportEvent> sender = outbound::add;

        Dispatcher<OrdState, ExecReportEvent> dispatcher =
                new Dispatcher<>(fsm(), metrics, sender);                 // <<<

        DisruptorEngine<ExecReportEvent> engine =
                new DisruptorEngine<>(256, dispatcher, metrics);

        /* Produce two FIX ExecutionReports */
        Message newAck  = execReport("X", ExecType.NEW,  100);            // 150=0
        Message fillAck = execReport("X", ExecType.FILL, 100);            // 150=2

        engine.publish(map(newAck));   // triggers RISK_PIPE, FSM NEW→PART_FILLED
        engine.publish(map(fillAck));  // triggers PNL_PIPE,  FSM PART→FILLED

        engine.shutdown();

        /* Assertions */
        assertThat(outbound).hasSize(2);
        assertThat(outbound.get(0).execType()).isEqualTo(ExecType.NEW);
        assertThat(outbound.get(1).execType()).isEqualTo(ExecType.FILL);

        // fresh FSM instance unchanged → still NEW (proves immutability of helper)
        assertThat(fsm().state()).isEqualTo(OrdState.NEW);
    }
}
