package io.managed.services.test;

import io.managed.services.test.wait.ReadyFunction;
import io.managed.services.test.wait.TReadyFunction;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.codec.impl.BodyCodecImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFactory2;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.testng.ITestContext;
import org.testng.SkipException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.time.Duration.ofSeconds;
import static lombok.Lombok.sneakyThrow;

/**
 * Test utils contains static help methods
 */
public class TestUtils {
    private static final Logger LOGGER = LogManager.getLogger(TestUtils.class);

    private static final long MINUTES = 60 * 1000;
    private static final long DEFAULT_TIMEOUT = 3 * MINUTES;

    private static final MessageFactory2 MESSAGE_FACTORY = new ParameterizedMessageFactory();

    private static final List<Throwable> RETRIES = new LinkedList<>();

    public static final String FAKE_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUI" +
        "iwia2lkIiA6ICItNGVsY19WZE5fV3NPVVlmMkc0UXhyOEdjd0l4X0t0WFVDaXRhd" +
        "ExLbEx3In0.eyJleHAiOjE2MTM3MzI2NzAsImlhdCI6MTYxMzczMTc3MCwiYXV0a" +
        "F90aW1lIjoxNjEzNzMxNzY5LCJqdGkiOiIyZjAzYjI4Ni0yNWEzLTQyZjItOTdlY" +
        "S0zMjAwMjBjNWRkMzYiLCJpc3MiOiJodHRwczovL3Nzby5yZWRoYXQuY29tL2F1d" +
        "GgvcmVhbG1zL3JlZGhhdC1leHRlcm5hbCIsImF1ZCI6ImNsb3VkLXNlcnZpY2VzI" +
        "iwic3ViIjoiZjo1MjhkNzZmZi1mNzA4LTQzZWQtOGNkNS1mZTE2ZjRmZTBjZTY6b" +
        "WstdGVzdC11c2VyLWUyZS1wcmltYXJ5IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoiY" +
        "2xvdWQtc2VydmljZXMiLCJzZXNzaW9uX3N0YXRlIjoiNWIzNzMzODktM2FhOC00Y" +
        "jExLTg2MTctOGYwNDQwM2Y2OTE5IiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2luc" +
        "yI6WyJodHRwczovL3Byb2QuZm9vLnJlZGhhdC5jb206MTMzNyIsImh0dHBzOi8vY" +
        "XBpLmNsb3VkLnJlZGhhdC5jb20iLCJodHRwczovL3FhcHJvZGF1dGguY2xvdWQuc" +
        "mVkaGF0LmNvbSIsImh0dHBzOi8vY2xvdWQub3BlbnNoaWZ0LmNvbSIsImh0dHBzO" +
        "i8vcHJvZC5mb28ucmVkaGF0LmNvbSIsImh0dHBzOi8vY2xvdWQucmVkaGF0LmNvb" +
        "SJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsiYXV0aGVudGljYXRlZCJdfSwic" +
        "2NvcGUiOiIiLCJhY2NvdW50X251bWJlciI6IjcwMjQ0MDciLCJpc19pbnRlcm5hb" +
        "CI6ZmFsc2UsImFjY291bnRfaWQiOiI1Mzk4ODU3NCIsImlzX2FjdGl2ZSI6dHJ1Z" +
        "Swib3JnX2lkIjoiMTQwMTQxNjEiLCJsYXN0X25hbWUiOiJVc2VyIiwidHlwZSI6I" +
        "lVzZXIiLCJsb2NhbGUiOiJlbl9VUyIsImZpcnN0X25hbWUiOiJUZXN0IiwiZW1ha" +
        "WwiOiJtay10ZXN0LXVzZXIrZTJlLXByaW1hcnlAcmVkaGF0LmNvbSIsInVzZXJuY" +
        "W1lIjoibWstdGVzdC11c2VyLWUyZS1wcmltYXJ5IiwiaXNfb3JnX2FkbWluIjpmY" +
        "WxzZX0.y0OHnHA8wLKPhpoeBp_8V4r76R6Miqdj6fNevWHOBsrJ4_j9GJJ2QfJme" +
        "TSY5V3d0nT2Rt2SZ9trPrLEFd3Wr5z9YGIle--TXKKkYKyyFr4FO8Uaxvh-oN45C" +
        "3cGsNYfbRBILqBCFHTmh54q1XoHA6FiteqdgMzUrBAoFG3SeFLl41u9abNA7EEe8" +
        "0ldozXsiSaLDWSylF1g9u1BhGqGuOpX0RoZGuTL_3KINEE7XoCbvW0xKecCA8-u1" +
        "C06X_GUgR0tVvdgoGpB9uPDX3sbqMpl7fNgJvwyZa8acVoJuxs5K945OYGzGXuDG" +
        "Gzt-zxEov9g4udCDxNQTUoHuCIrMrr1ubt2iFbqso4UF6h-NIbxqARxhlhhyH8U9" +
        "c2Zm1J_fLA9WJ8g1DJF75D66hV05s_RyRX1G6dFEriuT00PbGZQrxgH38zgZ8s-a" +
        "S3qCAc2vYS-ZD4_Sl2xQgICC1HYpbgUbWNeAVEOWygZJUPMJLgpJ3aM2P8Dnjia5" +
        "0KL0owSTYBWvFDkROI-ymDXfcRvEMVKyOdhljQNPZew4Ux4apBi9t-ncB9XabDo1" +
        "1eddbbmcV05FWDb8X4opshptnWDzAw4ZPhbjoTBhNEI2JbFssOSYpskNnkB4kKQb" +
        "BjVxAPldBNFwRKLOfvJNdY1jNurMY1xVMl2dbEpFBkqJf1lByU";

