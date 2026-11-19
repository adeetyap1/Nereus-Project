package my.nereus.intro.cinematic;

import java.util.function.LongConsumer;

/**
 * Satu langkah timeline. Implementasi WAJIB memanggil {@code done.accept(delayTicks)} tepat sekali
 * (boleh nanti, misalnya setelah teleport selesai) atau memanggil {@link StepContext#fail(String)}.
 */
public interface Step {

    void execute(StepContext ctx, LongConsumer done);

    String name();
}
