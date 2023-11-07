package tiny.reactor;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DummyPublisherTest extends AbstractPublisherTest {
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
        sequence.add("onNext(" + n + ") ok");
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

    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
    Assertions.assertThat(sequence).containsExactly("onSubscribe() ok", "onNext(0) ok", "onNext(1) ok", "onNext(2) ok", "onComplete() ok");
  }

  @Test
  public void publisherRespectsSubscriberBackpressure() throws InterruptedException {
    DummyPublisher<Long> dummy = new DummyPublisher<>(generate(5L));
    final Subscription[] subscription = new Subscription[1];
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    List<Long> collected = new ArrayList<>();
    dummy.subscribe(new Subscriber<>() {
      @Override
      public void onSubscribe(Subscription s) {
        subscription[0] = s;
      }

      @Override
      public void onNext(Long aLong) {
        collected.add(aLong);
      }

      @Override
      public void onError(Throwable t) {
        error.set(t);
      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });


    // checks that backpressure from subscription is respected by publisher
    Assertions.assertThat(collected).isEmpty();

    subscription[0].request(1);
    Assertions.assertThat(collected).containsExactly(0L);

    subscription[0].request(1);
    Assertions.assertThat(collected).containsExactly(0L, 1L);

    subscription[0].request(2);
    Assertions.assertThat(collected).containsExactly(0L, 1L, 2L, 3L);

    subscription[0].request(Long.MAX_VALUE);
    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  public void shouldSetNPEOnNullDiscovery() throws InterruptedException {
    DummyPublisher<Long> dummy = new DummyPublisher<>(new Long[]{null});
    AtomicReference<Throwable> error = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    dummy.subscribe(new Subscriber<>() {
      @Override
      public void onSubscribe(Subscription s) {
        s.request(1);
      }

      @Override
      public void onNext(Long aLong) {

      }

      @Override
      public void onError(Throwable t) {
        error.set(t);
        latch.countDown();
      }

      @Override
      public void onComplete() {
      }
    });
    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
    Assertions.assertThat(error.get()).isInstanceOf(NullPointerException.class);
  }


  @Test
  public void shouldNotBustCallStackWithRecursiveCalls() throws InterruptedException {
    DummyPublisher<Long> dummy = new DummyPublisher<>(generate(1000L));
    CountDownLatch latch = new CountDownLatch(1);

    dummy.subscribe(new Subscriber<>() {
      Subscription subscription;

      @Override
      public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(1);
      }

      @Override
      public void onNext(Long aLong) {
        subscription.request(1);
      }

      @Override
      public void onError(Throwable t) {

      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });
    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  public void shouldRespectCancellationSignalWithNoMoreEmissionsAfterIt() throws InterruptedException {
    DummyPublisher<Long> dummy = new DummyPublisher<>(generate(1000L));
    CountDownLatch latch = new CountDownLatch(1);
    List<Long> collected = new ArrayList<>();
    AtomicReference<Throwable> error = new AtomicReference<>();

    dummy.subscribe(new Subscriber<>() {
      Subscription subscription;
      @Override
      public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.cancel();
        s.request(1);
      }

      @Override
      public void onNext(Long aLong) {
        collected.add(aLong);
        subscription.request(1);
      }

      @Override
      public void onError(Throwable t) {
        error.set(t);
      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });
    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isFalse();
    Assertions.assertThat(collected).isEmpty();
  }

  @Test
  public void shouldHandleNonPositiveRequestsByCancellingAndSettingThrowable() throws InterruptedException {
    DummyPublisher<Long> dummy = new DummyPublisher<>(generate(1000L));
    CountDownLatch latch = new CountDownLatch(1);
    List<Long> collected = new ArrayList<>();
    AtomicReference<Throwable> error = new AtomicReference<>();

    dummy.subscribe(new Subscriber<>() {
      Subscription subscription;
      @Override
      public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(-1);
      }

      @Override
      public void onNext(Long aLong) {
        collected.add(aLong);
        subscription.request(1);
      }

      @Override
      public void onError(Throwable t) {
        error.set(t);
        latch.countDown();
      }

      @Override
      public void onComplete() {
      }
    });

    Assertions.assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
    Assertions.assertThat(error.get()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldRespondToRequestsFromAPoolOfThreadsWithCorrectNumberOfEmissions() throws InterruptedException {
    long n = 1000;
    Long[] generated = generate(n);
    DummyPublisher<Long> dummy = new DummyPublisher<>(generated);
    CountDownLatch latch = new CountDownLatch(1);
    List<Long> collected = new ArrayList<>();
    AtomicReference<Throwable> error = new AtomicReference<>();

    dummy.subscribe(new Subscriber<>() {
      Subscription subscription;
      @Override
      public void onSubscribe(Subscription s) {
        this.subscription = s;
        for (int i = 0; i < n; i++)
          ForkJoinPool.commonPool().execute(()-> s.request(1));
      }

      @Override
      public void onNext(Long aLong) {
        collected.add(aLong);
      }

      @Override
      public void onError(Throwable t) {
        error.set(t);
      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });
    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
    Assertions.assertThat(collected).containsExactly(generated);
  }

  @Test
  public void shouldSendAllElementsOnLongMaxValue() throws InterruptedException {
    long n = 1000;
    Long[] generated = generate(n);
    DummyPublisher<Long> dummy = new DummyPublisher<>(generated);
    CountDownLatch latch = new CountDownLatch(1);
    List<Long> collected = new ArrayList<>();
    AtomicReference<Throwable> error = new AtomicReference<>();

    dummy.subscribe(new Subscriber<>() {
      Subscription subscription;
      @Override
      public void onSubscribe(Subscription s) {
        this.subscription = s;
        s.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(Long aLong) {
        collected.add(aLong);
      }

      @Override
      public void onError(Throwable t) {
        error.set(t);
      }

      @Override
      public void onComplete() {
        latch.countDown();
      }
    });
    Assertions.assertThat(latch.await(1000, TimeUnit.MILLISECONDS)).isTrue();
    Assertions.assertThat(collected).containsExactly(generated);
  }
}