    /**
     * Wait until the passed async lambda function return true
     *
     * @param vertx       Vertex
     * @param description A description used for logging and errors
     * @param interval    Interval between each call
     * @param timeout     Max time to wait before failing if the async lambda doesn't return true
     * @param isReady     The async lambda that will be call on each interval
     * @return A Future that will be completed once the lambda function returns true
     */
    public static <T> Future<T> waitFor(
        Vertx vertx,
        String description,
        Duration interval,
        Duration timeout,
        IsReady<T> isReady) {

        // generate the exception earlier to print a cleaner stacktrace in case of timeout
        Exception e = new TimeoutException(String.format("timeout after %s waiting for %s", timeout.toString(), description));

        Instant deadline = Instant.now().plus(timeout);
        return waitFor(vertx, description, interval, deadline, e, isReady);
    }

    static <T> Future<T> waitFor(
        Vertx vertx,
        String description,
        Duration interval,
        Instant deadline,
        Exception timeout,
        IsReady<T> isReady) {

        boolean last = Instant.now().isAfter(deadline);

        LOGGER.debug("waiting for {}; left={}", description, Duration.between(Instant.now(), deadline));
        return isReady.apply(last)
            .compose(r -> {
                if (r.getValue0()) {
                    return Future.succeededFuture(r.getValue1());
                }

                // if the last request after the timeout didn't succeed fail with the timeout error
                if (last) {
                    return Future.failedFuture(timeout);
                }

                return sleep(vertx, interval)
                    .compose(v -> waitFor(vertx, description, interval, deadline, timeout, isReady));
            });
    }

    public static <A> A waitFor(String description, Duration interval, Duration timeout, ReadyFunction<A> isReady)
        throws TimeoutException, InterruptedException {

        TReadyFunction<A, RuntimeException> ready = (l, a) -> isReady.apply(l, a);
        return waitFor(description, interval, timeout, ready);
    }

    public static <A, T extends Throwable> A waitFor(String description, Duration interval, Duration timeout, TReadyFunction<A, T> isReady)
        throws T, TimeoutException, InterruptedException {

        var atom = new AtomicReference<A>();
        ThrowableFunction<Boolean, Boolean, T> ready = l -> isReady.apply(l, atom);
        waitFor(description, interval, timeout, ready);
        return atom.get();
    }

    // TODO: Convert waitFor into and independent class
    // TODO: The default interval should be timeout / 30s
    // TODO: The timeout should always be a multiple 30s
    public static <T extends Throwable> void waitFor(
        String description,
        Duration interval,
        Duration timeout,
        ThrowableFunction<Boolean, Boolean, T> isReady)
        throws T, TimeoutException, InterruptedException {

        // generate the exception earlier to print a cleaner stacktrace in case of timeout
        var e = new TimeoutException(String.format("timeout after %s waiting for %s", timeout.toString(), description));

        LOGGER.info("wait for {} for {}", description, timeout);

        Instant deadline = Instant.now().plus(timeout);
        waitFor(description, interval, deadline, e, isReady);
    }

    private static <T extends Throwable> void waitFor(
        String description,
        Duration interval,
        Instant deadline,
        TimeoutException timeout,
        ThrowableFunction<Boolean, Boolean, T> isReady)
        throws T, TimeoutException, InterruptedException {

        boolean last = Instant.now().isAfter(deadline);

        LOGGER.debug("waiting for {}; left={}", description, Duration.between(Instant.now(), deadline));
        if (isReady.call(last)) {
            return;
        }

        if (last) {
            throw timeout;
        }

        Thread.sleep(interval.toMillis());

        waitFor(description, interval, deadline, timeout, isReady);
    }

    /**
     * Convert a Java CompletionStage or CompletableFuture to a Vertx Future
     *
     * @param completion CompletionStage | CompletableFuture
     * @param <T>        Type
     * @return Vertx Future
     */
    public static <T> Future<T> toVertxFuture(CompletionStage<T> completion) {
        Promise<T> promise = Promise.promise();
        completion.whenComplete((r, t) -> {
            if (t == null) {
                promise.complete(r);
            } else {
                promise.fail(t);
            }
        });
        return promise.future();
    }

    public static <T> Future<Void> forEach(Iterable<T> iterable, Function<T, Future<Void>> action) {
        return forEach(iterable.iterator(), action);
    }

