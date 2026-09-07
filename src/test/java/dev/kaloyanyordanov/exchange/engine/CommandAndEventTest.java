package dev.kaloyanyordanov.exchange.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kaloyanyordanov.exchange.book.BookSnapshot;
import dev.kaloyanyordanov.exchange.book.OrderId;
import dev.kaloyanyordanov.exchange.book.Side;
import dev.kaloyanyordanov.exchange.book.Trade;
import dev.kaloyanyordanov.exchange.engine.OrderRejected.RejectReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class CommandAndEventTest {

  @Test
  void submitOrderExposesFields() {
    SubmitOrder cmd = new SubmitOrder(OrderId.of(1L), Side.BUY, 100L, 5L, 7L);
    assertThat(cmd.id()).isEqualTo(OrderId.of(1L));
    assertThat(cmd.side()).isEqualTo(Side.BUY);
    assertThat(cmd.price()).isEqualTo(100L);
    assertThat(cmd.quantity()).isEqualTo(5L);
    assertThat(cmd.accountId()).isEqualTo(7L);
  }

  @Test
  void submitOrderValidates() {
    assertThatThrownBy(() -> new SubmitOrder(null, Side.BUY, 100L, 5L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SubmitOrder(OrderId.of(1L), null, 100L, 5L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SubmitOrder(OrderId.of(1L), Side.BUY, 0L, 5L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SubmitOrder(OrderId.of(1L), Side.BUY, 100L, 0L, 1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void submitResultHasThreeOutcomes() {
    assertThat(SubmitResult.values())
        .containsExactly(
            SubmitResult.ENQUEUED,
            SubmitResult.REJECTED_BUSY,
            SubmitResult.REJECTED_NOT_RUNNING);
    assertThat(SubmitResult.valueOf("ENQUEUED")).isEqualTo(SubmitResult.ENQUEUED);
  }

  @Test
  void orderAcceptedValidates() {
    OrderAccepted event = new OrderAccepted(OrderId.of(3L), 9L);
    assertThat(event.id()).isEqualTo(OrderId.of(3L));
    assertThat(event.sequence()).isEqualTo(9L);
    assertThatThrownBy(() -> new OrderAccepted(null, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OrderAccepted(OrderId.of(1L), -1L))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void tradeExecutedValidates() {
    Trade trade = new Trade(OrderId.of(1L), OrderId.of(2L), 100L, 5L, 10L, 20L, 0L);
    assertThat(new TradeExecuted(trade).trade()).isEqualTo(trade);
    assertThatThrownBy(() -> new TradeExecuted(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bookChangedValidates() {
    BookSnapshot snapshot = new BookSnapshot(List.of(), List.of());
    assertThat(new BookChanged(snapshot).snapshot()).isEqualTo(snapshot);
    assertThatThrownBy(() -> new BookChanged(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void orderRejectedValidates() {
    OrderRejected event = new OrderRejected(OrderId.of(5L), RejectReason.INSUFFICIENT_CASH, 4L);
    assertThat(event.id()).isEqualTo(OrderId.of(5L));
    assertThat(event.reason()).isEqualTo(RejectReason.INSUFFICIENT_CASH);
    assertThat(event.quantity()).isEqualTo(4L);
    assertThat(RejectReason.values())
        .containsExactly(RejectReason.INSUFFICIENT_CASH, RejectReason.INSUFFICIENT_ASSET);
    assertThatThrownBy(() -> new OrderRejected(null, RejectReason.INSUFFICIENT_CASH, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OrderRejected(OrderId.of(1L), null, 1L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OrderRejected(OrderId.of(1L), RejectReason.INSUFFICIENT_ASSET, 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
