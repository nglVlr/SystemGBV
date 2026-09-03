package com.granados.sistema.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExclusiveJobsTest {

    @Test
    void mismaClaveRechazaAlSegundoSinEsperar() throws Exception {
        ExclusiveJobs jobs = new ExclusiveJobs();
        CountDownLatch ocupado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        AtomicBoolean termino = new AtomicBoolean(false);

        Thread primero = new Thread(() -> {
            jobs.run("compras-mes-2026-8", () -> {
                ocupado.countDown();
                try {
                    assertTrue(soltar.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                termino.set(true);
            });
        });
        primero.start();
        assertTrue(ocupado.await(5, TimeUnit.SECONDS));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> jobs.run("compras-mes-2026-8", () -> 1));
        assertTrue(ex.getMessage().contains("Otra persona"));

        soltar.countDown();
        primero.join(5000);
        assertTrue(termino.get());
        assertEquals(7, jobs.run("compras-mes-2026-8", () -> 7));
    }

    @Test
    void clavesDistintasNoSeBloquean() {
        ExclusiveJobs jobs = new ExclusiveJobs();
        jobs.run("presupuesto-egresos", () -> {
            assertEquals("ok", jobs.run("paquetes-2026-8", () -> "ok"));
            return null;
        });
    }
}
