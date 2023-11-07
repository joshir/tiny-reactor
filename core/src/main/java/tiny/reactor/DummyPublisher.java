package tiny.reactor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DummyPublisher<T> implements Publisher<T> {
  private final T[] arr;

  public DummyPublisher(T[] arr) {
    this.arr = arr;
  }

  @Override
  public void subscribe(Subscriber<? super T> s) {
    s.onSubscribe(new Subscription() {
      AtomicInteger index = new AtomicInteger();
      AtomicLong received = new AtomicLong();
      boolean canceled = false;
      long init;

      @Override
      public void request(long n) {
        if (n <= 0 && !canceled) {
          cancel();
          s.onError(new IllegalArgumentException("request must not be non-positive"));
          return;
        }

        do {
          init = received.get();
          if(init == Long.MAX_VALUE)
            return;
          n += init;
          // check if some other thread has made changes in the meantime
          // and try again until n can safely be added
          // CAS is better than synchronized by a factor of 10
        } while(!received.compareAndSet(init, n));


        if (init > 0) {
          // after the initial request, all other requests are immediately
          // returned after capturing the number of requested elements
          // call stack never grows beyond request()->next()->request()
          // as opposed to just letting the stack blow up to whatever size
          // determined by n that has an upperbound of Long.MAX_VALUE but typically
          // jvm stack busts in <1MB
          return;
        }

        while (true) {
          int i = 0;
          for (; i < received.get() && index.get() < arr.length; i++, index.incrementAndGet()) {
            if (canceled)
              return;

            T t = arr[index.get()];

            if (t == null) {
              s.onError(new NullPointerException("element must not be null"));
              return;
            }

            s.onNext(t);
          }

          if (index.get() == arr.length) {
            s.onComplete();
            return;
          }

          if (received.addAndGet(-i) == 0) {
            // one thread is "stealing requests" and dispatching
            // onNext signals if received is not zero
            // when received is zero, busy spinning is exited
            return;
          }
        }
      }

      @Override
      public void cancel() {
        canceled = true;
      }
    });
  }
}
