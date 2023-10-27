package tiny.reactor;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DummyPublisherTest extends AbstractPublisherTest{
  @Test
  public void methodCallsInExpectedOrder() throws InterruptedException {
    DummyPublisher<Long> dummy = new DummyPublisher<>(generate(3L));
    List<String> sequence = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(1);
    dummy.subscribe(new Subscriber<>() {
      @Override
      public void onSubscribe(Subscription s) {
        sequence.add("onSubscribe() ok");
        s.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(Long n) {
        sequence.add("onNext(" + n +") ok");
      }

      @Override
      public void onError(Throwable t) {
      }

      @Override
      public void onComplete() {
        sequence.add("onComplete() ok");
        latch.countDown();
      }
    });

    Assertions.assertThat(latch.await(1000, TimeUnit.MICROSECONDS)).isTrue();
    Assertions.assertThat(sequence).containsExactly("onSubscribe() ok", "onNext(0) ok", "onNext(1) ok", "onNext(2) ok", "onComplete() ok");
  }
}
