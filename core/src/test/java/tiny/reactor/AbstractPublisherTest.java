package tiny.reactor;

abstract class AbstractPublisherTest {
  static Long[] generate(Long n) {
    Long[] retVal = new Long[(n >= Integer.MAX_VALUE) ? 100_000 : Math.toIntExact(n)];

    for (int k = 0; k < n; k++)
      retVal[k] = (long) k;

    return retVal;
  }
}
