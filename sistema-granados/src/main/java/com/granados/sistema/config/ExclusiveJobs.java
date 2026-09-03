package com.granados.sistema.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Un candado por clave para trabajos pesados (procesar un mes, importar
 * SICOIN). Varias PCs pueden usar el sistema a la vez; dos personas no
 * pueden pisarse en el MISMO trabajo. Si el candado esta ocupado se
 * responde al segundo usuario de inmediato, sin encolar minutos.
 */
@Component
public class ExclusiveJobs {

    private final ConcurrentHashMap<String, ReentrantLock> candados =
            new ConcurrentHashMap<>();

    public AutoCloseable hold(String clave) {
        ReentrantLock lock = candados.computeIfAbsent(clave, k -> new ReentrantLock());
        boolean tomado;
        try {
            tomado = lock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Se interrumpio la espera. Intenta de nuevo.");
        }
        if (!tomado) {
            throw new IllegalStateException(
                    "Otra persona esta haciendo este mismo trabajo ahora. "
                    + "Espera a que termine e intenta de nuevo.");
        }
        return lock::unlock;
    }

    public <T> T run(String clave, Supplier<T> trabajo) {
        try (AutoCloseable ignored = hold(clave)) {
            return trabajo.get();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public void run(String clave, Runnable trabajo) {
        run(clave, () -> {
            trabajo.run();
            return null;
        });
    }
}
