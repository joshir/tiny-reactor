package tiny.reactor;

public class DummyPublisher<T> implements Publisher<T>{
  private final T[] arr;

  public DummyPublisher(T[] arr) {
    this.arr = arr;
  }

  @Override
  public void subscribe(Subscriber<? super T> s) {
    final int[] index = {0};
    s.onSubscribe(new Subscription() {
      @Override
      public void request(long n) {
        for(int i = 0; i < n && index[0] < arr.length; i++, index[0]++){
          T t = arr[index[0]];

          if (t == null) {
            s.onError(new NullPointerException("element must not be null"));
            return;
          }

          s.onNext(t);
        }

        if(index[0] == arr.length)
          s.onComplete();
      }

      @Override
      public void cancel() {
      }
    });
  }
}
