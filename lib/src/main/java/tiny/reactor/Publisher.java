package tiny.reactor;


public interface Publisher <T>{

  void subscribe(Subscriber<? super T> s);

}