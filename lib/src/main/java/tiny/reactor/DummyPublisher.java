package tiny.reactor;

public class DummyPublisher<T> implements Publisher<T>{
  private final T[] arr;

  public DummyPublisher(T[] arr) {
    this.arr = arr;
  }

  @Override
  public void subscribe(Subscriber<? super T> s) {
    s.onSubscribe(new Subscription() {
      @Override
      public void request(long n) {
      }

      @Override
      public void cancel() {
      }
    });

    for(T e: arr)
      s.onNext(e);

    s.onComplete();
  }
}