    /**
     * Similar to Iterable.forEach but it will wait for the Future returned by the action to complete before processing
     * the next item and return on the first Error.
     *
     * @param iterator Iterator
     * @param action   Lambda
     * @param <T>      T
     * @return a completed future once the forEach complete
     */
    public static <T> Future<Void> forEach(Iterator<T> iterator, Function<T, Future<Void>> action) {
        if (!iterator.hasNext()) {
            return Future.succeededFuture();
        }

        return action.apply(iterator.next())
            .compose(r -> forEach(iterator, action));
    }


    /**
     * Return a Future that will be completed after the passed duration.
     *
     * <pre>{@code
     * import static io.managed.services.test.TestUtils.await;
     * import static io.managed.services.test.TestUtils.waitFor;
     * import static java.time.Duration.ofSeconds;
     *
     * await(sleep(vertx, ofSeconds(10)))
     * }</pre>
     *
     * @param x Vertx
     * @param d Duration
     * @return Future
     */
    public static Future<Void> sleep(Vertx x, Duration d) {
        Promise<Void> p = Promise.promise();
        x.setTimer(d.toMillis(), l -> p.complete());
        return p.future();
    }


    public static <T> Future<T> retry(Vertx x, Supplier<Future<T>> call) {

        // retry in case of any error
        return retry(x, call, __ -> true, Environment.RETRY_CALL_THRESHOLD);
    }

    public static <T> Future<T> retry(
        Vertx x,
        Supplier<Future<T>> call,
        Function<Throwable, Boolean> condition) {

        return retry(x, call, condition, Environment.RETRY_CALL_THRESHOLD);
    }

    /**
     * Retry the call supplier if the supplier returns a failed future and
     * the condition returns true.
     *
     * @param x         Vertx
     * @param call      The supplier to call the first time and retry in case of failure
     * @param condition The condition to retry the call supplier
     * @param attempts  The max number of attempts before returning the last failure
     * @param <T>       T
     * @return Future
     */
    public static <T> Future<T> retry(
        Vertx x,
        Supplier<Future<T>> call,
        Function<Throwable, Boolean> condition,
        int attempts) {

        Function<Throwable, Future<T>> retry = t -> {
            // add the error to the list of retries
            RETRIES.add(t);

            LOGGER.error("skip error: ", t);

            // retry the API call
            return sleep(x, ofSeconds(1))
                .compose(r -> retry(x, call, condition, attempts - 1));
        };

        return call.get().recover(t -> {
            if (attempts > 0 && condition.apply(t)) {
                // retry the call if there are available attempts and if the condition returns true
                return retry.apply(t);
            }
            return Future.failedFuture(t);
        });
    }

    public static <T, E extends Throwable> T retry(
        ThrowableSupplier<T, E> call, Function<Throwable, Boolean> condition, int attempts)
        throws E {

        try {
            return call.get();

        } catch (Throwable t) {

            if (attempts > 0 && condition.apply(t)) {
                // retry the call if there are available attempts and if the condition returns true
                LOGGER.error("skip error: ", t);

                // retry the API call
                try {
                    Thread.sleep(ofSeconds(1).toMillis());
                } catch (InterruptedException e) {
                    throw sneakyThrow(e);
                }

                return retry(call, condition, attempts - 1);
            }
            throw sneakyThrow(t);
        }
    }

    public static List<Throwable> getRetries() {
        return RETRIES;
    }

    /**
     * Format the message like log4j
     *
     * @param message String format
     * @param params  Objects
     * @return String
     */
    public static String message(String message, Object... params) {
        return MESSAGE_FACTORY.newMessage(message, params).getFormattedMessage();
    }

    public static Path getLogPath(String folderName, ITestContext context) {
        String testMethod = context.getName();
        Class<?> testClass = context.getClass();
        return getLogPath(folderName, testClass, testMethod);
    }

    public static Path getLogPath(String folderName, Class<?> testClass, String testMethod) {
        Path path = Environment.LOG_DIR.resolve(Paths.get(folderName, testClass.getName()));
        if (testMethod != null) {
            path = path.resolve(testMethod.replace("(", "").replace(")", ""));
        }
        return path;
    }

    public static void logWithSeparator(String pattern, String text) {
        LOGGER.info("=======================================================================");
        LOGGER.info(pattern, text);
        LOGGER.info("=======================================================================");
    }

    public static <T> T asJson(Class<T> c, String s) {
        LOGGER.debug(s);
        return BodyCodecImpl.jsonDecoder(c).apply(Buffer.buffer(s));
    }

    public static String decodeBase64(String encodedString) {
        return new String(Base64.getDecoder().decode(encodedString));
    }

    /**
     * Block the Thread and wait for for the Future result and in case of Future failure throw the Future error
     */
    public static <T> T bwait(Future<T> future) throws Throwable {
        if (Vertx.currentContext() != null) {
            throw new IllegalCallerException("bwait() can not be used in within a Vert.x callback");
        }

        // await for the future to complete
        var latch = new CountDownLatch(1);
        future.onComplete(__ -> latch.countDown());
        latch.await();

        // assert the future result
        if (future.failed()) {
            throw future.cause();
        }
        return future.result();
    }

    public static void assumeTeardown() {
        if (Environment.SKIP_TEARDOWN) {
            throw new SkipException("skip teardown");
        }
    }
}
