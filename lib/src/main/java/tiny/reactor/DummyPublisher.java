package tiny.reactor;

public class DummyPublisher<T> implements Publisher<T> {
  private final T[] arr;

  public DummyPublisher(T[] arr) {
    this.arr = arr;
  }

  @Override
  public void subscribe(Subscriber<? super T> s) {
    s.onSubscribe(new Subscription() {
      int index = 0;
      long received = 0l;
      boolean canceled = false;

      @Override
      public void request(long n) {
        if(n<=0 && !canceled) {
          cancel();
          s.onError(new IllegalArgumentException("request must not be non-positive"));
          return;
        }
        long init = received;

        received += n;

        if (init != 0) {
          // after the initial request, all other requests are immediately
          // returned after capturing the number of requested elements
          return;
        }

        int i = 0;
        for (; i < received && index < arr.length; i++, index++) {
          if(canceled)
            return;
          T t = arr[index];

          if (t == null) {
            s.onError(new NullPointerException("element must not be null"));
            return;
          }

          s.onNext(t);
        }

        if (index == arr.length)
          s.onComplete();

        received -= i;
      }

      @Override
      public void cancel() {
        canceled = true;
      }
    });
  }
}
