package tiny.reactor;

public interface Subscription {

  void request(long n);

  void cancel();

}